package com.zvonok.unittests.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import com.zvonok.service.BrowserActivityAccess;
import com.zvonok.service.BrowserActivityService;
import com.zvonok.service.BrowserIntegrationClient;
import com.zvonok.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrowserActivityTest {
    final CallSessionRepository calls = mock(CallSessionRepository.class);
    final CallParticipantRepository participants = mock(CallParticipantRepository.class);
    final FriendService friends = mock(FriendService.class);
    final BrowserIntegrationClient browser = mock(BrowserIntegrationClient.class);
    final BrowserActivityAccess access = new BrowserActivityAccess(calls, participants, friends);
    final BrowserActivityService activities = new BrowserActivityService(access, browser);
    final ObjectMapper mapper = new ObjectMapper();
    CallSession call; Room room; CallParticipant alice; CallParticipant bob;

    CallParticipant member(Long id, String name) {
        var user = new User(); user.setId(id); user.setUsername(name);
        var p = new CallParticipant(); p.setUser(user); p.setStatus(CallParticipantStatus.JOINED);
        return p;
    }
    @BeforeEach void setup() throws Exception {
        alice = member(1L, "alice"); bob = member(2L, "bob");
        room = new Room(); room.setId(9L); room.setType(RoomType.PRIVATE); room.setMembers(List.of(alice.getUser(), bob.getUser()));
        call = new CallSession(); call.setId(7L); call.setRoom(room); call.setRoomType(RoomType.PRIVATE); call.setStatus(CallSessionStatus.ACTIVE);
        when(calls.findById(7L)).thenReturn(Optional.of(call));
        when(participants.findAllByCallSessionId(7L)).thenReturn(List.of(alice, bob));
        when(friends.areFriends(1L, 2L)).thenReturn(true);
        when(browser.command(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(mapper.readTree("{\"sessionId\":\"browser-one\",\"status\":\"active\",\"media\":{\"epoch\":\"epoch-one\"}}"));
    }
    @Test void authenticMemberOwnsOneActivityAndGuestCannotSpoofOwner() {
        var a = activities.create(7L, "alice");
        assertTrue(a.owner()); assertTrue(a.joined());
        assertThrows(RuntimeException.class, () -> activities.create(7L, "bob"));
        assertFalse(activities.find(7L, "bob").joined());
        assertNull(activities.find(7L, "bob").session());
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "bob", "media", null, null, null));
        activities.command(7L, a.activityId(), "bob", "join", null, null, null);
        assertTrue(activities.find(7L, "bob").joined());
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "bob", "close", null, null, null));
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "bob", "control/grant", null, null, null));
        activities.command(7L, a.activityId(), "bob", "leave", null, null, null);
        assertFalse(activities.find(7L, "bob").joined());
        activities.command(7L, a.activityId(), "bob", "join", null, null, null);
        activities.command(7L, a.activityId(), "alice", "close", null, null, null);
        assertNull(activities.find(7L, "alice"));
        var next = activities.create(7L, "alice"); assertNotEquals(a.activityId(), next.activityId());
        assertThrows(RuntimeException.class, () -> activities.command(7L, a.activityId(), "bob", "join", null, null, null));
    }
    @Test void rejectsNonMemberGroupInactiveNonFriendAndDepartedParticipant() {
        assertThrows(InsufficientPermissionsException.class, () -> activities.create(7L, "mallory"));
        room.setType(RoomType.GROUP);
        assertThrows(InsufficientPermissionsException.class, () -> activities.create(7L, "alice"));
        room.setType(RoomType.PRIVATE); room.setIsActive(false);
        assertThrows(InsufficientPermissionsException.class, () -> activities.create(7L, "alice"));
        room.setIsActive(true); when(friends.areFriends(1L, 2L)).thenReturn(false);
        assertThrows(InsufficientPermissionsException.class, () -> activities.create(7L, "alice"));
        when(friends.areFriends(1L, 2L)).thenReturn(true); alice.setLeftAt(LocalDateTime.now());
        assertThrows(InsufficientPermissionsException.class, () -> activities.create(7L, "alice"));
        verifyNoInteractions(browser);
    }
    @Test void revokedGuestIsRemovedAndCallEndClosesSession() {
        var a = activities.create(7L, "alice");
        activities.command(7L, a.activityId(), "bob", "join", null, null, null);
        bob.setStatus(CallParticipantStatus.LEFT); bob.setLeftAt(LocalDateTime.now());
        activities.reconcile();
        verify(browser).command(a.activityId(), "1", "revoke-member", null, null, null);
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "bob", "media", null, null, null));
        call.setStatus(CallSessionStatus.ENDED); activities.reconcile();
        verify(browser).command(a.activityId(), "1", "close", null, null, null);
    }
    @Test void forwardsExactFenceInsteadOfGrantingNewGenerationToQueuedInput() {
        var a = activities.create(7L, "alice");
        var input = mapper.createObjectNode().put("type", "text").put("text", "hello");
        activities.command(7L, a.activityId(), "alice", "input", input, "epoch-one", "4");
        verify(browser).command(a.activityId(), "1", "input", input, "epoch-one", "4");
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "alice", "invite", null, null, null));
        assertThrows(RuntimeException.class, () -> activities.command(7L, UUID.randomUUID(), "alice", "media", null, null, null));
    }
    @Test void expiredGuestMembershipCanRejoinOnlyAfterCoreAuthorization() {
        var a = activities.create(7L, "alice");
        activities.command(7L, a.activityId(), "bob", "join", null, null, null);
        when(browser.command(a.activityId(), "2", "state", null, null, null))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertFalse(activities.find(7L, "bob").joined());
        assertThrows(InsufficientPermissionsException.class, () -> activities.command(7L, a.activityId(), "bob", "media", null, null, null));
        activities.command(7L, a.activityId(), "bob", "join", null, null, null);
        verify(browser, times(2)).command(a.activityId(), "2", "join", null, null, null);
    }
}
