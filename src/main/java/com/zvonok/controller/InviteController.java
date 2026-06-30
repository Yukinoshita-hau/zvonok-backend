package com.zvonok.controller;

import com.zvonok.controller.dto.JoinRoomInviteResponse;
import com.zvonok.controller.dto.RoomInvitePreviewDto;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.RoomInviteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class InviteController {

	private final RoomInviteService roomInviteService;

	@GetMapping("/{token}")
	public ResponseEntity<RoomInvitePreviewDto> preview(@PathVariable String token,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(roomInviteService.preview(token, principal.getUsername()));
	}

	@PostMapping("/{token}/join")
	public ResponseEntity<JoinRoomInviteResponse> join(@PathVariable String token,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(roomInviteService.join(token, principal.getUsername()));
	}
}
