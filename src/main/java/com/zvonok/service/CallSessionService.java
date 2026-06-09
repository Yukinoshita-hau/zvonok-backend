package com.zvonok.service;

import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.ActiveCallResponse;
import com.zvonok.controller.dto.CallParticipantResponse;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.controller.dto.LeaveCallDto;
import com.zvonok.exception.CallSessionNotFoundException;
import com.zvonok.exception.CallStateConflictException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallEndReason;
import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import com.zvonok.service.dto.BaseCallEvent;
import com.zvonok.service.dto.CallTokenContext;
import com.zvonok.service.dto.CallType;
import com.zvonok.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallSessionService {

	public static final Set<CallSessionStatus> ACTIVE_OR_RINGING =
			Set.of(CallSessionStatus.RINGING, CallSessionStatus.ACTIVE);

	private final RoomService roomService;
	private final UserService userService;
	private final CallSessionRepository callSessionRepository;
	private final CallParticipantRepository callParticipantRepository;
	private final CallEventPublisher callEventPublisher;
	private final LiveKitRoomAdminService liveKitRoomAdminService;
	private final CallSessionCleanupService callSessionCleanupService;

	@Transactional
	public CallSession startCall(String callerUsername, InviteCallDto dto) {
		Room room = roomService.getRoomForUpdate(dto.getChatRoomId(), callerUsername);
		User caller = userService.getUser(callerUsername);

		CallSession existing = callSessionRepository
				.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(room.getId(), ACTIVE_OR_RINGING)
				.orElse(null);

		if (existing != null) {
			throw new CallStateConflictException(
					"Cannot start a new call while another call is active or ringing");
		}

		if (room.getType() == RoomType.PRIVATE && room.getMembers().size() != 2) {
			throw new CallStateConflictException("Private call requires exactly 2 members");
		}

		CallSession session = new CallSession();
		session.setRoom(room);
		session.setRoomType(room.getType());
		session.setCreatedBy(caller);
		session.setHostUser(caller);
		session.setStartedAt(LocalDateTime.now());
		session.setLivekitRoomName(createPendingLiveKitRoomName(room));
		if (room.getType() == RoomType.PRIVATE) {
			session.setStatus(CallSessionStatus.RINGING);
		} else {
			session.setStatus(CallSessionStatus.ACTIVE);
			session.setActivatedAt(LocalDateTime.now());
		}
		session = callSessionRepository.save(session);

		session.setLivekitRoomName(createLiveKitRoomName(room, session.getId()));
		session = callSessionRepository.save(session);

		if (room.getType() == RoomType.PRIVATE) {
			createPrivateParticipants(session, caller, room);
			publishCallStartedToHost(session, caller);
			publishPrivateInvite(session, caller);
		} else {
			createGroupParticipants(session, caller, room);
			publishCallStartedToHost(session, caller);
			publishGroupStart(session, caller);
		}

		return session;
	}

	@Transactional
	public CallSession accept(String username, AcceptCallDto dto) {
		User actor = userService.getUser(username);
		CallSession session =
				resolveSessionForAction(username, dto.getCallId(), dto.getChatRoomId());

		if (isTerminal(session.getStatus())) {
			return session;
		}

		CallParticipant participant = findOrCreateGroupParticipant(session, actor);

		if (participant.getStatus() == CallParticipantStatus.ACCEPTED
				|| participant.getStatus() == CallParticipantStatus.JOINED) {
			return session;
		}

		if (session.getRoomType() == RoomType.PRIVATE) {
			ensurePrivateReceiver(session, actor);
			if (session.getStatus() != CallSessionStatus.RINGING) {
				return session;
			}
			participant.setStatus(CallParticipantStatus.ACCEPTED);
			participant.setAcceptedAt(LocalDateTime.now());
			participant.setLastSeenAt(LocalDateTime.now());
			callParticipantRepository.save(participant);

			session.setStatus(CallSessionStatus.ACTIVE);

			if (session.getActivatedAt() == null) {
				session.setActivatedAt(LocalDateTime.now());
			}
			callSessionRepository.save(session);

			publishToParticipants(session, event(CallType.CALL_ACCEPTED, session, username,
					CallParticipantStatus.ACCEPTED));
			publishToParticipants(session,
					event(CallType.CALL_ACCEPT, session, username, CallParticipantStatus.ACCEPTED));
			return session;
		}

		participant.setStatus(CallParticipantStatus.ACCEPTED);
		callParticipantRepository.save(participant);

		publishToParticipants(session, event(CallType.CALL_PARTICIPANT_JOINED, session, username,
				CallParticipantStatus.ACCEPTED));
		return session;
	}

	@Transactional
	public CallSession decline(String username, DeclineCallDto dto) {
		User actor = userService.getUser(username);
		CallSession session =
				resolveSessionForAction(username, dto.getCallId(), dto.getChatRoomId());
		CallParticipant participant = callParticipantRepository
				.findByCallSessionIdAndUserId(session.getId(), actor.getId()).orElseThrow(
						() -> new InsufficientPermissionsException("User is not call participant"));

		if (isTerminal(session.getStatus())) {
			return session;
		}

		participant.setStatus(CallParticipantStatus.DECLINED);
		callParticipantRepository.save(participant);

		if (session.getRoomType() == RoomType.PRIVATE) {
			finishCall(session, CallEndReason.DECLINED, actor, false, false);

			publishToParticipants(session, event(CallType.CALL_DECLINED, session, username,
					CallParticipantStatus.DECLINED));
			publishToParticipants(session, event(CallType.CALL_DECLINE, session, username,
					CallParticipantStatus.DECLINED));
			return session;
		}

		publishHostOnly(session, event(CallType.CALL_PARTICIPANT_DECLINED, session, username,
				CallParticipantStatus.DECLINED));
		return session;
	}

	@Transactional
	public CallSession leave(String username, LeaveCallDto dto) {
		User actor = userService.getUser(username);
		CallSession session =
				resolveSessionForAction(username, dto.getCallId(), dto.getChatRoomId());
		CallParticipant participant = callParticipantRepository
				.findByCallSessionIdAndUserId(session.getId(), actor.getId()).orElseThrow(
						() -> new InsufficientPermissionsException("User is not call participant"));

		if (isTerminal(session.getStatus())) {
			return session;
		}

		participant.setStatus(CallParticipantStatus.LEFT);
		participant.setLeftAt(LocalDateTime.now());
		callParticipantRepository.save(participant);

		publishToParticipants(session, event(CallType.CALL_PARTICIPANT_LEFT, session, username,
				CallParticipantStatus.LEFT));

		if (session.getRoomType() == RoomType.PRIVATE) {
			finishCall(session, CallEndReason.USER_LEFT, actor, false, true);
			return session;
		}

		long activeCount =
				callParticipantRepository.countByCallSessionIdAndStatusIn(session.getId(),
						Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED));

		if (activeCount == 0) {
			finishCall(session, CallEndReason.NO_ACTIVE_PARTICIPANTS, null, true, true);
		}

		return session;
	}

	@Transactional
	public CallSession end(String username, EndCallDto dto) {
		User actor = userService.getUser(username);
		CallSession session =
				resolveSessionForAction(username, dto.getCallId(), dto.getChatRoomId());
		CallParticipant participant = callParticipantRepository
				.findByCallSessionIdAndUserId(session.getId(), actor.getId()).orElseThrow(
						() -> new InsufficientPermissionsException("User is not call participant"));

		if (session.getStatus() == CallSessionStatus.ENDED) {
			return session;
		}

		if (session.getRoomType() == RoomType.GROUP
				&& participant.getRole() != CallParticipantRole.HOST) {
			throw new InsufficientPermissionsException("Only host can end group call in phase 1");
		}

		finishCall(session, CallEndReason.HOST_ENDED, actor, true, true);

		return session;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void assertTokenStillAllowedAndTouch(Long callId, String username,
			Set<CallSessionStatus> allowedCallStatuses,
			Set<CallParticipantStatus> allowedParticipantStatuses) {

		int updated = callParticipantRepository.touchLastSeenIfTokenAllowed(callId, username,
				LocalDateTime.now(), allowedCallStatuses, allowedParticipantStatuses);

		if (updated == 0) {
			throw new CallStateConflictException(
					HttpResponseMessage.HTTP_LIVEKIT_CALL_STATE_CONFLICT_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markLiveKitRoomReady(Long callId) {
		int updated = callSessionRepository.markLiveKitRoomReadyIfAbsent(callId,
				LocalDateTime.now(), ACTIVE_OR_RINGING);

		if (updated == 0) {
			// TODO: надо будет чекнуть не end ли сесия
		}
		/*
		 * CallSession session = getCallSessionForUpdate(callId);
		 * 
		 * if (session.getLivekitRoomReadyAt() == null) {
		 * session.setLivekitRoomReadyAt(LocalDateTime.now()); callSessionRepository.save(session);
		 * }
		 */
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void endSystemIfStale(Long callId, CallEndReason reason) {
		if (callId == null)
			return;

		CallSession managedSession = getCallSessionForUpdate(callId);

		if (isTerminal(managedSession.getStatus())) {
			return;
		}

		finishCall(managedSession, reason, null, true, true);
	}

	@Transactional(readOnly = true)
	public CallSession getCallSession(Long callId) {
		return callSessionRepository.findById(callId)
				.orElseThrow(() -> new CallSessionNotFoundException(
						HttpResponseMessage.HTTP_CALL_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	@Transactional
	public CallSession getCallSessionForUpdate(Long callId) {
		return callSessionRepository.findByIdForUpdate(callId)
				.orElseThrow(() -> new CallSessionNotFoundException(
						HttpResponseMessage.HTTP_CALL_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	@Transactional(readOnly = true)
	public CallParticipant getParticipant(Long callId, String username) {
		User user = userService.getUser(username);
		return callParticipantRepository.findByCallSessionIdAndUserId(callId, user.getId())
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public boolean hasActiveOrRingingCall(Long roomId) {
		return callSessionRepository.existsByRoomIdAndStatusIn(roomId, ACTIVE_OR_RINGING);
	}

	@Transactional(readOnly = true)
	public List<CallParticipant> getParticipants(Long callId) {
		return callParticipantRepository.findAllByCallSessionId(callId);
	}

	@Transactional
	public ActiveCallResponse findActiveCallResponseFromDB(Long roomId, String username) {
		Room room = roomService.getRoom(roomId, username);

		List<CallSession> candidates = callSessionRepository
				.findAllByRoomIdAndStatusInOrderByCreatedAtDesc(roomId, ACTIVE_OR_RINGING);

		if (candidates.isEmpty()) {
			return null;
		}

		CallSession freshest = candidates.getFirst();

		if (candidates.size() > 1) {
			callSessionCleanupService.cleanupDuplicateActiveSessions(roomId);
		}

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(freshest.getId());


		return ActiveCallResponse.builder().callId(freshest.getId()).chatRoomId(room.getId())
				.roomId(room.getId()).roomType(freshest.getRoomType()).status(freshest.getStatus())
				.liveKitRoomName(freshest.getLivekitRoomName())
				.hostUsername(freshest.getHostUser().getUsername())
				.callerUsername(freshest.getCreatedBy().getUsername())
				.callType(freshest.getRoomType().name())
				.participants(participants.stream().map(p -> toParticipantResponse(p)).toList())
				.startedAt(freshest.getStartedAt()).activatedAt(freshest.getActivatedAt())
				.createAt(freshest.getCreatedAt()).build();
	}

	@Transactional(readOnly = true)
	public CallTokenContext getCallTokenContext(Long callId, String username) {
		User user = userService.getUser(username);

		CallSession session = callSessionRepository.findById(callId)
				.orElseThrow(() -> new CallSessionNotFoundException(
						HttpResponseMessage.HTTP_CALL_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		CallParticipant participant =
				callParticipantRepository.findByCallSessionIdAndUserId(callId, user.getId())
						.orElseThrow(() -> new InsufficientPermissionsException(
								"User is not participant of this call"));

		return new CallTokenContext(session.getId(), session.getLivekitRoomName(),
				session.getRoomType(), session.getStatus(), session.getLivekitRoomReadyAt(),
				username, user.getDisplayName(), participant.getStatus());
	}


	private CallSession resolveSessionForAction(String username, Long callId, Long roomId) {
		if (callId != null) {
			// CallSession session = getCallSession(callId);
			CallSession session = getCallSessionForUpdate(callId);
			roomService.getRoom(session.getRoom().getId(), username);
			return session;
		}

		if (roomId == null) {
			throw new CallSessionNotFoundException("callId or chatRoomId is required");
		}

		Room room = roomService.getRoom(roomId, username);
		return callSessionRepository
				.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(room.getId(), ACTIVE_OR_RINGING)
				.orElseThrow(() -> new CallSessionNotFoundException(
						"Active call session not found for room"));
	}

	private void createPrivateParticipants(CallSession session, User caller, Room room) {
		User receiver = room.getMembers().stream().filter(m -> !m.getId().equals(caller.getId()))
				.findFirst().orElseThrow(
						() -> new CallStateConflictException("Receiver not found in private room"));

		callParticipantRepository.save(createParticipant(session, caller, CallParticipantRole.HOST,
				CallParticipantStatus.ACCEPTED));
		callParticipantRepository.save(createParticipant(session, receiver,
				CallParticipantRole.MEMBER, CallParticipantStatus.RINGING));
	}

	private void createGroupParticipants(CallSession session, User initiator, Room room) {
		for (User member : room.getMembers()) {
			if (member.getId().equals(initiator.getId())) {
				callParticipantRepository.save(createParticipant(session, member,
						CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED));
			} else {
				callParticipantRepository.save(createParticipant(session, member,
						CallParticipantRole.MEMBER, CallParticipantStatus.RINGING));
			}
		}
	}

	private CallParticipant createParticipant(CallSession session, User user,
			CallParticipantRole role, CallParticipantStatus status) {
		CallParticipant participant = new CallParticipant();
		participant.setCallSession(session);
		participant.setUser(user);
		participant.setIdentity(user.getUsername());
		participant.setDisplayName(user.getDisplayName());
		participant.setRole(role);
		participant.setStatus(status);

		if (status == CallParticipantStatus.JOINED)
			participant.setJoinedAt(LocalDateTime.now());

		if (status == CallParticipantStatus.ACCEPTED)
			participant.setAcceptedAt(LocalDateTime.now());
		return participant;
	}

	private void publishCallStartedToHost(CallSession session, User caller) {
		BaseCallEvent event = event(CallType.CALL_STARTED, session, caller.getUsername(),
				CallParticipantStatus.ACCEPTED);
		TransactionUtils
				.runAfterCommit(() -> callEventPublisher.sendToUser(caller.getUsername(), event));
	}

	private void publishPrivateInvite(CallSession session, User caller) {
		CallParticipant receiver = callParticipantRepository.findAllByCallSessionId(session.getId())
				.stream().filter(p -> !p.getUser().getId().equals(caller.getId())).findFirst()
				.orElseThrow(
						() -> new CallStateConflictException("Receiver participant not found"));

		BaseCallEvent event =
				event(CallType.CALL_INVITE, session, caller.getUsername(), receiver.getStatus());
		TransactionUtils.runAfterCommit(
				() -> callEventPublisher.sendToUser(receiver.getUser().getUsername(), event));
	}

	private void publishGroupStart(CallSession session, User caller) {
		BaseCallEvent event = event(CallType.CALL_INVITE, session, caller.getUsername(),
				CallParticipantStatus.RINGING);

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		TransactionUtils.runAfterCommit(() -> participants.stream()
				.filter(p -> !p.getUser().getId().equals(caller.getId()))
				.forEach(p -> callEventPublisher.sendToUser(p.getUser().getUsername(), event)));
	}

	private void publishHostOnly(CallSession session, BaseCallEvent event) {
		TransactionUtils.runAfterCommit(
				() -> callEventPublisher.sendToUser(session.getHostUser().getUsername(), event));
	}

	private void publishToParticipants(CallSession session, BaseCallEvent event) {
		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		TransactionUtils.runAfterCommit(() -> participants
				.forEach(p -> callEventPublisher.sendToUser(p.getUser().getUsername(), event)));
	}

	private BaseCallEvent event(CallType type, CallSession session, String actorUsername,
			CallParticipantStatus participantStatus) {
		BaseCallEvent event = new BaseCallEvent();
		event.setEventId(UUID.randomUUID().toString());
		event.setType(type);
		event.setCallId(session.getId());
		event.setChatRoomId(session.getRoom().getId());
		event.setRoomId(session.getRoom().getId());
		event.setRoomType(session.getRoomType());
		event.setLiveKitRoomName(session.getLivekitRoomName());
		event.setCallerUsername(session.getCreatedBy().getUsername());
		event.setHostUsername(session.getHostUser().getUsername());
		event.setParticipantUsername(actorUsername);
		event.setCallRoomType(session.getRoomType());
		event.setCallStatus(session.getStatus());
		event.setParticipantStatus(participantStatus);
		event.setEndReason(session.getEndReason());
		event.setParticipantsCount(
				(int) callParticipantRepository.countByCallSessionIdAndStatusIn(session.getId(),
						Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED)));
		event.setOccurredAt(LocalDateTime.now());
		return event;
	}

	private void finishCall(CallSession session, CallEndReason reason, User endedByUser,
			boolean deleteLiveKitRoom, boolean notifyParticipants) {
		LocalDateTime now = LocalDateTime.now();

		session.setStatus(CallSessionStatus.ENDED);
		session.setEndedAt(now);
		session.setEndedByUser(endedByUser);
		session.setEndReason(reason);
		callSessionRepository.save(session);

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		participants.forEach(participant -> {
			if (participant.getStatus() == CallParticipantStatus.JOINED
					|| participant.getStatus() == CallParticipantStatus.ACCEPTED
					|| participant.getStatus() == CallParticipantStatus.RINGING) {
				participant
						.setStatus(reason == CallEndReason.DECLINED ? CallParticipantStatus.DECLINED
								: CallParticipantStatus.LEFT);
				participant.setLeftAt(now);
			}
			participant.setLastSeenAt(now);
		});
		callParticipantRepository.saveAll(participants);

		if (notifyParticipants) {
			BaseCallEvent endedEvent = event(CallType.CALL_ENDED, session,
					session.getHostUser().getUsername(), CallParticipantStatus.LEFT);
			endedEvent.setParticipantsCount(0);
			endedEvent.setEndReason(reason);
			publishToParticipants(session, endedEvent);
		}

		if (deleteLiveKitRoom) {
			String roomName = session.getLivekitRoomName();
			TransactionUtils.runAfterCommit(() -> liveKitRoomAdminService.deleteRoom(roomName));
		}
	}

	private CallParticipantResponse toParticipantResponse(CallParticipant participant) {
		return CallParticipantResponse.builder().userId(participant.getUser().getId())
				.username(participant.getUser().getUsername())
				.displayName(participant.getUser().getDisplayName())
				.avatarUrl(participant.getUser().getAvatarUrl()).status(participant.getStatus())
				.joinedAt(participant.getJoinedAt()).acceptedAt(participant.getAcceptedAt())
				.leftAt(participant.getLeftAt()).build();
	}

	private String createPendingLiveKitRoomName(Room room) {
		return "pending-" + room.getId() + "-" + UUID.randomUUID();
	}

	private String createLiveKitRoomName(Room room, Long callSessionId) {
		return switch (room.getType()) {
			case PRIVATE -> "dm-" + room.getId() + "-call-" + callSessionId;
			case GROUP -> "group-" + room.getId() + "-call-" + callSessionId;
		};
	}

	private void ensurePrivateReceiver(CallSession session, User actor) {
		if (session.getHostUser().getId().equals(actor.getId())) {
			throw new InsufficientPermissionsException("Host cannot accept private call");
		}
	}

	private boolean isTerminal(CallSessionStatus status) {
		return status == CallSessionStatus.ENDED;
	}

	private CallParticipant findOrCreateGroupParticipant(CallSession session, User actor) {
		return callParticipantRepository
				.findByCallSessionIdAndUserId(session.getId(), actor.getId()).orElseGet(() -> {
					if (session.getRoomType() == RoomType.PRIVATE) {
						throw new InsufficientPermissionsException("User is not call participant");
					}

					roomService.getRoom(session.getRoom().getId(), actor.getUsername());
					CallParticipant participant = createParticipant(session, actor,
							CallParticipantRole.MEMBER, CallParticipantStatus.ACCEPTED);
					return callParticipantRepository.save(participant);
				});
	}
}
