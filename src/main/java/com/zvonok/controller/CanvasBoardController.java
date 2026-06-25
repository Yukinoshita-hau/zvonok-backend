package com.zvonok.controller;

import com.zvonok.controller.dto.CanvasBoardSessionDto;
import com.zvonok.controller.dto.CanvasNoteVoteDto;
import com.zvonok.controller.dto.CanvasSnapshotDto;
import com.zvonok.controller.dto.CanvasStickyNoteDto;
import com.zvonok.controller.dto.CreateCanvasStickyNoteRequest;
import com.zvonok.controller.dto.CreateCanvasBoardRequest;
import com.zvonok.controller.dto.StartCanvasTimerRequest;
import com.zvonok.controller.dto.UpdateCanvasBoardPermissionsRequest;
import com.zvonok.controller.dto.UpdateCanvasBoardTemplateRequest;
import com.zvonok.controller.dto.UpdateCanvasPresenterRequest;
import com.zvonok.controller.dto.UpdateCanvasStickyNoteRequest;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CanvasBoardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/calls/{callId}/boards")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CanvasBoardController {

	private final CanvasBoardService canvasBoardService;

	@PostMapping
	public ResponseEntity<CanvasBoardSessionDto> createBoard(@PathVariable Long callId,
			@RequestBody CreateCanvasBoardRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(canvasBoardService.createBoard(callId, request, principal.getName()));
	}

	@GetMapping
	public List<CanvasBoardSessionDto> getActiveBoards(@PathVariable Long callId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.getActiveBoards(callId, principal.getName());
	}

	@GetMapping("/{boardId}")
	public CanvasBoardSessionDto getBoard(@PathVariable Long callId, @PathVariable Long boardId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.getBoard(callId, boardId, principal.getName());
	}

	@GetMapping("/{boardId}/snapshot")
	public CanvasSnapshotDto getSnapshot(@PathVariable Long callId, @PathVariable Long boardId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.getSnapshot(callId, boardId, principal.getName());
	}

	@DeleteMapping("/{boardId}")
	public ResponseEntity<Void> closeBoard(@PathVariable Long callId, @PathVariable Long boardId,
			@AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.closeBoard(callId, boardId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{boardId}/clear")
	public ResponseEntity<Void> clearBoard(@PathVariable Long callId, @PathVariable Long boardId,
			@AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.clearBoard(callId, boardId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{boardId}/undo")
	public ResponseEntity<Void> undoLastStroke(@PathVariable Long callId,
			@PathVariable Long boardId, @AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.undoLastStroke(callId, boardId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{boardId}/permissions")
	public CanvasBoardSessionDto updatePermissions(@PathVariable Long callId,
			@PathVariable Long boardId,
			@RequestBody UpdateCanvasBoardPermissionsRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.updateBoardPermissions(callId, boardId, request,
				principal.getName());
	}

	@PatchMapping("/{boardId}/template")
	public CanvasBoardSessionDto updateTemplate(@PathVariable Long callId,
			@PathVariable Long boardId,
			@RequestBody UpdateCanvasBoardTemplateRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.updateBoardTemplate(callId, boardId, request,
				principal.getName());
	}

	@PostMapping("/{boardId}/timer/start")
	public CanvasBoardSessionDto startTimer(@PathVariable Long callId,
			@PathVariable Long boardId, @RequestBody StartCanvasTimerRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.startTimer(callId, boardId, request, principal.getName());
	}

	@PostMapping("/{boardId}/timer/stop")
	public CanvasBoardSessionDto stopTimer(@PathVariable Long callId,
			@PathVariable Long boardId, @AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.stopTimer(callId, boardId, principal.getName());
	}

	@PostMapping("/{boardId}/timer/reset")
	public CanvasBoardSessionDto resetTimer(@PathVariable Long callId,
			@PathVariable Long boardId, @AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.resetTimer(callId, boardId, principal.getName());
	}

	@GetMapping("/{boardId}/notes")
	public List<CanvasStickyNoteDto> getNotes(@PathVariable Long callId,
			@PathVariable Long boardId, @AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.getNotes(callId, boardId, principal.getName());
	}

	@PostMapping("/{boardId}/notes")
	public ResponseEntity<CanvasStickyNoteDto> createNote(@PathVariable Long callId,
			@PathVariable Long boardId, @RequestBody CreateCanvasStickyNoteRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(canvasBoardService.createNote(callId, boardId, request,
						principal.getName()));
	}

	@PatchMapping("/{boardId}/notes/{noteId}")
	public CanvasStickyNoteDto updateNote(@PathVariable Long callId,
			@PathVariable Long boardId, @PathVariable Long noteId,
			@RequestBody UpdateCanvasStickyNoteRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.updateNote(callId, boardId, noteId, request,
				principal.getName());
	}

	@DeleteMapping("/{boardId}/notes/{noteId}")
	public ResponseEntity<Void> deleteNote(@PathVariable Long callId,
			@PathVariable Long boardId, @PathVariable Long noteId,
			@AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.deleteNote(callId, boardId, noteId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{boardId}/notes/{noteId}/vote")
	public ResponseEntity<Void> voteNote(@PathVariable Long callId,
			@PathVariable Long boardId, @PathVariable Long noteId,
			@AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.voteNote(callId, boardId, noteId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{boardId}/notes/{noteId}/vote")
	public ResponseEntity<Void> unvoteNote(@PathVariable Long callId,
			@PathVariable Long boardId, @PathVariable Long noteId,
			@AuthenticationPrincipal UserPrincipal principal) {
		canvasBoardService.unvoteNote(callId, boardId, noteId, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{boardId}/votes")
	public List<CanvasNoteVoteDto> getVotes(@PathVariable Long callId,
			@PathVariable Long boardId, @AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.getVotes(callId, boardId, principal.getName());
	}

	@PostMapping("/{boardId}/background-image")
	public CanvasBoardSessionDto uploadBackgroundImage(@PathVariable Long callId,
			@PathVariable Long boardId, @RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.uploadBackgroundImage(callId, boardId, file,
				principal.getName());
	}

	@PatchMapping("/{boardId}/presenter")
	public CanvasBoardSessionDto updatePresenter(@PathVariable Long callId,
			@PathVariable Long boardId, @RequestBody UpdateCanvasPresenterRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return canvasBoardService.updatePresenter(callId, boardId, request,
				principal.getName());
	}
}
