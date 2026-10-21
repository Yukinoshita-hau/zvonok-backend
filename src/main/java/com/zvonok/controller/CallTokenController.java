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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CallTokenController {

    private final LiveKitCallTokenService liveKitCallTokenService;
	private final CallRestoreService restoreService;

    @PostMapping("/{callId}/token")
    public LiveKitTokenResponse issueCallToken(@PathVariable Long callId,
            @RequestHeader("X-Media-Client-Id") UUID clientId,
            @RequestParam(defaultValue = "false") boolean transfer,
            @AuthenticationPrincipal UserPrincipal principal) {

        return liveKitCallTokenService.issueCallToken(callId, principal.getName(), clientId.toString(), transfer);
    }

    @PostMapping("/{callId}/media/renew")
    public String renewMedia(@PathVariable Long callId, @RequestHeader("X-Media-Client-Id") UUID clientId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return liveKitCallTokenService.renewMedia(callId, principal.getName(), clientId.toString());
    }

    @PostMapping("/{callId}/media/release")
    public ResponseEntity<Void> releaseMedia(@PathVariable Long callId, @RequestHeader("X-Media-Client-Id") UUID clientId,
            @AuthenticationPrincipal UserPrincipal principal) {
        liveKitCallTokenService.releaseMedia(callId, principal.getName(), clientId.toString());
        return ResponseEntity.noContent().build();
    }

	@GetMapping("/restore")
	public ResponseEntity<RestoreCallSessionResponse> restoreCallSession(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Media-Client-Id") UUID clientId) {
		String username = principal.getName();

		return ResponseEntity.ok(
				restoreService.restoreCallSession(username, clientId.toString())
					.orElseGet(RestoreCallSessionResponse::empty)
		);
	}
}
