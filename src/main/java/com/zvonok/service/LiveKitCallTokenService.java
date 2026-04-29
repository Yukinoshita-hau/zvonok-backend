package com.zvonok.service;

import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.service.dto.LiveKitTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiveKitCallTokenService {

    private static final Set<CallParticipantStatus> ALLOWED_PARTICIPANT_STATUSES =
            Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED);

    private final CallSessionService callSessionService;
    private final LiveKitTokenService liveKitTokenService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public LiveKitTokenResponse issueCallToken(Long callId, String username) {
        CallSession session = callSessionService.getCallSession(callId);
        CallParticipant participant = callSessionService.getParticipant(callId, username);

        if (participant == null) {
            throw new InsufficientPermissionsException("User is not participant of this call");
        }

        validateCallStatus(session);
        validateParticipantStatus(participant, session);

        return liveKitTokenService.generateCallToken(session.getLivekitRoomName(), username,
                userService.getUser(username).getDisplayName(), callId);
    }

    private void validateCallStatus(CallSession session) {
        if (session.getRoomType() == RoomType.PRIVATE) {
            if (session.getStatus() != CallSessionStatus.RINGING
                    && session.getStatus() != CallSessionStatus.ACTIVE) {
                throw new InsufficientPermissionsException("Call is not available for token issuance");
            }
            return;
        }

        if (session.getStatus() != CallSessionStatus.ACTIVE) {
            throw new InsufficientPermissionsException("Group call must be ACTIVE to issue token");
        }
    }

    private void validateParticipantStatus(CallParticipant participant, CallSession session) {
        if (ALLOWED_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
            return;
        }

        if (session.getRoomType() == RoomType.PRIVATE && participant.getStatus() == CallParticipantStatus.RINGING) {
            throw new InsufficientPermissionsException("Receiver must accept private call before token issuance");
        }

        throw new InsufficientPermissionsException("Participant status does not allow token issuance");
    }
}
