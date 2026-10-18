package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import com.zvonok.config.CallLifecycleProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.enumeration.CallEndReason;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import com.zvonok.service.dto.BaseCallEvent;
import com.zvonok.service.dto.CallType;
import com.zvonok.utils.TransactionUtils;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
@Transactional
public class CallSessionCleanupService {

	private final CallSessionRepository callSessionRepository;
	private final CallParticipantRepository callParticipantRepository;
	private final CallEventPublisher callEventPublisher;
	private final LiveKitRoomAdminService liveKit;
	private final CallLifecycleProperties policy;

	public record Snapshot(Long id, String roomName, Long version, LocalDateTime startedAt,
			CallSessionStatus status, LocalDateTime emptySince) {}

	@Transactional(readOnly = true)
	public List<Snapshot> snapshots(long afterId) {
		return callSessionRepository.findByStatusInAndIdGreaterThanOrderByIdAsc(
				CallSessionService.ACTIVE_OR_RINGING, afterId, PageRequest.of(0, 100)).stream()
				.map(s -> new Snapshot(s.getId(), s.getLivekitRoomName(), s.getVersion(),
						s.getStartedAt(), s.getStatus(), s.getMediaEmptySince())).toList();
	}

	public void expireInvitations(Snapshot snapshot, LocalDateTime now) {
		CallSession session = callSessionRepository.findByIdForUpdate(snapshot.id()).orElse(null);
		if (session == null || session.getStatus() == CallSessionStatus.ENDED) return;
		if (session.getStartedAt().plusSeconds(policy.getInvitationSeconds()).isAfter(now)) return;
		if (session.getStatus() == CallSessionStatus.RINGING) {
			endSystemIfStale(session, CallEndReason.CANCELLED);
			return;
		}
		// Group invitations expire independently; an ongoing group call survives.
		List<CallParticipant> participants = callParticipantRepository.findAllByCallSessionId(session.getId());
		for (CallParticipant p : participants) {
			if (p.getStatus() != CallParticipantStatus.RINGING) continue;
			p.setStatus(CallParticipantStatus.DECLINED);
			p.setDeclinedAt(now);
			p.setLeftAt(now);
			publishToParticipants(session, event(CallType.CALL_PARTICIPANT_DECLINED,
					session, p.getUser().getUsername(), CallParticipantStatus.DECLINED));
		}
		callParticipantRepository.saveAll(participants);
	}

	public void observeMedia(Snapshot snapshot, boolean empty, boolean confirmed,
			LocalDateTime observedAt) {
		CallSession s = callSessionRepository.findByIdForUpdate(snapshot.id()).orElse(null);
		if (s == null || s.getStatus() != CallSessionStatus.ACTIVE
				|| !Objects.equals(s.getVersion(), snapshot.version())) return;
		if (s.getMediaObservedAt() != null && !observedAt.isAfter(s.getMediaObservedAt())) return;
		s.setMediaObservedAt(observedAt);
		if (!empty) {
			s.setMediaEmptySince(null);
		} else if (!s.getStartedAt().plusSeconds(policy.getInitialConnectSeconds()).isAfter(observedAt)) {
			if (s.getMediaEmptySince() == null) {
				s.setMediaEmptySince(observedAt);
			} else if (confirmed && !s.getMediaEmptySince().plusSeconds(policy.getMediaGraceSeconds()).isAfter(observedAt)) {
				endSystemIfStale(s, CallEndReason.NO_ACTIVE_PARTICIPANTS);
			}
		}
		callSessionRepository.save(s);
	}

	private void endSystemIfStale(CallSession session, CallEndReason reason) {
		if (session == null || session.getStatus() == CallSessionStatus.ENDED) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();

		session.setStatus(CallSessionStatus.ENDED);
		session.setEndedAt(now);
		session.setEndedByUser(null);
		session.setEndReason(reason);
		callSessionRepository.save(session);

		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());

		participants.stream().filter(p -> p.getStatus() != CallParticipantStatus.DECLINED
				&& p.getStatus() != CallParticipantStatus.LEFT).forEach(p -> {
					p.setStatus(CallParticipantStatus.LEFT);
					p.setLeftAt(now);
					p.setLastSeenAt(now);
				});

		callParticipantRepository.saveAll(participants);

		publishToParticipants(session,
				event(CallType.CALL_ENDED, session, "system", CallParticipantStatus.LEFT));
		String roomName = session.getLivekitRoomName();
		TransactionUtils.runAfterCommit(() -> liveKit.deleteRoom(roomName));
	}


	private void publishToParticipants(CallSession session, BaseCallEvent event) {
		List<CallParticipant> participants =
				callParticipantRepository.findAllByCallSessionId(session.getId());
		TransactionUtils.runAfterCommit(() -> participants.forEach(p -> callEventPublisher.sendToUser(p.getUser().getUsername(), event)));
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
}
