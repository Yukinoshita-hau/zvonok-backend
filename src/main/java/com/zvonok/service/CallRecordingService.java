package com.zvonok.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.zvonok.exception.CallStateConflictException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallRecording;
import com.zvonok.model.CallSession;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallRecordingStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.repository.CallRecordingRepository;
import com.zvonok.service.enums.CallRecordingAction;
import com.zvonok.service.interfaces.CallRecordingPayload;
import com.zvonok.utils.TransactionUtils;
import jakarta.transaction.Transactional;
import livekit.LivekitEgress;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallRecordingService {

	private static final Set<CallRecordingStatus> BLOCKING_RECORDING_STATUSES = Set.of(
			CallRecordingStatus.STARTING, CallRecordingStatus.ACTIVE, CallRecordingStatus.STOPPING);

	private static final Set<CallRecordingStatus> STOPPABLE_RECORDING_STATUSES =
			Set.of(CallRecordingStatus.STARTING, CallRecordingStatus.ACTIVE);

	private final CallSessionService callSessionService;
	private final UserService userService;
	private final CallRecordingRepository callRecordingRepository;
	private final LiveKitRoomAdminService liveKitRoomAdminService;
	private final LiveKitEgressAdminService liveKitEgressAdminService;
	private final SimpMessagingTemplate messagingTemplate;
	private final String callRecordingPath = "/queue/call-recording";

	@Transactional
	public CallRecording startRecording(Long callId, String username) {
		CallSession session = callSessionService.getCallSessionForUpdate(callId);
		User actor = userService.getUser(username);

		validateCanStartRecording(session, actor);

		callRecordingRepository.findFirstByCallSessionIdAndStatusInOrderByStartedAtDesc(
				session.getId(), BLOCKING_RECORDING_STATUSES).ifPresent(existing -> {
					throw new CallStateConflictException(
							"Recording is already active for this call");
				});

		liveKitRoomAdminService.requireExistingRoom(session.getLivekitRoomName());

		String filePath = buildRecordingFilePath(session);

		LivekitEgress.EgressInfo egressInfo = liveKitEgressAdminService
				.startRoomCompositeMp4(session.getLivekitRoomName(), filePath);

		CallRecording recording = new CallRecording();
		recording.setCallSession(session);
		recording.setStartedBy(actor);
		recording.setEgressId(egressInfo.getEgressId());
		recording.setStatus(CallRecordingStatus.STARTING);
		recording.setFilePath(filePath);
		recording.setStartedAt(LocalDateTime.now());

		recording = callRecordingRepository.save(recording);

		TransactionUtils.runAfterCommit(() -> {
			session.getRoom().getMembers().stream().forEach(m -> {
				messagingTemplate.convertAndSendToUser(m.getUsername(), callRecordingPath,
						CallRecordingPayload.builder().action(CallRecordingAction.RECORDING_START)
								.sessionId(session.getId()).build());
			});
		});
		return recording;
	}

	@Transactional
	public CallRecording stopRecording(Long callId, String username) {
		CallSession session = callSessionService.getCallSessionForUpdate(callId);
		User actor = userService.getUser(username);

		validateCanStopRecording(session, actor);

		CallRecording recording = callRecordingRepository
				.findFirstByCallSessionIdAndStatusInOrderByStartedAtDesc(session.getId(),
						BLOCKING_RECORDING_STATUSES)
				.orElseThrow(
						() -> new CallStateConflictException("No active recording for this call"));

		if (recording.getStatus() == CallRecordingStatus.STOPPING) {
			return recording;
		}

		if (recording.getStatus() != CallRecordingStatus.STARTING
				&& recording.getStatus() != CallRecordingStatus.ACTIVE) {
			throw new CallStateConflictException(
					"Recording cannot be stopped from status " + recording.getStatus());
		}

		recording.setStatus(CallRecordingStatus.STOPPING);
		callRecordingRepository.saveAndFlush(recording);

		LivekitEgress.EgressInfo egressInfo =
				liveKitEgressAdminService.stopEgress(recording.getEgressId());

		applyEgressResult(recording, egressInfo);

		recording = callRecordingRepository.save(recording);

		TransactionUtils.runAfterCommit(() -> {
			session.getRoom().getMembers().forEach(m -> {
				messagingTemplate.convertAndSendToUser(m.getUsername(), callRecordingPath,
						CallRecordingPayload.builder().action(CallRecordingAction.RECORDING_STOP)
								.sessionId(session.getId()).build());
			});
		});

		return recording;
	}

	private void applyEgressResult(CallRecording recording, LivekitEgress.EgressInfo egressInfo) {
		if (egressInfo.getStatus() == LivekitEgress.EgressStatus.EGRESS_COMPLETE) {
			recording.setStatus(CallRecordingStatus.COMPLETED);
			recording.setEndedAt(LocalDateTime.now());

			if (egressInfo.getFileResultsCount() > 0) {
				recording.setFileLocation(egressInfo.getFileResults(0).getLocation());
			}

			return;
		}

		if (egressInfo.getStatus() == LivekitEgress.EgressStatus.EGRESS_FAILED
				|| egressInfo.getStatus() == LivekitEgress.EgressStatus.EGRESS_ABORTED) {
			recording.setStatus(CallRecordingStatus.FAILED);
			recording.setEndedAt(LocalDateTime.now());
			recording.setErrorMessage(egressInfo.getError());
			return;
		}

		if (egressInfo.getStatus() == LivekitEgress.EgressStatus.EGRESS_ENDING) {
			recording.setStatus(CallRecordingStatus.STOPPING);
			return;
		}

		recording.setStatus(CallRecordingStatus.STOPPING);
	}

	private void validateCanStartRecording(CallSession session, User actor) {
		if (session.getStatus() != CallSessionStatus.ACTIVE) {
			throw new CallStateConflictException("Call is not active");
		}

		CallParticipant participant =
				callSessionService.getParticipant(session.getId(), actor.getUsername());

		if (participant == null) {
			throw new InsufficientPermissionsException("User is not participant of this call");
		}

		if (participant.getRole() != CallParticipantRole.HOST) {
			throw new InsufficientPermissionsException("Only host can start recording");
		}
	}

	private void validateCanStopRecording(CallSession session, User actor) {
		validateCanStartRecording(session, actor);
	}

	private String buildRecordingFilePath(CallSession session) {
		String timestamp =
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

		return "call-recordings/%d/%s-%s.mp4".formatted(session.getId(),
				session.getLivekitRoomName(), timestamp);
	}
}
