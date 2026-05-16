package com.zvonok.controller;

import com.zvonok.controller.dto.RestoreCallSessionResponse;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CallRestoreService;
import com.zvonok.service.LiveKitCallTokenService;
import com.zvonok.service.dto.LiveKitTokenResponse;
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
@RequestMapping("/calls")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CallTokenController {

    private final LiveKitCallTokenService liveKitCallTokenService;
	private final CallRestoreService restoreService;

    @PostMapping("/{callId}/token")
    public LiveKitTokenResponse issueCallToken(@PathVariable Long callId,
            @AuthenticationPrincipal UserPrincipal principal) {

        return liveKitCallTokenService.issueCallToken(callId, principal.getName());
    }

	@GetMapping("/restore")
	public ResponseEntity<RestoreCallSessionResponse> restoreCallSession(@AuthenticationPrincipal UserPrincipal principal) {
		String username = principal.getName();

		return ResponseEntity.ok(
				restoreService.restoreCallSession(username)
					.orElseGet(RestoreCallSessionResponse::empty)
		);
	}
}
