package com.zvonok.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zvonok.exception.InsufficientPermissionsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "browser.activity.enabled", havingValue = "true")
public class BrowserActivityService {
    private final BrowserActivityAccess access;
    private final BrowserIntegrationClient browser;
    // shortcut: local single-JVM registry; a Core restart closes the BrowserSession through its six-second API lease.
    private final java.util.concurrent.ConcurrentHashMap<Long, Activity> activities = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> COMMANDS = Set.of("state", "join", "leave", "close", "media", "heartbeat",
            "navigate", "input", "back", "forward", "reload", "quality", "tabs/create", "tabs/activate", "tabs/close", "control/request", "control/grant",
            "control/decline", "control/revoke", "control/release");
    private static class Activity {
        UUID id = UUID.randomUUID();
        Long callId;
        String ownerId;
        String ownerName;
        volatile String guestId;
        volatile JsonNode session;
        final java.util.concurrent.atomic.AtomicBoolean reconciling = new java.util.concurrent.atomic.AtomicBoolean();
    }
    public record View(UUID activityId, Long callId, boolean owner, boolean joined, JsonNode session) {}

    public View find(Long callId, String username) {
        var members = access.require(callId, username);
        String subject = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        Activity a;
        boolean owner;
        boolean joined;
        synchronized (this) {
            a = activities.get(callId);
            if (a == null || a.session == null) return null;
            owner = a.ownerId.equals(subject);
            joined = owner || subject.equals(a.guestId);
        }
        // Never return another member's collaboration identity/lease to an invited viewer.
        JsonNode session = null;
        if (joined) {
            // A slow state response must not hold the monitor needed by lease renewal.
            try { session = browser.command(a.id, subject, "state", null, null, null); }
            catch (ResponseStatusException e) {
                if (owner || e.getStatusCode().value() != 403) throw e;
                synchronized (this) {
                    if (activities.get(a.callId) == a && subject.equals(a.guestId)) a.guestId = null;
                }
                joined = false;
            }
        }
        synchronized (this) {
            if (activities.get(a.callId) != a || (!owner && joined && !subject.equals(a.guestId)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Activity changed");
            return new View(a.id, callId, owner, joined, session);
        }
    }

    public View create(Long callId, String username) {
        var members = access.require(callId, username);
        if (members.size() != 2) throw new InsufficientPermissionsException("Both friends must accept the call");

        var a = new Activity();
        a.callId = callId;
        a.ownerName = username;
        a.ownerId = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        if (activities.putIfAbsent(callId, a) != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One Browser Activity per call");
        try { a.session = browser.command(a.id, a.ownerId, "create", null, null, null); }
        catch (RuntimeException e) { activities.remove(callId, a); throw e; }
        return new View(a.id, callId, true, true, a.session);
    }

    public JsonNode command(Long callId, UUID activityId, String username, String op, JsonNode body, String epoch, String generation) {
        var members = access.require(callId, username);
        String subject = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        Activity a;
        synchronized (this) {
            a = activities.get(callId);
            if (a == null || !a.id.equals(activityId) || !a.callId.equals(callId))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Activity ended");
            if (!COMMANDS.contains(op)) throw new InsufficientPermissionsException("Unsupported browser action");
            boolean owner = a.ownerId.equals(subject);
            if (!owner && (op.equals("close") || op.equals("control/grant") || op.equals("control/decline") || op.equals("control/revoke")))
                throw new InsufficientPermissionsException("Owner required");
            if (!owner && !op.equals("join") && !subject.equals(a.guestId)) throw new InsufficientPermissionsException("Join required");
        }
        // Do not serialize input with revoke: Slice 3 fences its queue before acknowledging revoke.
        JsonNode response = browser.command(a.id, subject, op, body, epoch, generation);
        synchronized (this) {
            if (activities.get(a.callId) == a) {
                if (op.equals("join") && !a.ownerId.equals(subject)) a.guestId = subject;
                if (op.equals("leave")) a.guestId = null;
                if (op.equals("close")) activities.remove(a.callId, a);
            }
        }
        return response;
    }

    @Scheduled(fixedDelay = 1000)
    public void reconcile() {
        activities.values().parallelStream().forEach(this::reconcile);
    }

    private void reconcile(Activity a) {
        if (a.session == null || !a.reconciling.compareAndSet(false, true)) return;
        try { reconcileActive(a); } finally { a.reconciling.set(false); }
    }

    private void reconcileActive(Activity a) {
        String phase = "access";
        try {
            var members = access.require(a.callId, a.ownerName);
            if (a.guestId != null && members.stream().noneMatch(p -> p.getUser().getId().toString().equals(a.guestId))) {
                phase = "revoke-member";
                browser.command(a.id, a.ownerId, "revoke-member", null, null, null);
                a.guestId = null;
            }
            phase = "keepalive";
            browser.command(a.id, a.ownerId, "keepalive", null, null, null);
        } catch (Exception e) {
            int status = e instanceof ResponseStatusException response ? response.getStatusCode().value() : 0;
            log.warn("browser-reconcile at={} activityId={} callId={} phase={} status={} exception={}",
                    System.currentTimeMillis(), a.id, a.callId, phase, status, e.getClass().getSimpleName());
            // Do not turn a transient renewal failure into DELETE; the unchanged API/Worker leases still expire.
            if (phase.equals("keepalive") && (status == 429 || status >= 500)) return;
            // On authorization/DB/transport loss, stop renewing; retry DELETE until cleanup is acknowledged.
            try { browser.command(a.id, a.ownerId, "close", null, null, null); activities.remove(a.callId, a); }
            catch (Exception unavailable) { /* API lease and existing Worker owner/TTL watchdogs fail closed. */ }
        }
    }
}

