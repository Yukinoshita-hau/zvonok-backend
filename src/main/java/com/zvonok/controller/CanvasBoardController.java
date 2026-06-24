package com.zvonok.controller;

import com.zvonok.controller.dto.CanvasBoardSessionDto;
import com.zvonok.controller.dto.CanvasSnapshotDto;
import com.zvonok.controller.dto.CreateCanvasBoardRequest;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CanvasBoardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
