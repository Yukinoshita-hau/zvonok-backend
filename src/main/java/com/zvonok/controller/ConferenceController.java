package com.zvonok.controller;

import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ConferenceService;
import com.zvonok.service.dto.ConferenceCreateResponse;
import com.zvonok.service.dto.ConferenceJoinResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conferences")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class ConferenceController {

	private final ConferenceService conferenceService;

	@PostMapping
	public ConferenceCreateResponse createConference(@AuthenticationPrincipal UserPrincipal principal) {
		return conferenceService.createConference(principal.getName());
	}

	@PostMapping("/{code}/join")
	public ConferenceJoinResponse joinConference(@PathVariable String code,
			@AuthenticationPrincipal UserPrincipal principal) {
		return conferenceService.joinConference(code, principal.getName());
	}

	@PostMapping("/{code}/end")
	public ResponseEntity<Void> endConference(@PathVariable String code,
			@AuthenticationPrincipal UserPrincipal principal) {
		conferenceService.endConference(code, principal.getName());
		return ResponseEntity.noContent().build();
	}
}
