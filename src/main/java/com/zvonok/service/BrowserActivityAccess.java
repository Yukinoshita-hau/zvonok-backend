package com.zvonok.service;

import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrowserActivityAccess {
    private final CallSessionRepository calls;
    private final CallParticipantRepository participants;
    private final FriendService friends;

    @Transactional(readOnly = true)
    public List<CallParticipant> require(Long callId, String username) {
        var call = calls.findById(callId).orElseThrow(BrowserActivityAccess::denied);
        var room = call.getRoom();
        if (call.getStatus() != CallSessionStatus.ACTIVE || call.getRoomType() != RoomType.PRIVATE
                || room.getType() != RoomType.PRIVATE || !Boolean.TRUE.equals(room.getIsActive())
                || room.getMembers().size() != 2
                || room.getMembers().stream().noneMatch(u -> u.getUsername().equals(username))
                || !friends.areFriends(room.getMembers().get(0).getId(), room.getMembers().get(1).getId())) throw denied();
        var members = participants.findAllByCallSessionId(callId).stream()
                .filter(p -> p.getLeftAt() == null
                        && (p.getStatus() == CallParticipantStatus.ACCEPTED || p.getStatus() == CallParticipantStatus.JOINED)
                        && room.getMembers().stream().anyMatch(u -> u.getId().equals(p.getUser().getId())))
                .toList();
        if (members.stream().noneMatch(p -> p.getUser().getUsername().equals(username))) throw denied();
        return members;
    }

    private static InsufficientPermissionsException denied() {
        return new InsufficientPermissionsException("Active two-friend DM call membership required");
    }
}
