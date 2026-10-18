package com.zvonok.unittests.service;

import com.zvonok.config.CallLifecycleProperties;
import com.zvonok.controller.dto.*;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.*;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CallActionsLifecycleTest {
    RoomService rooms = mock(RoomService.class);
    UserService users = mock(UserService.class);
    CallSessionRepository calls = mock(CallSessionRepository.class);
    CallParticipantRepository participants = mock(CallParticipantRepository.class);
    CallEventPublisher events = mock(CallEventPublisher.class);
    LiveKitRoomAdminService media = mock(LiveKitRoomAdminService.class);
    CallLifecycleProperties policy = new CallLifecycleProperties();
    CallSessionService service = new CallSessionService(rooms, users, calls, participants, events, media, policy);
    CallRestoreStateService restore = new CallRestoreStateService(participants, users, policy);
    Room room = new Room(); User host = new User(); User user = new User();
    CallSession call = new CallSession(); CallParticipant p = new CallParticipant();
    @BeforeEach void setup() {
        host.setId(1L); host.setUsername("host"); user.setId(2L); user.setUsername("user");
        room.setId(11L); room.setType(RoomType.GROUP); room.setMembers(List.of(host, user)); room.setIsActive(true);
        call.setId(3L); call.setRoom(room); call.setRoomType(RoomType.GROUP); call.setStatus(CallSessionStatus.ACTIVE);
        call.setHostUser(host); call.setCreatedBy(host); call.setStartedAt(LocalDateTime.now()); call.setLivekitRoomName("call-3");
        p.setCallSession(call); p.setUser(user); p.setStatus(CallParticipantStatus.RINGING); p.setRole(CallParticipantRole.MEMBER);
        when(users.getUser("user")).thenReturn(user); when(users.getUser("host")).thenReturn(host);
        when(rooms.getRoom(11L, "user")).thenReturn(room);
        when(rooms.getRoomForUpdate(11L, "host")).thenReturn(room);
        when(calls.findByIdForUpdate(3L)).thenReturn(Optional.of(call));
        when(participants.findByCallSessionIdAndUserId(3L, 2L)).thenReturn(Optional.of(p));
        when(participants.findAllByCallSessionId(3L)).thenReturn(List.of(p));
        when(participants.save(any())).thenAnswer(i -> i.getArgument(0));
        when(calls.save(any())).thenAnswer(i -> { CallSession s = i.getArgument(0); if (s.getId() == null) s.setId(3L); return s; });
        when(participants.findRestoreCandidates(eq(2L), any(), any(), any())).thenReturn(List.of(p));
    }
    @Test void createGroupCallAcceptsHostAndInvitesMembers() {
        InviteCallDto dto = new InviteCallDto(); dto.setChatRoomId(11L);
        var created = service.startCall("host", dto);
        assertEquals(CallSessionStatus.ACTIVE, created.getStatus());
        assertEquals("group-11-call-3", created.getLivekitRoomName());
        verify(participants).save(argThat(x -> x.getUser() == host && x.getStatus() == CallParticipantStatus.ACCEPTED));
        verify(participants).save(argThat(x -> x.getUser() == user && x.getStatus() == CallParticipantStatus.RINGING));
    }
    @Test void repeatedJoinIsIdempotentAndKeepsSingleParticipant() {
        JoinCallDto dto = new JoinCallDto(); dto.setCallId(3L);
        service.join("user", dto); service.join("user", dto);
        assertEquals(CallParticipantStatus.ACCEPTED, p.getStatus()); assertNull(p.getLeftAt());
        verify(participants, times(1)).save(p);
        verify(events, times(2)).sendToUser(eq("user"), any()); // existing canonical + compatibility events, once each
    }
    @Test void groupMemberAddedAfterStartCanJoinExactlyOnce() {
        when(participants.findByCallSessionIdAndUserId(3L, 2L)).thenReturn(Optional.empty());
        JoinCallDto dto = new JoinCallDto(); dto.setCallId(3L);
        service.join("user", dto);
        verify(participants, times(2)).save(argThat(x -> x.getUser() == user));
        verify(calls, never()).save(any());
    }
    @Test void acceptedGroupMemberIgnoresDelayedDecline() {
        p.setStatus(CallParticipantStatus.ACCEPTED);
        DeclineCallDto dto = new DeclineCallDto(); dto.setCallId(3L);
        service.decline("user", dto);
        assertEquals(CallParticipantStatus.ACCEPTED, p.getStatus()); verifyNoInteractions(events);
    }
    @Test void groupReacceptClearsVoluntaryLeftAt() {
        p.setStatus(CallParticipantStatus.LEFT); p.setLeftAt(LocalDateTime.now());
        AcceptCallDto dto = new AcceptCallDto(); dto.setCallId(3L);
        service.accept("user", dto);
        assertEquals(CallParticipantStatus.ACCEPTED, p.getStatus()); assertNull(p.getLeftAt());
        assertNotNull(p.getAcceptedAt());
    }
    @Test void repeatedLeaveDoesNotRepublishOrEndGroupWithAnotherParticipant() {
        when(participants.countByCallSessionIdAndStatusIn(eq(3L), any())).thenReturn(1L);
        p.setStatus(CallParticipantStatus.ACCEPTED);
        LeaveCallDto dto = new LeaveCallDto(); dto.setCallId(3L);
        service.leave("user", dto); service.leave("user", dto);
        assertEquals(CallSessionStatus.ACTIVE, call.getStatus());
        assertEquals(CallParticipantStatus.LEFT, p.getStatus());
        verify(events, times(1)).sendToUser(eq("user"), any());
    }
    @Test void privateDeclineDeletesCallerMediaRoomAndPublishesTerminalEvent() {
        call.setRoomType(RoomType.PRIVATE); call.setStatus(CallSessionStatus.RINGING);
        DeclineCallDto dto = new DeclineCallDto(); dto.setCallId(3L);
        service.decline("user", dto); service.decline("user", dto);
        assertEquals(CallSessionStatus.ENDED, call.getStatus()); assertNotNull(p.getLeftAt());
        verify(media, times(1)).deleteRoom("call-3");
        verify(events, times(1)).sendToUser(eq("user"), argThat(e -> e.getType() == com.zvonok.service.dto.CallType.CALL_ENDED));
    }
    @Test void acceptingExpiredPrivateInvitationEndsInsteadOfConnecting() {
        call.setRoomType(RoomType.PRIVATE); call.setStatus(CallSessionStatus.RINGING);
        call.setStartedAt(LocalDateTime.now().minusSeconds(61));
        AcceptCallDto dto = new AcceptCallDto(); dto.setCallId(3L);
        service.accept("user", dto);
        assertEquals(CallSessionStatus.ENDED, call.getStatus());
        assertEquals(CallEndReason.CANCELLED, call.getEndReason());
    }
    @Test void restoreWithinMediaGraceDoesNotCreateSessionOrParticipant() {
        p.setStatus(CallParticipantStatus.ACCEPTED); call.setMediaEmptySince(LocalDateTime.now().minusSeconds(89));
        assertTrue(restore.findCandidate("user").isPresent());
        verify(calls, never()).save(any()); verify(participants, never()).save(any());
        verifyNoInteractions(events, media);
    }
    @Test void oldTokenTimestampDoesNotRejectAnOtherwiseLiveSession() {
        p.setStatus(CallParticipantStatus.JOINED); p.setLastSeenAt(LocalDateTime.now().minusHours(2));
        assertTrue(restore.findCandidate("user").isPresent());
    }
    @Test void endedCallCannotRestore() {
        call.setStatus(CallSessionStatus.ENDED); assertTrue(restore.findCandidate("user").isEmpty());
    }
    @Test void leftParticipantCannotRestoreEvenWithStaleAcceptedStatus() {
        p.setStatus(CallParticipantStatus.ACCEPTED); p.setLeftAt(LocalDateTime.now());
        assertTrue(restore.findCandidate("user").isEmpty());
    }
    @Test void removedMemberCannotRestoreIncomingInvitation() {
        room.setMembers(List.of(host)); assertTrue(restore.findCandidate("user").isEmpty());
    }
    @Test void expiredInvitationCannotRestore() {
        call.setStartedAt(LocalDateTime.now().minusSeconds(61)); assertTrue(restore.findCandidate("user").isEmpty());
    }
}
