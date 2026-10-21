package com.zvonok.service;

import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.User;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallMediaOwnershipTest {
    CallSessionRepository calls = mock(CallSessionRepository.class);
    CallParticipantRepository participants = mock(CallParticipantRepository.class);
    CallParticipant participant = new CallParticipant();
    CallMediaOwnershipService ownership = new CallMediaOwnershipService(calls, participants);

    CallMediaOwnershipTest() {
        User user = new User(); user.setUsername("alice");
        participant.setUser(user);
        when(calls.findByIdForUpdate(1L)).thenReturn(Optional.of(new CallSession()));
        when(participants.findByCallSessionIdAndUserUsername(1L, "alice")).thenReturn(Optional.of(participant));
    }

    @Test void anotherClientCannotTakeAnOwnedCallAutomatically() {
        assertTrue(ownership.claim(1L, "alice", "one", false));
        assertFalse(ownership.claim(1L, "alice", "two", false));
        assertEquals("one", participant.getMediaClientId());
    }

    @Test void existingSfuParticipantBlocksAnUnownedLegacyConnection() {
        var response = ownership.issue(1L, "alice", "one", false, true, () -> {
            fail("An already connected identity must not receive a competing token");
            return null;
        });
        assertEquals("PASSIVE", response.getMediaOwnership());
        assertNull(response.getParticipantToken());
        assertNull(participant.getMediaClientId());
    }

    @Test void transferWaitsForOldClientToDisconnectAndRelease() {
        ownership.claim(1L, "alice", "one", false);
        assertFalse(ownership.claim(1L, "alice", "two", true));
        assertEquals("RELEASE", ownership.renew(1L, "alice", "one"));
        assertFalse(ownership.claim(1L, "alice", "three", true));
        ownership.release(1L, "alice", "three");
        assertEquals("one", participant.getMediaClientId());
        ownership.release(1L, "alice", "one");
        assertTrue(ownership.claim(1L, "alice", "two", false));
        ownership.release(1L, "alice", "one");
        assertEquals("two", participant.getMediaClientId());
        assertFalse(ownership.claim(1L, "alice", "one", false));
    }

    @Test void expiredClientCannotRenewOrReconnectWithNewToken() {
        ownership.claim(1L, "alice", "one", false);
        participant.setMediaLeaseUntil(LocalDateTime.now().minusSeconds(1));
        assertEquals("RELEASE", ownership.renew(1L, "alice", "one"));
        assertFalse(ownership.claim(1L, "alice", "one", false));
        assertTrue(ownership.claim(1L, "alice", "two", false));
    }

    @Test void expiredLeaseDoesNotDisplaceOwnerStillPresentInSfu() {
        ownership.claim(1L, "alice", "one", false);
        participant.setMediaLeaseUntil(LocalDateTime.now().minusSeconds(1));
        var response = ownership.issue(1L, "alice", "two", false, true, () -> {
            fail("SFU presence must prevent a competing token after lease expiry");
            return null;
        });
        assertEquals("PASSIVE", response.getMediaOwnership());
        assertEquals("one", participant.getMediaClientId());
    }
}
