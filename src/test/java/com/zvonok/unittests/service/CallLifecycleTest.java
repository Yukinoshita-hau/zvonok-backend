package com.zvonok.unittests.service;

import com.zvonok.config.CallLifecycleProperties;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.*;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import com.zvonok.service.CallSessionCleanupService.Snapshot;
import java.time.LocalDateTime;
import java.util.*;
import livekit.LivekitModels;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CallLifecycleTest {
    CallSessionRepository calls = mock(CallSessionRepository.class);
    CallParticipantRepository participants = mock(CallParticipantRepository.class);
    CallEventPublisher events = mock(CallEventPublisher.class);
    LiveKitRoomAdminService media = mock(LiveKitRoomAdminService.class);
    CallLifecycleProperties policy = new CallLifecycleProperties();
    CallSessionCleanupService cleanup = new CallSessionCleanupService(calls, participants, events, media, policy);
    CallLifecycleReconciler reconciler = new CallLifecycleReconciler(cleanup, media, policy);
    LocalDateTime now = LocalDateTime.of(2026, 10, 17, 12, 0);
    CallSession call = new CallSession();
    CallParticipant participant = new CallParticipant();
    User user = new User();
    Room room = new Room();

    @BeforeEach void setup() {
        user.setId(1L); user.setUsername("alice");
        room.setId(2L); room.setIsActive(true); room.setMembers(List.of(user)); room.setType(RoomType.GROUP);
        call.setId(3L); call.setVersion(1L); call.setRoom(room); call.setRoomType(RoomType.GROUP);
        call.setHostUser(user); call.setCreatedBy(user); call.setLivekitRoomName("call-3");
        call.setStatus(CallSessionStatus.ACTIVE); call.setStartedAt(now.minusMinutes(5));
        participant.setUser(user); participant.setCallSession(call); participant.setStatus(CallParticipantStatus.ACCEPTED);
        when(calls.findByIdForUpdate(3L)).thenReturn(Optional.of(call));
        when(participants.findAllByCallSessionId(3L)).thenReturn(List.of(participant));
    }

    Snapshot snapshot() { return new Snapshot(3L, "call-3", call.getVersion(), call.getStartedAt(), call.getStatus(), call.getMediaEmptySince()); }
    List<LivekitModels.ParticipantInfo> live() { return List.of(LivekitModels.ParticipantInfo.newBuilder().setIdentity("alice").build()); }

    @Test void firstEmptyObservationStartsGraceAndShortOutageSurvives() {
        when(media.listParticipants("call-3")).thenReturn(List.of());
        reconciler.reconcile(snapshot(), now);
        assertEquals(now, call.getMediaEmptySince());
        reconciler.reconcile(snapshot(), now.plusSeconds(20));
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verifyNoInteractions(events);
    }
    @Test void participantReturningAt89SecondsCancelsCleanup() {
        call.setMediaEmptySince(now);
        when(media.listParticipants("call-3")).thenReturn(live());
        reconciler.reconcile(snapshot(), now.plusSeconds(89));
        assertNull(call.getMediaEmptySince());
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verifyNoInteractions(events);
    }
    @Test void participantReturningImmediatelyBeforeFinalCheckCancelsCleanup() {
        call.setMediaEmptySince(now);
        when(media.listParticipants("call-3")).thenReturn(List.of(), live());
        reconciler.reconcile(snapshot(), now.plusSeconds(90));
        assertNull(call.getMediaEmptySince());
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verify(media, times(2)).listParticipants("call-3");
        verifyNoInteractions(events);
    }
    @Test void secondRestFailureCannotEndCall() {
        call.setMediaEmptySince(now);
        when(media.listParticipants("call-3")).thenReturn(List.of()).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(snapshot(), now.plusSeconds(90)));
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verifyNoInteractions(events);
    }
    @Test void restFailureDoesNotStartGrace() {
        when(media.listParticipants("call-3")).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> reconciler.reconcile(snapshot(), now));
        assertNull(call.getMediaEmptySince());
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
    }
    @Test void initialConnectGetsItsOwnWindowBeforeMediaGrace() {
        call.setStartedAt(now.minusSeconds(119));
        reconciler.reconcile(snapshot(), now);
        verifyNoInteractions(media, events);
        assertNull(call.getMediaEmptySince());
    }
    @Test void finalEndIsIdempotentAndPublishesAfterCommitOnly() {
        call.setMediaEmptySince(now);
        when(media.listParticipants("call-3")).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            Snapshot snapshot = snapshot();
            reconciler.reconcile(snapshot, now.plusSeconds(90));
            reconciler.reconcile(snapshot, now.plusSeconds(91));
            assertEquals(CallSessionStatus.ENDED, call.getStatus());
            assertEquals(CallParticipantStatus.LEFT, participant.getStatus());
            assertNotNull(participant.getLeftAt());
            verifyNoInteractions(events);
            verify(media, never()).deleteRoom(anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(events, times(1)).sendToUser(eq("alice"), argThat(e -> e.getType() == com.zvonok.service.dto.CallType.CALL_ENDED));
            verify(media, times(1)).deleteRoom("call-3");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    @Test void concurrentRecoveryInvalidatesObservedSnapshot() {
        call.setMediaEmptySince(now);
        Snapshot old = snapshot();
        call.setVersion(2L);
        cleanup.observeMedia(old, true, true, now.plusSeconds(90));
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verifyNoInteractions(events);
    }
    @Test void concurrentEndCannotBeResurrected() {
        Snapshot old = snapshot(); call.setStatus(CallSessionStatus.ENDED);
        cleanup.observeMedia(old, false, false, now);
        assertEquals(CallSessionStatus.ENDED, call.getStatus());
        verifyNoInteractions(events);
    }
    @Test void privateRingingExpiresAt60AndCleanupRepeatsSafely() {
        call.setRoomType(RoomType.PRIVATE); call.setStatus(CallSessionStatus.RINGING); call.setStartedAt(now.minusSeconds(59));
        participant.setStatus(CallParticipantStatus.RINGING);
        cleanup.expireInvitations(snapshot(), now);
        assertEquals(CallSessionStatus.RINGING, call.getStatus());
        cleanup.expireInvitations(snapshot(), now.plusSeconds(1));
        cleanup.expireInvitations(snapshot(), now.plusSeconds(2));
        assertEquals(CallSessionStatus.ENDED, call.getStatus());
        verify(events, times(1)).sendToUser(eq("alice"), any());
    }
    @Test void groupInvitationExpiresWithoutEndingGroupMedia() {
        participant.setStatus(CallParticipantStatus.RINGING);
        cleanup.expireInvitations(snapshot(), now);
        assertEquals(CallParticipantStatus.DECLINED, participant.getStatus());
        assertNotNull(participant.getLeftAt());
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        verify(media, never()).deleteRoom(anyString());
    }
}
