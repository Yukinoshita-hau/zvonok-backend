package com.zvonok.service;

import com.zvonok.controller.dto.CodeAccessRequestDto;
import com.zvonok.controller.dto.CodeContentSyncRequestDto;
import com.zvonok.controller.dto.CodeCursorSyncRequestDto;
import com.zvonok.controller.dto.CodeLanguageChangeRequestDto;
import com.zvonok.controller.dto.CodeSessionDto;
import com.zvonok.controller.dto.CodeSessionEventDto;
import com.zvonok.controller.dto.CodeSessionSenderDto;
import com.zvonok.controller.dto.CodeStdinSyncRequestDto;
import com.zvonok.controller.dto.CreateCodeSessionRequestDto;
import com.zvonok.exception.CodeSessionAccessDeniedException;
import com.zvonok.exception.CodeSessionNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.InvalidCodeSessionException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.CodeSession;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.CodeSessionEventType;
import com.zvonok.repository.CodeSessionRepository;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.dto.code.CodeRunRequestDto;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import com.zvonok.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CodeSessionService {

	private static final int MAX_CODE_LENGTH = 50_000;
	private static final int MAX_STDIN_LENGTH = 20_000;
	private static final Set<CallParticipantStatus> ALLOWED_PARTICIPANT_STATUSES =
			Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED);

	private final CallSessionService callSessionService;
	private final CodeExecutionService codeExecutionService;
	private final CodeSessionRepository codeSessionRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;

	@Transactional
	public CodeSessionDto createSession(CreateCodeSessionRequestDto request, String username) {
		CallSession call = validateCallParticipantForUpdate(request.callSessionId(), username);
		validateCallCanCreateSession(call);

		CodeSession existing = codeSessionRepository
				.findFirstByCallSessionIdAndActiveTrueOrderByCreatedAtDesc(call.getId())
				.orElse(null);
		if (existing != null) {
			return toDto(existing);
		}

		String language = validateAndNormalizeLanguage(request.language());
		String code = normalizeCode(request.initialCode());
		String stdin = normalizeStdin(request.stdin());

		CodeSession session = new CodeSession();
		session.setCallSession(call);
		session.setRoomId(call.getRoom().getId());
		session.setCreatedBy(username);
		session.setActive(true);
		session.setLanguage(language);
		session.setCode(code);
		session.setStdin(stdin);
		session = codeSessionRepository.save(session);

		CodeSessionDto dto = toDto(session);
		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_SESSION_CREATED, session,
				username, dto);
		Long callSessionId = session.getCallSession().getId();
		TransactionUtils.runAfterCommit(() -> publishEvent(callSessionId, event));
		return dto;
	}

	@Transactional(readOnly = true)
	public CodeSessionDto getActiveSession(Long callSessionId, String username) {
		validateCallParticipant(callSessionId, username);
		CodeSession session = codeSessionRepository
				.findFirstByCallSessionIdAndActiveTrueOrderByCreatedAtDesc(callSessionId)
				.orElseThrow(() -> new CodeSessionNotFoundException(
						HttpResponseMessage.HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
		return toDto(session);
	}

	@Transactional
	public CodeSessionDto grantEditor(Long sessionId, CodeAccessRequestDto request,
			String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanManageSession(session, context.participant(), username);

		User editor = getActiveParticipantUser(session.getCallSession().getId(), request);
		session.setActiveEditor(editor);

		CodeSessionDto dto = toDto(session);
		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_ACCESS_GRANTED, session,
				username, dto);
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
		return dto;
	}

	@Transactional
	public CodeSessionDto revokeEditor(Long sessionId, String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanManageSession(session, context.participant(), username);

		session.setActiveEditor(null);

		CodeSessionDto dto = toDto(session);
		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_ACCESS_REVOKED, session,
				username, dto);
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
		return dto;
	}

	@Transactional
	public CodeRunResponseDto runSession(Long sessionId, String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanRunSession(session, context.participant(), username);

		CodeSessionEventDto startedEvent = buildEvent(CodeSessionEventType.CODE_RUN_STARTED,
				session, username, toDto(session));
		publishEvent(session.getCallSession().getId(), startedEvent);

		CodeRunResponseDto response = codeExecutionService.runCode(
				new CodeRunRequestDto(session.getLanguage(), session.getCode(), session.getStdin()),
				username);
		session.setLastOutput(response.stdout());
		session.setLastStatus(response.status());
		session.setLastExitCode(response.exitCode());
		session.setLastExecutionTimeMs(response.executionTimeMs());

		CodeSessionEventDto finishedEvent = buildEvent(CodeSessionEventType.CODE_RUN_FINISHED,
				session, username, toDto(session));
		TransactionUtils.runAfterCommit(
				() -> publishEvent(session.getCallSession().getId(), finishedEvent));
		return response;
	}

	@Transactional
	public CodeSessionDto closeSession(Long sessionId, String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanManageSession(session, context.participant(), username);

		session.setActive(false);
		session.setClosedAt(Instant.now());

		CodeSessionDto dto = toDto(session);
		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_SESSION_CLOSED, session,
				username, dto);
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
		return dto;
	}

	@Transactional
	public void syncContent(Long sessionId, CodeContentSyncRequestDto request, String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanEditSession(session, context.participant(), username);

		String code = validateCode(request == null ? null : request.code());
		session.setCode(code);

		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_CONTENT_SYNC, session,
				username, request);
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
	}

	@Transactional
	public void syncStdin(Long sessionId, CodeStdinSyncRequestDto request, String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanEditSession(session, context.participant(), username);

		String stdin = validateStdin(request == null ? null : request.stdin());
		session.setStdin(stdin);

		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_STDIN_SYNC, session,
				username, request);
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
	}

	@Transactional
	public void changeLanguage(Long sessionId, CodeLanguageChangeRequestDto request,
			String username) {
		CodeSession session = getSessionForUpdate(sessionId);
		ParticipantContext context = validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		ensureCanEditSession(session, context.participant(), username);

		String language = validateAndNormalizeLanguage(request == null ? null : request.language());
		session.setLanguage(language);
		if (request != null && request.code() != null) {
			session.setCode(validateCode(request.code()));
		}

		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_LANGUAGE_CHANGED, session,
				username, toDto(session));
		TransactionUtils.runAfterCommit(() -> publishEvent(session.getCallSession().getId(), event));
	}

	@Transactional(readOnly = true)
	public void syncCursor(Long sessionId, CodeCursorSyncRequestDto request, String username) {
		CodeSession session = getSession(sessionId);
		validateSessionParticipant(session, username);
		validateCallCanMutateSession(session.getCallSession());
		ensureSessionActive(session);
		validateCursor(request);
		CodeCursorSyncRequestDto normalizedRequest = normalizeCursor(request);

		CodeSessionEventDto event = buildEvent(CodeSessionEventType.CODE_CURSOR_SYNC, session,
				username, normalizedRequest);
		publishEvent(session.getCallSession().getId(), event);
	}

	private ParticipantContext validateSessionParticipant(CodeSession session, String username) {
		CallParticipant participant = callSessionService.getParticipant(
				session.getCallSession().getId(), username);
		if (participant == null || !ALLOWED_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_CODE_SESSION_USER_NOT_ACTIVE_CALL_PARTICIPANT_RESPONSE_MESSAGE
							.getMessage());
		}
		return new ParticipantContext(session.getCallSession(), participant);
	}

	private CallSession validateCallParticipant(Long callSessionId, String username) {
		CallSession call = callSessionService.getCallSession(callSessionId);
		validateCallParticipantStatus(callSessionId, username);
		return call;
	}

	private CallSession validateCallParticipantForUpdate(Long callSessionId, String username) {
		CallSession call = callSessionService.getCallSessionForUpdate(callSessionId);
		validateCallParticipantStatus(callSessionId, username);
		return call;
	}

	private void validateCallParticipantStatus(Long callSessionId, String username) {
		CallParticipant participant = callSessionService.getParticipant(callSessionId, username);
		if (participant == null || !ALLOWED_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_CODE_SESSION_USER_NOT_ACTIVE_CALL_PARTICIPANT_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void validateCallCanCreateSession(CallSession call) {
		if (call.getStatus() == CallSessionStatus.ENDED) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_CREATE_FOR_ENDED_CALL_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void validateCallCanMutateSession(CallSession call) {
		if (call.getStatus() == CallSessionStatus.ENDED) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_MUTATE_FOR_ENDED_CALL_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private CodeSession getSession(Long sessionId) {
		if (sessionId == null) {
			throw new CodeSessionNotFoundException(
					HttpResponseMessage.HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
		}
		return codeSessionRepository.findById(sessionId)
				.orElseThrow(() -> new CodeSessionNotFoundException(
						HttpResponseMessage.HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	private CodeSession getSessionForUpdate(Long sessionId) {
		if (sessionId == null) {
			throw new CodeSessionNotFoundException(
					HttpResponseMessage.HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
		}
		return codeSessionRepository.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new CodeSessionNotFoundException(
						HttpResponseMessage.HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	private void ensureSessionActive(CodeSession session) {
		if (!session.isActive()) {
			throw new CodeSessionAccessDeniedException(
					HttpResponseMessage.HTTP_CODE_SESSION_CLOSED_RESPONSE_MESSAGE.getMessage());
		}
	}

	private void ensureCanManageSession(CodeSession session, CallParticipant participant,
			String username) {
		if (session.getCreatedBy().equals(username) || isHost(participant)) {
			return;
		}
		throw new CodeSessionAccessDeniedException(
				HttpResponseMessage.HTTP_CODE_SESSION_MANAGE_DENIED_RESPONSE_MESSAGE.getMessage());
	}

	private void ensureCanEditSession(CodeSession session, CallParticipant participant,
			String username) {
		if (isHost(participant) || isActiveEditor(session, username)) {
			return;
		}
		throw new CodeSessionAccessDeniedException(
				HttpResponseMessage.HTTP_CODE_SESSION_EDIT_DENIED_RESPONSE_MESSAGE.getMessage());
	}

	private void ensureCanRunSession(CodeSession session, CallParticipant participant,
			String username) {
		if (isHost(participant) || isActiveEditor(session, username)) {
			return;
		}
		throw new CodeSessionAccessDeniedException(
				HttpResponseMessage.HTTP_CODE_SESSION_RUN_DENIED_RESPONSE_MESSAGE.getMessage());
	}

	private boolean isHost(CallParticipant participant) {
		return participant != null && participant.getRole() == CallParticipantRole.HOST;
	}

	private boolean isActiveEditor(CodeSession session, String username) {
		return session.getActiveEditor() != null
				&& username.equals(session.getActiveEditor().getUsername());
	}

	private User getActiveParticipantUser(Long callSessionId, CodeAccessRequestDto request) {
		if (request == null || request.username() == null || request.username().isBlank()) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_EDITOR_REQUIRED_RESPONSE_MESSAGE
							.getMessage());
		}
		String editorUsername = request.username().trim();
		CallParticipant participant = callSessionService.getParticipant(callSessionId,
				editorUsername);
		if (participant == null || !ALLOWED_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_EDITOR_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE
							.getMessage());
		}
		return userRepository.findByUsername(editorUsername)
				.orElseThrow(() -> new InvalidCodeSessionException(
						HttpResponseMessage.HTTP_CODE_SESSION_EDITOR_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE
								.getMessage()));
	}

	private String validateAndNormalizeLanguage(String language) {
		if (language == null || language.isBlank()) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_LANGUAGE_REQUIRED_RESPONSE_MESSAGE
							.getMessage());
		}
		return codeExecutionService.normalizeLanguage(language);
	}

	private String normalizeCode(String code) {
		if (code == null) {
			return "";
		}
		return validateCode(code);
	}

	private String validateCode(String code) {
		if (code == null) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_CODE_REQUIRED_RESPONSE_MESSAGE
							.getMessage());
		}
		if (code.length() > MAX_CODE_LENGTH) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_CODE_TOO_LARGE_RESPONSE_MESSAGE
							.getMessage());
		}
		return code;
	}

	private String normalizeStdin(String stdin) {
		if (stdin == null) {
			return "";
		}
		return validateStdin(stdin);
	}

	private String validateStdin(String stdin) {
		if (stdin == null) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_STDIN_REQUIRED_RESPONSE_MESSAGE
							.getMessage());
		}
		if (stdin.length() > MAX_STDIN_LENGTH) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_STDIN_TOO_LARGE_RESPONSE_MESSAGE
							.getMessage());
		}
		return stdin;
	}

	private void validateCursor(CodeCursorSyncRequestDto request) {
		if (request == null || request.lineNumber() == null || request.column() == null
				|| request.lineNumber() < 1 || request.column() < 1
				|| isInvalidCursorValue(request.selectionStartLineNumber())
				|| isInvalidCursorValue(request.selectionStartColumn())
				|| isInvalidCursorValue(request.selectionEndLineNumber())
				|| isInvalidCursorValue(request.selectionEndColumn())) {
			throw new InvalidCodeSessionException(
					HttpResponseMessage.HTTP_CODE_SESSION_CURSOR_INVALID_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private boolean isInvalidCursorValue(Integer value) {
		return value != null && value < 1;
	}

	private CodeCursorSyncRequestDto normalizeCursor(CodeCursorSyncRequestDto request) {
		Integer selectionStartLineNumber = request.selectionStartLineNumber() == null
				? request.lineNumber()
				: request.selectionStartLineNumber();
		Integer selectionStartColumn = request.selectionStartColumn() == null
				? request.column()
				: request.selectionStartColumn();
		Integer selectionEndLineNumber = request.selectionEndLineNumber() == null
				? request.lineNumber()
				: request.selectionEndLineNumber();
		Integer selectionEndColumn = request.selectionEndColumn() == null
				? request.column()
				: request.selectionEndColumn();
		return new CodeCursorSyncRequestDto(request.lineNumber(), request.column(),
				selectionStartLineNumber, selectionStartColumn, selectionEndLineNumber,
				selectionEndColumn);
	}

	private CodeSessionDto toDto(CodeSession session) {
		String activeEditor = session.getActiveEditor() == null
				? null
				: session.getActiveEditor().getUsername();
		return new CodeSessionDto(session.getId(), session.getCallSession().getId(),
				session.getRoomId(), session.isActive(), session.getLanguage(), session.getCode(),
				session.getStdin(), session.getLastOutput(), session.getLastStatus(),
				session.getLastExitCode(), session.getLastExecutionTimeMs(),
				session.getCreatedBy(), activeEditor, session.getCreatedAt(),
				session.getUpdatedAt());
	}

	private CodeSessionEventDto buildEvent(CodeSessionEventType type, CodeSession session,
			String senderUsername, Object payload) {
		CallParticipant sender = callSessionService.getParticipant(session.getCallSession().getId(),
				senderUsername);
		Long senderId = sender == null ? null : sender.getUser().getId();
		CodeSessionSenderDto senderDto = sender == null
				? null
				: new CodeSessionSenderDto(sender.getUser().getId(),
						sender.getUser().getUsername(), sender.getUser().getDisplayName());
		return new CodeSessionEventDto(type, session.getId(), session.getCallSession().getId(),
				session.getRoomId(), senderId, senderUsername, senderDto, payload, Instant.now());
	}

	private void publishEvent(Long callSessionId, CodeSessionEventDto event) {
		messagingTemplate.convertAndSend("/topic/calls/" + callSessionId + "/code-sessions",
				event);
	}

	private record ParticipantContext(CallSession call, CallParticipant participant) {
	}
}
