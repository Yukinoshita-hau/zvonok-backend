package com.zvonok.service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zvonok.controller.dto.RestoreCallSessionResponse;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.service.enums.CallRestoreType;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import com.zvonok.config.CallLifecycleProperties;

@Service
@RequiredArgsConstructor
public class CallRestoreStateService {
    private final CallParticipantRepository participants;
    private final UserService users;
    private final CallLifecycleProperties policy;

    @Transactional(readOnly = true)
    public Optional<RestoreCallSessionResponse> findCandidate(String username) {
        User user = users.getUser(username);
        Optional<RestoreCallSessionResponse> active = findCandidate(user,
                List.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED));
        return active.isPresent() ? active : findCandidate(user, List.of(CallParticipantStatus.RINGING));
    }

    private Optional<RestoreCallSessionResponse> findCandidate(User user, List<CallParticipantStatus> statuses) {
        return participants.findRestoreCandidates(user.getId(), statuses,
                CallSessionService.ACTIVE_OR_RINGING, PageRequest.of(0, 1)).stream()
                .filter(p -> statuses.contains(p.getStatus()) && eligible(p, user)).findFirst().map(this::response);
    }

    private boolean eligible(CallParticipant p, User user) {
        CallSession s = p.getCallSession();
        return p.getLeftAt() == null && s != null

                && (p.getStatus() != CallParticipantStatus.RINGING || s.getStartedAt() == null
                        || s.getStartedAt().plusSeconds(policy.getInvitationSeconds()).isAfter(LocalDateTime.now()))
                && CallSessionService.ACTIVE_OR_RINGING.contains(s.getStatus())
                && s.getRoom() != null && Boolean.TRUE.equals(s.getRoom().getIsActive())
                && s.getRoom().getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
    }

    private RestoreCallSessionResponse response(CallParticipant p) {
        CallSession s = p.getCallSession();
        return RestoreCallSessionResponse.builder().restorable(true)
                .callRestoreType(p.getStatus() == CallParticipantStatus.RINGING
                        ? CallRestoreType.INCOMING_CALL : CallRestoreType.ACTIVE_CALL)
                .callId(s.getId()).chatRoomId(s.getRoom().getId()).roomId(s.getRoom().getId())
                .roomType(s.getRoomType()).callStatus(s.getStatus())
                .hostUsername(s.getHostUser().getUsername()).participantStatus(p.getStatus())
                .liveKitRoomName(s.getLivekitRoomName()).startedAt(s.getStartedAt())
                .activatedAt(s.getActivatedAt()).build();
    }
}
