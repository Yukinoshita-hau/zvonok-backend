package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.zvonok.config.CallLifecycleProperties;
import com.zvonok.model.enumeration.CallSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallLifecycleReconciler {
    private final CallSessionCleanupService cleanup;
    private final LiveKitRoomAdminService liveKit;
    private final CallLifecycleProperties policy;
    private long cursor;

    @Scheduled(fixedDelayString = "${call.lifecycle.reconcile-delay-ms:30000}",
            initialDelayString = "${call.lifecycle.reconcile-delay-ms:30000}")
    public void reconcile() {
        var batch = cleanup.snapshots(cursor);
        if (batch.isEmpty()) { cursor = 0; return; }
        for (var snapshot : batch) {
            cursor = snapshot.id();
            try {
                reconcile(snapshot, LocalDateTime::now);
            } catch (RuntimeException e) {
                // An unavailable REST service is never evidence of an empty room.
                log.warn("Call reconciliation deferred: callId={}", snapshot.id(), e);
            }
        }
        if (batch.size() < 100) cursor = 0;
    }

    public void reconcile(CallSessionCleanupService.Snapshot snapshot, LocalDateTime now) {
        reconcile(snapshot, () -> now);
    }

    private void reconcile(CallSessionCleanupService.Snapshot snapshot, Supplier<LocalDateTime> clock) {
        LocalDateTime now = clock.get();
        cleanup.expireInvitations(snapshot, now);
        if (snapshot.status() != CallSessionStatus.ACTIVE) return;
        if (snapshot.startedAt().plusSeconds(policy.getInitialConnectSeconds()).isAfter(now)) return;
        boolean empty = liveKit.listParticipants(snapshot.roomName()).isEmpty();
        now = clock.get();
        boolean finalCheck = empty && snapshot.emptySince() != null
                && !snapshot.emptySince().plusSeconds(policy.getMediaGraceSeconds()).isAfter(now);
        if (finalCheck) {
            // Second REST observation immediately before the short locked final gate.
            empty = liveKit.listParticipants(snapshot.roomName()).isEmpty();
            now = clock.get();
        }
        cleanup.observeMedia(snapshot, empty, finalCheck && empty, now);
    }
}
