package com.zvonok.service;

import com.zvonok.exception.CallStateConflictException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.LiveKitRoomNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.enumeration.CallEndReason;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.service.dto.CallTokenContext;
import com.zvonok.service.dto.LiveKitTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiveKitCallTokenService {

    private static final Set<CallParticipantStatus> ALLOWED_PARTICIPANT_STATUSES =
            Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED);

    private final CallSessionService callSessionService;
    private final LiveKitTokenService liveKitTokenService;
	private final LiveKitRoomAdminService liveKitRoomAdminService;

	/*	TODO: крч то что это транзакция в которой проиходит межсетевой запрос это плохо по ряду причин которые ты знаешь
	 *  TODO: и надо вынести всё что связанно с транзакции в иное место
	 *  TODO: а где стучусь к livekit делать без транзакции
	 *  TODO: крч ты знаешь что делать
	 */
	public LiveKitTokenResponse issueCallToken(Long callId, String username) {
		CallTokenContext context = callSessionService.getCallTokenContext(callId, username);

		validateCallStatus(context);
		validateParticipantStatus(context);

		ensureLiveKitRoomForToken(context);

		Set<CallSessionStatus> allowedCallStatuses = 
			context.roomType() == RoomType.PRIVATE
				? Set.of(CallSessionStatus.ACTIVE, CallSessionStatus.RINGING)
				: Set.of(CallSessionStatus.ACTIVE);

		callSessionService.assertTokenStillAllowedAndTouch(context.callId(), username, allowedCallStatuses, ALLOWED_PARTICIPANT_STATUSES);

		return liveKitTokenService.generateCallToken(
				context.livekitRoomName(),
				username,
				context.displayName(),
				context.callId()
		);
	}

	private void ensureLiveKitRoomForToken(CallTokenContext context) {
		if (shouldLiveKitRoomAlreadyExist(context)) {
			try {
				liveKitRoomAdminService.requireExistingRoom(context.livekitRoomName());
			} catch (LiveKitRoomNotFoundException e) {
				callSessionService.endSystemIfStale(
						context.callId(), 
						CallEndReason.STALE_CLEANUP
				);

				throw new CallStateConflictException(
						HttpResponseMessage.HTTP_LIVEKIT_CALL_STATE_CONFLICT_RESPONSE_MESSAGE.getMessage()
				);
			}

			return;
		}

		liveKitRoomAdminService.ensureRoomReady(context.livekitRoomName());
		callSessionService.markLiveKitRoomReady(context.callId());
	}

	private void validateCallStatus(CallTokenContext context) {
		if (context.roomType() == RoomType.PRIVATE) {
			if (context.callStatus() != CallSessionStatus.ACTIVE
					&& context.callStatus() != CallSessionStatus.RINGING) {
				throw new InsufficientPermissionsException("Call is not available for token issuance");
			}
			return;
		}

		if (context.callStatus() != CallSessionStatus.ACTIVE) {
			throw new InsufficientPermissionsException("Group call must be ACTIVE to issue token");
		}
	}

	private void validateParticipantStatus(CallTokenContext context) {
		if (ALLOWED_PARTICIPANT_STATUSES.contains(context.participantStatus())) {
			return;
		}

		if (context.roomType() == RoomType.PRIVATE
				&& context.participantStatus() == CallParticipantStatus.RINGING) {
			throw new InsufficientPermissionsException("Receiver must accept private call before token issuance");	
		}

		throw new InsufficientPermissionsException(
				"Participant status does not allow token issuance"
		);
	}

	private boolean shouldLiveKitRoomAlreadyExist(CallTokenContext context) {
		if (context.callStatus() != CallSessionStatus.ACTIVE) {
			return false;
		}

		if (context.livekitRoomReadyAt() == null) {
			return false;
		}

		return context.livekitRoomReadyAt()
			.isBefore(LocalDateTime.now().minusSeconds(10));
	}
}
