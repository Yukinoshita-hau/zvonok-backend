package com.zvonok.service;

import com.zvonok.controller.dto.CanvasBoardEventDto;
import com.zvonok.controller.dto.CanvasBoardSessionDto;
import com.zvonok.controller.dto.CanvasDrawEventDto;
import com.zvonok.controller.dto.CanvasPointDto;
import com.zvonok.controller.dto.CanvasSnapshotDto;
import com.zvonok.controller.dto.CanvasStrokeDto;
import com.zvonok.controller.dto.CreateCanvasBoardRequest;
import com.zvonok.exception.CanvasBoardAccessDeniedException;
import com.zvonok.exception.CanvasBoardNotFoundException;
import com.zvonok.exception.InvalidCanvasDrawEventException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.CanvasBoard;
import com.zvonok.model.CanvasPoint;
import com.zvonok.model.CanvasStroke;
import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;
import com.zvonok.model.enumeration.CanvasDrawEventType;
import com.zvonok.model.enumeration.CanvasTool;
import com.zvonok.repository.CanvasBoardRepository;
import com.zvonok.repository.CanvasPointRepository;
import com.zvonok.repository.CanvasStrokeRepository;
import com.zvonok.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CanvasBoardService {

	private static final int MIN_WIDTH = 1;
	private static final int MAX_WIDTH = 64;
	private static final int MAX_POINTS_PER_STROKE = 5000;
	private static final int MAX_STROKES_PER_BOARD = 2000;
	private static final Set<CallParticipantStatus> DRAW_ALLOWED_STATUSES =
			Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED);

	private final CallSessionService callSessionService;
	private final CanvasBoardRepository canvasBoardRepository;
	private final CanvasStrokeRepository canvasStrokeRepository;
	private final CanvasPointRepository canvasPointRepository;
	private final SimpMessagingTemplate messagingTemplate;

	@Transactional
	public CanvasBoardSessionDto createBoard(Long callId, CreateCanvasBoardRequest request,
			String username) {
		CallSession call = validateCallParticipantForUpdate(callId, username);
		validateCallCanCreateBoard(call);
		validateCreateRequest(request);

		CanvasBoard existing = canvasBoardRepository
				.findFirstByCallSessionIdAndModeAndActiveTrue(callId, request.mode()).orElse(null);
		if (existing != null) {
			return toSessionDto(existing);
		}

		CanvasBoard board = new CanvasBoard();
		board.setCallSession(call);
		board.setRoomId(call.getRoom().getId());
		board.setMode(request.mode());
		board.setBackground(request.background());
		board.setCreatedBy(username);
		board.setActive(true);
		board = canvasBoardRepository.save(board);

		CanvasBoardSessionDto dto = toSessionDto(board);
		TransactionUtils.runAfterCommit(
				() -> publishBoardEvent(callId, new CanvasBoardEventDto("BOARD_CREATED", dto)));
		return dto;
	}

	@Transactional(readOnly = true)
	public List<CanvasBoardSessionDto> getActiveBoards(Long callId, String username) {
		validateCallParticipant(callId, username);
		return canvasBoardRepository.findAllByCallSessionIdAndActiveTrueOrderByCreatedAtAsc(callId)
				.stream().map(this::toSessionDto).toList();
	}

	@Transactional(readOnly = true)
	public CanvasBoardSessionDto getBoard(Long callId, Long boardId, String username) {
		validateCallParticipant(callId, username);
		return toSessionDto(getBoardForCall(callId, boardId));
	}

	@Transactional(readOnly = true)
	public CanvasSnapshotDto getSnapshot(Long callId, Long boardId, String username) {
		validateCallParticipant(callId, username);
		CanvasBoard board = getBoardForCall(callId, boardId);
		return toSnapshotDto(board);
	}

	@Transactional
	public void closeBoard(Long callId, Long boardId, String username) {
		CallSession call = validateCallParticipant(callId, username);
		validateCallCanMutateBoard(call);
		CanvasBoard board = getBoardForCallForUpdate(callId, boardId);
		ensureCanManageBoard(callId, board, username);

		board.setActive(false);
		CanvasBoardSessionDto dto = toSessionDto(board);
		TransactionUtils.runAfterCommit(
				() -> publishBoardEvent(callId, new CanvasBoardEventDto("BOARD_CLOSED", dto)));
	}

	@Transactional
	public void clearBoard(Long callId, Long boardId, String username) {
		CallSession call = validateCallParticipant(callId, username);
		validateCallCanMutateBoard(call);
		CanvasBoard board = getBoardForCallForUpdate(callId, boardId);
		ensureBoardActive(board);
		ensureCanManageBoard(callId, board, username);

		canvasPointRepository.deleteAllByBoardId(boardId);
		canvasStrokeRepository.deleteAllByBoardId(boardId);

		CanvasDrawEventDto clearEvent = sanitizedEvent(CanvasDrawEventType.BOARD_CLEAR, boardId,
				null, username, null, null, null, null, null);
		CanvasBoardSessionDto boardDto = toSessionDto(board);

		TransactionUtils.runAfterCommit(() -> {
			publishDrawEvent(callId, boardId, clearEvent);
			publishBoardEvent(callId, new CanvasBoardEventDto("BOARD_CLEARED", boardDto));
		});
	}

	@Transactional
	public void handleDrawEvent(Long callId, Long boardId, CanvasDrawEventDto event,
			String username) {
		CallSession call = validateCallParticipant(callId, username);
		validateCallCanMutateBoard(call);
		CanvasBoard board = getBoardForCallForUpdate(callId, boardId);
		ensureBoardActive(board);
		validateEventNotNull(event);

		CanvasDrawEventDto sanitized =
				isTransientEvent(event.type()) ? applyTransientEvent(board, event, username)
						: applyDrawEvent(board, event, username);
		TransactionUtils.runAfterCommit(() -> publishDrawEvent(callId, boardId, sanitized));
	}

	private boolean isTransientEvent(CanvasDrawEventType type) {
		return switch (type) {
			case CURSOR_MOVE, CURSOR_LEAVE, LASER_POINT, LASER_END -> true;
			default -> false;
		};
	}

	private CanvasDrawEventDto applyTransientEvent(CanvasBoard board, CanvasDrawEventDto event,
			String username) {
		return switch (event.type()) {
			case CURSOR_MOVE, LASER_POINT -> {
				validatePoint(event.x(), event.y());
				yield sanitizedEvent(event.type(), board.getId(), null, username, event.x(),
						event.y(), null, null, null);
			}
			case CURSOR_LEAVE, LASER_END -> sanitizedEvent(event.type(), board.getId(), null,
					username, null, null, null, null, null);
			default -> throw new InvalidCanvasDrawEventException(
					"Unsupported transient canvas event");
		};
	}

	private CanvasDrawEventDto applyDrawEvent(CanvasBoard board, CanvasDrawEventDto event,
			String username) {
		return switch (event.type()) {
			case STROKE_START -> applyStrokeStart(board, event, username);
			case STROKE_POINT -> applyStrokePoint(board, event, username);
			case STROKE_END -> applyStrokeEnd(board, event, username);
			case BOARD_CLEAR -> {
				ensureCanManageBoard(board.getCallSession().getId(), board, username);
				canvasPointRepository.deleteAllByBoardId(board.getId());
				canvasStrokeRepository.deleteAllByBoardId(board.getId());
				yield sanitizedEvent(CanvasDrawEventType.BOARD_CLEAR, board.getId(), null, username,
						null, null, null, null, null);
			}
			default -> throw new InvalidCanvasDrawEventException(
					"Unsupported persistent canvas event");
		};
	}

	private CanvasDrawEventDto applyStrokeStart(CanvasBoard board, CanvasDrawEventDto event,
			String username) {
		validateStrokeId(event.strokeId());
		validatePoint(event.x(), event.y());
		validateStrokeStyle(event);

		if (canvasStrokeRepository.existsByBoardIdAndStrokeKey(board.getId(), event.strokeId())) {
			throw new InvalidCanvasDrawEventException("Canvas stroke already exists");
		}
		if (canvasStrokeRepository.countByBoardId(board.getId()) >= MAX_STROKES_PER_BOARD) {
			throw new InvalidCanvasDrawEventException("Canvas board stroke limit exceeded");
		}

		CanvasStroke stroke = new CanvasStroke();
		stroke.setBoard(board);
		stroke.setStrokeKey(event.strokeId());
		stroke.setUserId(username);
		stroke.setColor(event.color());
		stroke.setWidth(event.width());
		stroke.setTool(event.tool());
		stroke = canvasStrokeRepository.save(stroke);

		CanvasPoint point = createPoint(stroke, event.x(), event.y(), 0);
		canvasPointRepository.save(point);

		return sanitizedEvent(CanvasDrawEventType.STROKE_START, board.getId(), event.strokeId(),
				username, event.x(), event.y(), event.color(), event.width(), event.tool());
	}

	private CanvasDrawEventDto applyStrokePoint(CanvasBoard board, CanvasDrawEventDto event,
			String username) {
		validateStrokeId(event.strokeId());
		validatePoint(event.x(), event.y());

		CanvasStroke stroke = getStroke(board.getId(), event.strokeId());
		if (stroke.getEndedAt() != null) {
			throw new InvalidCanvasDrawEventException("Cannot add points to ended canvas stroke");
		}
		long pointCount = canvasPointRepository.countByStrokeId(stroke.getId());
		if (pointCount >= MAX_POINTS_PER_STROKE) {
			throw new InvalidCanvasDrawEventException("Canvas stroke point limit exceeded");
		}

		CanvasPoint point = createPoint(stroke, event.x(), event.y(), (int) pointCount);
		canvasPointRepository.save(point);

		return sanitizedEvent(CanvasDrawEventType.STROKE_POINT, board.getId(), event.strokeId(),
				username, event.x(), event.y(), null, null, null);
	}

	private CanvasDrawEventDto applyStrokeEnd(CanvasBoard board, CanvasDrawEventDto event,
			String username) {
		validateStrokeId(event.strokeId());
		CanvasStroke stroke = getStroke(board.getId(), event.strokeId());
		if (stroke.getEndedAt() == null) {
			stroke.setEndedAt(Instant.now());
		}
		return sanitizedEvent(CanvasDrawEventType.STROKE_END, board.getId(), event.strokeId(),
				username, null, null, null, null, null);
	}

	private CanvasPoint createPoint(CanvasStroke stroke, Double x, Double y, int position) {
		CanvasPoint point = new CanvasPoint();
		point.setStroke(stroke);
		point.setX(x);
		point.setY(y);
		point.setPosition(position);
		return point;
	}

	private CanvasStroke getStroke(Long boardId, String strokeId) {
		return canvasStrokeRepository.findByBoardIdAndStrokeKey(boardId, strokeId)
				.orElseThrow(() -> new InvalidCanvasDrawEventException("Canvas stroke not found"));
	}

	private CanvasDrawEventDto sanitizedEvent(CanvasDrawEventType type, Long boardId,
			String strokeId, String username, Double x, Double y, String color, Integer width,
			CanvasTool tool) {
		return new CanvasDrawEventDto(type, boardId, strokeId, username, x, y, color, width, tool,
				Instant.now());
	}

	private CallSession validateCallParticipant(Long callId, String username) {
		CallSession call = callSessionService.getCallSession(callId);
		validateCallParticipantStatus(callId, username);
		return call;
	}

	private CallSession validateCallParticipantForUpdate(Long callId, String username) {
		CallSession call = callSessionService.getCallSessionForUpdate(callId);
		validateCallParticipantStatus(callId, username);
		return call;
	}

	private void validateCallParticipantStatus(Long callId, String username) {
		CallParticipant participant = callSessionService.getParticipant(callId, username);
		if (participant == null || !DRAW_ALLOWED_STATUSES.contains(participant.getStatus())) {
			throw new InsufficientPermissionsException("User is not active call participant");
		}
	}

	private void validateCallCanCreateBoard(CallSession call) {
		if (call.getStatus() == CallSessionStatus.ENDED) {
			throw new InvalidCanvasDrawEventException("Cannot create canvas board for ended call");
		}
	}

	private void validateCallCanMutateBoard(CallSession call) {
		if (call.getStatus() == CallSessionStatus.ENDED) {
			throw new InvalidCanvasDrawEventException("Cannot mutate canvas board for ended call");
		}
	}

	private CanvasBoard getBoardForCall(Long callId, Long boardId) {
		if (boardId == null) {
			throw new CanvasBoardNotFoundException("Canvas board not found");
		}
		return canvasBoardRepository.findByIdAndCallSessionId(boardId, callId)
				.orElseThrow(() -> new CanvasBoardNotFoundException("Canvas board not found"));
	}

	private CanvasBoard getBoardForCallForUpdate(Long callId, Long boardId) {
		if (boardId == null) {
			throw new CanvasBoardNotFoundException("Canvas board not found");
		}
		return canvasBoardRepository.findByIdAndCallSessionIdForUpdate(boardId, callId)
				.orElseThrow(() -> new CanvasBoardNotFoundException("Canvas board not found"));
	}

	private void validateCreateRequest(CreateCanvasBoardRequest request) {
		if (request == null || request.mode() == null || request.background() == null) {
			throw new InvalidCanvasDrawEventException(
					"Canvas board mode and background are required");
		}

		if (request.mode() == CanvasBoardMode.SCREEN_OVERLAY
				&& request.background() != CanvasBackground.TRANSPARENT) {
			throw new InvalidCanvasDrawEventException(
					"SCREEN_OVERLAY board must use TRANSPARENT background");
		}

		if (request.mode() == CanvasBoardMode.WHITEBOARD
				&& request.background() == CanvasBackground.TRANSPARENT) {
			throw new InvalidCanvasDrawEventException(
					"WHITEBOARD board must use WHITE or BLACK background");
		}
	}

	private void validateEventNotNull(CanvasDrawEventDto event) {
		if (event == null || event.type() == null) {
			throw new InvalidCanvasDrawEventException("Canvas draw event type is required");
		}
	}

	private void validateStrokeId(String strokeId) {
		if (strokeId == null || strokeId.isBlank()) {
			throw new InvalidCanvasDrawEventException("Canvas strokeId is required");
		}
	}

	private void validatePoint(Double x, Double y) {
		if (x == null || y == null) {
			throw new InvalidCanvasDrawEventException("Canvas point coordinates are required");
		}
		if (x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
			throw new InvalidCanvasDrawEventException(
					"Canvas coordinates must be normalized between 0.0 and 1.0");
		}
	}

	private void validateStrokeStyle(CanvasDrawEventDto event) {
		if (event.color() == null || event.color().isBlank()) {
			throw new InvalidCanvasDrawEventException("Canvas stroke color is required");
		}
		if (event.width() == null || event.width() < MIN_WIDTH || event.width() > MAX_WIDTH) {
			throw new InvalidCanvasDrawEventException(
					"Canvas stroke width must be between 1 and 64");
		}
		if (event.tool() == null) {
			throw new InvalidCanvasDrawEventException("Canvas tool is required");
		}
	}

	private void ensureBoardActive(CanvasBoard board) {
		if (!board.isActive()) {
			throw new CanvasBoardAccessDeniedException("Canvas board is closed");
		}
	}

	private void ensureCanManageBoard(Long callId, CanvasBoard board, String username) {
		// TODO: add advanced canvas permissions: canDraw, canErase, canClear, canCloseBoard.
		if (board.getCreatedBy().equals(username)) {
			return;
		}

		CallParticipant participant = callSessionService.getParticipant(callId, username);
		if (participant != null && participant.getRole() == CallParticipantRole.HOST) {
			return;
		}

		throw new CanvasBoardAccessDeniedException(
				"Only board creator or call host can manage board");
	}

	private CanvasBoardSessionDto toSessionDto(CanvasBoard board) {
		return new CanvasBoardSessionDto(board.getId(), board.getCallSession().getId(),
				board.getRoomId(), board.getMode(), board.getBackground(), board.getCreatedBy(),
				board.getCreatedAt(), board.isActive());
	}

	private CanvasSnapshotDto toSnapshotDto(CanvasBoard board) {
		List<CanvasStrokeDto> strokes =
				canvasStrokeRepository.findAllByBoardIdOrderByCreatedAtAsc(board.getId()).stream()
						.sorted(Comparator.comparing(CanvasStroke::getCreatedAt))
						.map(this::toStrokeDto).toList();
		return new CanvasSnapshotDto(board.getId(), strokes);
	}

	private CanvasStrokeDto toStrokeDto(CanvasStroke stroke) {
		List<CanvasPointDto> points = stroke.getPoints().stream()
				.sorted(Comparator.comparingInt(CanvasPoint::getPosition))
				.map(point -> new CanvasPointDto(point.getX(), point.getY())).toList();
		return new CanvasStrokeDto(stroke.getStrokeKey(), stroke.getUserId(), stroke.getColor(),
				stroke.getWidth(), stroke.getTool(), points);
	}

	private void publishDrawEvent(Long callId, Long boardId, CanvasDrawEventDto event) {
		messagingTemplate.convertAndSend("/topic/calls/" + callId + "/boards/" + boardId, event);
	}

	private void publishBoardEvent(Long callId, CanvasBoardEventDto event) {
		messagingTemplate.convertAndSend("/topic/calls/" + callId + "/boards", event);
	}
}
