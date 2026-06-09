package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.zvonok.controller.dto.RestoreCallSessionResponse;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.service.dto.LiveKitTokenResponse;
import com.zvonok.service.enums.CallRestoreType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallRestoreService {

	private final CallParticipantRepository callParticipantRepository;
	private final UserService userService;
	private final LiveKitCallTokenService tokenService;

	@Transactional
	public Optional<RestoreCallSessionResponse> restoreCallSession(String username) {
		User user = userService.getUser(username);

		CallParticipant participant =
				callParticipantRepository
						.findRestoreCandidates(user.getId(),
								List.of(CallParticipantStatus.ACCEPTED,
										CallParticipantStatus.JOINED,
										CallParticipantStatus.RINGING),
								CallSessionService.ACTIVE_OR_RINGING, PageRequest.of(0, 1))
						.stream().findFirst().orElse(null);

		if (participant == null) {
			return Optional.empty();
		}

		CallSession session = participant.getCallSession();

		if (!canRestore(session, participant)) {
			return Optional.of(RestoreCallSessionResponse.empty());
			// return Optional.empty();
		}

		if (participant.getStatus() == CallParticipantStatus.RINGING) {
			return Optional.of(buildIncomingCallResponse(session, participant));
		}

		LiveKitTokenResponse token = tokenService.issueCallToken(session.getId(), username);

		participant.setLastSeenAt(LocalDateTime.now());

		return Optional.of(buildActiveCallResponse(session, participant, token));
	}

	private boolean canRestore(CallSession session, CallParticipant participant) {
		if (session == null || participant == null) {
			return false;
		}

		if (participant.getLeftAt() != null) {
			return false;
		}

		if (participant.getStatus() != CallParticipantStatus.ACCEPTED
				&& participant.getStatus() != CallParticipantStatus.JOINED
				&& participant.getStatus() != CallParticipantStatus.RINGING) {
			return false;
		}

		if (session.getStatus() != CallSessionStatus.ACTIVE
				&& session.getStatus() != CallSessionStatus.RINGING) {
			return false;
		}

		/*
		 * if (!isWithinRestoreWindow(participant)) { return false; }
		 */

		return true;
	}

	private RestoreCallSessionResponse buildActiveCallResponse(CallSession session, CallParticipant participant, LiveKitTokenResponse token) {
						
		return RestoreCallSessionResponse.builder().restorable(true).callRestoreType(CallRestoreType.ACTIVE_CALL)
				.callId(session.getId()).chatRoomId(session.getRoom().getId())
				.roomId(session.getRoom().getId()).roomType(session.getRoomType())
				.callStatus(session.getStatus()).hostUsername(session.getHostUser().getUsername()).participantStatus(participant.getStatus())
				.liveKitRoomName(session.getLivekitRoomName()).serverUrl(token.getServerUrl())
				.participantToken(token.getParticipantToken()).expiresAt(token.getExpiresAt())
				.build();
	}

	private RestoreCallSessionResponse buildIncomingCallResponse(CallSession session, CallParticipant participant) {
						
		return RestoreCallSessionResponse.builder().restorable(true).callRestoreType(CallRestoreType.INCOMING_CALL)
				.callId(session.getId()).chatRoomId(session.getRoom().getId())
				.roomId(session.getRoom().getId()).roomType(session.getRoomType())
				.callStatus(session.getStatus()).hostUsername(session.getHostUser().getUsername()).participantStatus(participant.getStatus())
				.build();
	}

	private boolean isWithinRestoreWindow(CallParticipant participant) {
		if (participant.getLastSeenAt() == null) {
			return true;
		}

		return participant.getLastSeenAt().isAfter(LocalDateTime.now().minusSeconds(90));
	}
}
