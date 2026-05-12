package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

	public void cleanupDuplicateActiveSessions(Long roomId) {
		List<CallSession> candidates = callSessionRepository
			.findAllActiveByRoomIdForUpdate(roomId, CallSessionService.ACTIVE_OR_RINGING);
		
		if (candidates.size() <= 1) {
			return;
		}	 

		List<CallSession> stale = candidates.subList(1, candidates.size());

		stale.stream().forEach(s -> endSystemIfStale(s, CallEndReason.STALE_CLEANUP));
	}


	public void endSystemIfStale(CallSession session, CallEndReason reason) {
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
