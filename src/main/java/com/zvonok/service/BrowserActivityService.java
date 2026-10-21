package com.zvonok.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zvonok.exception.InsufficientPermissionsException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "browser.activity.enabled", havingValue = "true")
public class BrowserActivityService {
    private final BrowserActivityAccess access;
    private final BrowserIntegrationClient browser;
    // shortcut: local single-JVM registry; a Core restart closes the BrowserSession through its six-second API lease.
    private Activity current;
    private static final Set<String> COMMANDS = Set.of("state", "join", "leave", "close", "media", "heartbeat",
            "navigate", "input", "back", "forward", "reload", "control/request", "control/grant",
            "control/decline", "control/revoke", "control/release");
    private static class Activity {
        UUID id = UUID.randomUUID();
        Long callId;
        String ownerId;
        String ownerName;
        String guestId;
        JsonNode session;
    }
    public record View(UUID activityId, Long callId, boolean owner, boolean joined, JsonNode session) {}

    public synchronized View find(Long callId, String username) {
        var members = access.require(callId, username);
        if (current == null || !current.callId.equals(callId)) return null;
        String subject = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        boolean owner = current.ownerId.equals(subject);
        boolean joined = owner || subject.equals(current.guestId);
        // Never return another member's collaboration identity/lease to an invited viewer.
        JsonNode session = null;
        if (joined) {
            try { session = browser.command(current.id, subject, "state", null, null, null); }
            catch (ResponseStatusException e) {
                if (owner || e.getStatusCode().value() != 403) throw e;
                current.guestId = null;
                joined = false;
            }
        }
        return new View(current.id, callId, owner, joined, session);
    }

    public synchronized View create(Long callId, String username) {
        var members = access.require(callId, username);
        if (members.size() != 2) throw new InsufficientPermissionsException("Both friends must accept the call");
        if (current != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "One local Activity at a time");
        var a = new Activity();
        a.callId = callId;
        a.ownerName = username;
        a.ownerId = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        a.session = browser.command(a.id, a.ownerId, "create", null, null, null);
        current = a;
        return new View(a.id, callId, true, true, a.session);
    }

    public JsonNode command(Long callId, UUID activityId, String username, String op, JsonNode body, String epoch, String generation) {
        var members = access.require(callId, username);
        String subject = members.stream().filter(p -> p.getUser().getUsername().equals(username))
                .findFirst().orElseThrow().getUser().getId().toString();
        Activity a;
        synchronized (this) {
            a = current;
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
            if (current == a) {
                if (op.equals("join") && !a.ownerId.equals(subject)) a.guestId = subject;
                if (op.equals("leave")) a.guestId = null;
                if (op.equals("close")) current = null;
            }
        }
        return response;
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void reconcile() {
        var a = current;
        if (a == null) return;
        try {
            var members = access.require(a.callId, a.ownerName);
            if (a.guestId != null && members.stream().noneMatch(p -> p.getUser().getId().toString().equals(a.guestId))) {
                browser.command(a.id, a.ownerId, "revoke-member", null, null, null);
                a.guestId = null;
            }
            browser.command(a.id, a.ownerId, "keepalive", null, null, null);
        } catch (Exception e) {
            // On authorization/DB/transport loss, stop renewing; retry DELETE until cleanup is acknowledged.
            try { browser.command(a.id, a.ownerId, "close", null, null, null); current = null; }
            catch (Exception unavailable) { /* API lease and existing Worker owner/TTL watchdogs fail closed. */ }
        }
    }
}
