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
import com.zvonok.service.dto.CallType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CallSessionService {

	private static final Set<CallSessionStatus> ACTIVE_OR_RINGING =
			Set.of(CallSessionStatus.RINGING, CallSessionStatus.ACTIVE);

	private final RoomService roomService;
	private final UserService userService;
	private final CallSessionRepository callSessionRepository;
	private final CallParticipantRepository callParticipantRepository;
	private final CallEventPublisher callEventPublisher;

	public CallSession startCall(String callerUsername, InviteCallDto dto) {
		Room room = roomService.getRoom(dto.getChatRoomId(), callerUsername);
		User caller = userService.getUser(callerUsername);

		CallSession existing = callSessionRepository
				.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(room.getId(), ACTIVE_OR_RINGING)
				.orElse(null);

		if (existing != null) {
			if (room.getType() == RoomType.PRIVATE) {
				publishCallStartedToHost(existing, caller);
				publishPrivateInvite(existing, caller);
			} else {
				publishCallStartedToHost(existing, caller);
			}
			return existing;
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
		session.setStatus(room.getType() == RoomType.PRIVATE ? CallSessionStatus.RINGING
				: CallSessionStatus.ACTIVE);
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

	public CallSession accept(String username, AcceptCallDto dto) {
		User actor = userService.getUser(username);
		CallSession session =
				resolveSessionForAction(username, dto.getCallId(), dto.getChatRoomId());
		CallParticipant participant = findOrCreateGroupParticipant(session, actor);

		if (isTerminal(session.getStatus())) {
			return session;
		}

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
			session.setStatus(CallSessionStatus.DECLINED);
			session.setEndedAt(LocalDateTime.now());
			session.setEndedByUser(actor);
			session.setEndReason(CallEndReason.DECLINED);
			callSessionRepository.save(session);

			publishToParticipants(session, event(CallType.CALL_DECLINED, session, username,
					CallParticipantStatus.DECLINED));
			publishToParticipants(session, event(CallType.CALL_DECLINE, session, username,
					CallParticipantStatus.DECLINED));
			publishToParticipants(session,
					event(CallType.CALL_ENDED, session, username, CallParticipantStatus.DECLINED));
			return session;
		}

		publishHostOnly(session, event(CallType.CALL_PARTICIPANT_DECLINED, session, username,
				CallParticipantStatus.DECLINED));
		return session;
	}

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
			session.setStatus(CallSessionStatus.ENDED);
			session.setEndedAt(LocalDateTime.now());
			session.setEndedByUser(actor);
			session.setEndReason(CallEndReason.USER_ENDED);
			callSessionRepository.save(session);

			publishToParticipants(session,
					event(CallType.CALL_ENDED, session, username, CallParticipantStatus.LEFT));
			return session;
		}

		long activeCount =
				callParticipantRepository.countByCallSessionIdAndStatusIn(session.getId(),
						Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED));

		if (activeCount == 0) {
			session.setStatus(CallSessionStatus.ENDED);
			session.setEndedAt(LocalDateTime.now());
			session.setEndedByUser(actor);
			session.setEndReason(CallEndReason.ALL_LEFT);
			callSessionRepository.save(session);

			publishToParticipants(session,
					event(CallType.CALL_ENDED, session, username, CallParticipantStatus.LEFT));
		}

		return session;
	}

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

		session.setStatus(CallSessionStatus.ENDED);
		session.setEndedAt(LocalDateTime.now());
		session.setEndedByUser(actor);
		session.setEndReason(session.getRoomType() == RoomType.GROUP ? CallEndReason.HOST_ENDED
				: CallEndReason.USER_ENDED);
		callSessionRepository.save(session);

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		participants.stream().filter(p -> p.getStatus() != CallParticipantStatus.DECLINED)
				.forEach(p -> {
					p.setStatus(CallParticipantStatus.LEFT);
					p.setLeftAt(LocalDateTime.now());
				});
		callParticipantRepository.saveAll(participants);

		publishToParticipants(session,
				event(CallType.CALL_ENDED, session, username, CallParticipantStatus.LEFT));

		return session;
	}

	public void endSystemIfStale(CallSession session, CallEndReason reason) {
		if (session == null || isTerminal(session.getStatus())) {
			return;
		}

		session.setStatus(CallSessionStatus.ENDED);
		session.setEndedAt(LocalDateTime.now());
		session.setEndedByUser(session.getHostUser());
		session.setEndReason(reason);
		callSessionRepository.save(session);

		List<CallParticipant> participants = callParticipantRepository.findAllByCallSessionId(session.getId());

		participants.stream()
			.filter(p -> p.getStatus() != CallParticipantStatus.DECLINED && p.getStatus() != CallParticipantStatus.LEFT)
			.forEach(p -> {
				p.setStatus(CallParticipantStatus.LEFT);
				p.setLeftAt(LocalDateTime.now());
			});

		callParticipantRepository.saveAll(participants);

		publishToParticipants(session, event(CallType.CALL_END, session, "system", CallParticipantStatus.LEFT));
	}

	@Transactional(readOnly = true)
	public CallSession getCallSession(Long callId) {
		return callSessionRepository.findById(callId)
				.orElseThrow(() -> new CallSessionNotFoundException("Call session not found"));
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

	public ActiveCallResponse findActiveCallResponse(Long roomId, String username) {
		Room room = roomService.getRoom(roomId, username);

		List<CallSession> candidates = callSessionRepository
				.findAllByRoomIdAndStatusInOrderByCreatedAtDesc(roomId, ACTIVE_OR_RINGING);

		if (candidates.isEmpty()) {
			return null;
		}

		CallSession freshest = candidates.getFirst();

		if (candidates.size() > 1) {
			List<CallSession> stale = candidates.subList(1, candidates.size());
			stale.forEach(call -> {
				call.setStatus(CallSessionStatus.ENDED);
				call.setEndedAt(LocalDateTime.now());
				call.setEndReason(CallEndReason.STALE_CLEANUP);
			});
			callSessionRepository.saveAll(stale);
		}

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(freshest.getId());
		int participantsCount = (int) participants.stream()
				.filter(p -> p.getStatus() == CallParticipantStatus.ACCEPTED
						|| p.getStatus() == CallParticipantStatus.JOINED)
				.count();

		return ActiveCallResponse.builder().callId(freshest.getId())
				.chatRoomId(room.getId()).roomId(room.getId()).roomType(freshest.getRoomType())
				.status(freshest.getStatus()).liveKitRoomName(freshest.getLivekitRoomName())
				.hostUsername(freshest.getHostUser().getUsername())
				.callerUsername(freshest.getCreatedBy().getUsername())
				.callType(freshest.getRoomType().name())
				.participantsCount(participantsCount)
				.participants(participants.stream().map(p -> toParticipantResponse(p)).toList())
				.startedAt(freshest.getStartedAt())
				.createAt(freshest.getCreatedAt())
				.build();
	}

	private CallSession resolveSessionForAction(String username, Long callId, Long roomId) {
		if (callId != null) {
			CallSession session = getCallSession(callId);
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
		if (status == CallParticipantStatus.JOINED || status == CallParticipantStatus.ACCEPTED) {
			participant.setJoinedAt(LocalDateTime.now());
		}
		return participant;
	}

	private void publishCallStartedToHost(CallSession session, User caller) {
		BaseCallEvent event = event(CallType.CALL_STARTED, session, caller.getUsername(),
				CallParticipantStatus.ACCEPTED);
		callEventPublisher.sendToUser(caller.getUsername(), event);
	}

	private void publishPrivateInvite(CallSession session, User caller) {
		CallParticipant receiver = callParticipantRepository.findAllByCallSessionId(session.getId())
				.stream().filter(p -> !p.getUser().getId().equals(caller.getId())).findFirst()
				.orElseThrow(
						() -> new CallStateConflictException("Receiver participant not found"));

		BaseCallEvent event =
				event(CallType.CALL_INVITE, session, caller.getUsername(), receiver.getStatus());
		callEventPublisher.sendToUser(receiver.getUser().getUsername(), event);
	}

	private void publishGroupStart(CallSession session, User caller) {
		BaseCallEvent event = event(CallType.CALL_INVITE, session, caller.getUsername(),
				CallParticipantStatus.RINGING);

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		participants.stream().filter(p -> !p.getUser().getId().equals(caller.getId()))
				.forEach(p -> callEventPublisher.sendToUser(p.getUser().getUsername(), event));
	}

	private void publishHostOnly(CallSession session, BaseCallEvent event) {
		callEventPublisher.sendToUser(session.getHostUser().getUsername(), event);
	}

	private void publishToParticipants(CallSession session, BaseCallEvent event) {
		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		participants.forEach(p -> callEventPublisher.sendToUser(p.getUser().getUsername(), event));
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
		event.setParticipantsCount(
				(int) callParticipantRepository.countByCallSessionIdAndStatusIn(session.getId(),
						Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED)));
		event.setOccurredAt(LocalDateTime.now());
		return event;
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
		return status == CallSessionStatus.ENDED || status == CallSessionStatus.DECLINED
				|| status == CallSessionStatus.CANCELLED || status == CallSessionStatus.MISSED;
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
