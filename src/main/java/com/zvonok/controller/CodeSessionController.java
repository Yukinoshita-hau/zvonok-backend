package com.zvonok.controller;

import com.zvonok.controller.dto.CodeAccessRequestDto;
import com.zvonok.controller.dto.CodeSessionDto;
import com.zvonok.controller.dto.CreateCodeSessionRequestDto;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CodeSessionService;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/code-sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CodeSessionController {

	private final CodeSessionService codeSessionService;

	@PostMapping
	public ResponseEntity<CodeSessionDto> createSession(
			@Valid @RequestBody CreateCodeSessionRequestDto request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(codeSessionService.createSession(request, principal.getName()));
	}

	@GetMapping("/active")
	public CodeSessionDto getActiveSession(@RequestParam Long callSessionId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return codeSessionService.getActiveSession(callSessionId, principal.getName());
	}

	@PostMapping("/{sessionId}/grant-editor")
	public CodeSessionDto grantEditor(@PathVariable Long sessionId,
			@Valid @RequestBody CodeAccessRequestDto request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return codeSessionService.grantEditor(sessionId, request, principal.getName());
	}

	@PostMapping("/{sessionId}/revoke-editor")
	public CodeSessionDto revokeEditor(@PathVariable Long sessionId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return codeSessionService.revokeEditor(sessionId, principal.getName());
	}

	@PostMapping("/{sessionId}/run")
	public CodeRunResponseDto runSession(@PathVariable Long sessionId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return codeSessionService.runSession(sessionId, principal.getName());
	}

	@PostMapping("/{sessionId}/close")
	public CodeSessionDto closeSession(@PathVariable Long sessionId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return codeSessionService.closeSession(sessionId, principal.getName());
	}
}
