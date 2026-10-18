package com.zvonok.service;

import com.zvonok.controller.LiveKitController;
import com.zvonok.exception.*;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.*;
import com.zvonok.repository.*;
import com.zvonok.service.dto.LiveKitTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LiveKitSecurityTest {
    UserService users = mock(UserService.class);
    CallSessionRepository calls = mock(CallSessionRepository.class);
    CallParticipantRepository participants = mock(CallParticipantRepository.class);
    LiveKitTokenService signer = mock(LiveKitTokenService.class);
    LiveKitRoomAdminService admin = mock(LiveKitRoomAdminService.class);
    CallSessionService sessions = new CallSessionService(mock(RoomService.class), users, calls,
            participants, mock(CallEventPublisher.class), admin, new com.zvonok.config.CallLifecycleProperties());
    LiveKitCallTokenService tokens = new LiveKitCallTokenService(sessions, signer, admin);
    User user = new User();
    Room room = new Room();
    CallSession call = new CallSession();
    CallParticipant participant = new CallParticipant();

    @BeforeEach void setup() {
        user.setId(1L); user.setUsername("alice"); user.setDisplayName("Alice");
        room.setId(2L); room.setIsActive(true); room.setMembers(new ArrayList<>(List.of(user)));
        call.setId(3L); call.setRoom(room); call.setRoomType(RoomType.PRIVATE);
        call.setStatus(CallSessionStatus.ACTIVE); call.setLivekitRoomName("call-3");
        participant.setUser(user); participant.setCallSession(call); participant.setStatus(CallParticipantStatus.JOINED);
        when(users.getUser("alice")).thenReturn(user);
        when(calls.findById(3L)).thenReturn(Optional.of(call));
        lenient().when(calls.findByIdForUpdate(3L)).thenReturn(Optional.of(call));
        when(participants.findByCallSessionIdAndUserId(3L, 1L)).thenReturn(Optional.of(participant));
        lenient().when(participants.touchLastSeenIfTokenAllowed(eq(3L), eq("alice"), any(), any(), any())).thenReturn(1);
        lenient().when(signer.generateCallToken("call-3", "alice", "Alice", 3L)).thenReturn(new LiveKitTokenResponse());
    }

    @Test void participantCanReconnect() { assertNotNull(tokens.issueCallToken(3L, "alice")); }
    @Test void confirmedEmptyMediaPastGraceCannotMintToken() {
        call.setMediaEmptySince(LocalDateTime.now().minusSeconds(91));
        assertThrows(CallStateConflictException.class, () -> tokens.issueCallToken(3L, "alice"));
        verifyNoInteractions(admin, signer);
    }
    @Test void emptyRoomRemovedBySfuCanBeRecreatedDuringGraceWithoutNewSession() {
        call.setLivekitRoomReadyAt(LocalDateTime.now().minusMinutes(5));
        call.setMediaEmptySince(LocalDateTime.now().minusSeconds(89));
        when(admin.requireExistingRoom("call-3")).thenThrow(new com.zvonok.exception.LiveKitRoomNotFoundException("room missing"));
        assertNotNull(tokens.issueCallToken(3L, "alice"));
        verify(admin).ensureRoomReady("call-3");
        verify(signer).generateCallToken("call-3", "alice", "Alice", 3L);
        verify(calls, never()).save(argThat(s -> s.getId() == null));
    }
    @Test void restoreUsesSameAuthorizedIssuancePath() {
        call.setHostUser(user);
        when(participants.findRestoreCandidates(eq(1L), any(), any(), any())).thenReturn(List.of(participant));
        var response = new CallRestoreService(tokens, new CallRestoreStateService(participants, users, new com.zvonok.config.CallLifecycleProperties())).restoreCallSession("alice");
        assertTrue(response.isPresent());
        verify(signer).generateCallToken("call-3", "alice", "Alice", 3L);
    }
    @Test void restoreCannotMintAfterRoomMembershipRevocation() {
        room.setMembers(List.of());
        when(participants.findRestoreCandidates(eq(1L), any(), any(), any())).thenReturn(List.of(participant));
        assertTrue(new CallRestoreService(tokens, new CallRestoreStateService(participants, users, new com.zvonok.config.CallLifecycleProperties()))
                .restoreCallSession("alice").isEmpty());
        verifyNoInteractions(signer, admin);
    }
    @Test void groupParticipantCanJoin() { call.setRoomType(RoomType.GROUP); assertNotNull(tokens.issueCallToken(3L, "alice")); }
    @Test void privateCallerCanConnectWhileRinging() { call.setStatus(CallSessionStatus.RINGING); assertNotNull(tokens.issueCallToken(3L, "alice")); }
    @Test void strangerDenied() {
        when(participants.findByCallSessionIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @Test void removedMemberDenied() {
        room.setMembers(new ArrayList<>());
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @Test void inactiveRoomDenied() {
        room.setIsActive(false);
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @Test void leftParticipantDeniedEvenWithStaleStatus() {
        participant.setLeftAt(LocalDateTime.now());
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @ParameterizedTest @EnumSource(value=CallParticipantStatus.class, names={"ACCEPTED", "JOINED"}, mode=EnumSource.Mode.EXCLUDE)
    void otherParticipantStatesDenied(CallParticipantStatus status) {
        participant.setStatus(status);
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @ParameterizedTest @EnumSource(value=CallSessionStatus.class, names={"ACTIVE", "RINGING"}, mode=EnumSource.Mode.EXCLUDE)
    void terminalCallDenied(CallSessionStatus status) {
        call.setStatus(status);
        assertThrows(InsufficientPermissionsException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer, admin);
    }
    @Test void stateRevocationDuringRoomRequestPreventsSigning() {
        when(participants.touchLastSeenIfTokenAllowed(eq(3L), eq("alice"), any(), any(), any())).thenReturn(0);
        assertThrows(CallStateConflictException.class, () -> tokens.issueCallToken(3L, "alice")); verifyNoInteractions(signer);
    }
    @Test void arbitraryRoomCannotUseLegacyEndpoint() {
        assertThrows(InsufficientPermissionsException.class, () -> new LiveKitController().getToken("victim-room", () -> "alice"));
    }
    @Test void conferenceRequiresExistingActiveInvitation() {
        ConferenceRepository repository = mock(ConferenceRepository.class);
        ConferenceService conferences = new ConferenceService(repository, users, admin, signer);
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());
        assertThrows(ConferenceNotFoundException.class, () -> conferences.joinConference("unknown", "alice"));
        Conference conference = Conference.builder().code("invite").livekitRoomName("conf-invite")
                .host(user).status(ConferenceStatus.ENDED).build();
        when(repository.findByCode("invite")).thenReturn(Optional.of(conference));
        assertThrows(ConferenceEndException.class, () -> conferences.joinConference("invite", "alice"));
        verifyNoInteractions(signer);
        conference.setStatus(ConferenceStatus.ACTIVE);
        when(signer.generateCallToken("conf-invite", "alice", "Alice", null)).thenReturn(new LiveKitTokenResponse());
        assertNotNull(conferences.joinConference("invite", "alice"));
    }
    @Test void signedGrantIsBoundToRoomWithoutAdministrativePermissions() throws Exception {
        LiveKitTokenService real = new LiveKitTokenService();
        ReflectionTestUtils.setField(real, "apiKey", "test-key");
        ReflectionTestUtils.setField(real, "apiSecret", "test-secret-with-at-least-thirty-two-characters");
        ReflectionTestUtils.setField(real, "tokenTtlMinutes", 10);
        String jwt = real.generateCallToken("call-3", "alice", "Alice", 3L).getParticipantToken();
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        assertEquals("alice", payload.get("sub").asText());
        var grant = payload.get("video");
        assertEquals("call-3", grant.get("room").asText()); assertTrue(grant.get("roomJoin").asBoolean());
        assertFalse(grant.path("roomAdmin").asBoolean()); assertFalse(grant.path("roomCreate").asBoolean());
        long remaining = payload.get("exp").asLong() - java.time.Instant.now().getEpochSecond();
        assertTrue(remaining > 590 && remaining <= 600);
    }
}
