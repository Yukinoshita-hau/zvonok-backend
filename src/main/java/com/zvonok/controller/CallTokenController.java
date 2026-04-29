package com.zvonok.controller;

import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.LiveKitCallTokenService;
import com.zvonok.service.dto.LiveKitTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallTokenController {

    private final LiveKitCallTokenService liveKitCallTokenService;

    @PostMapping("/{callId}/token")
    public LiveKitTokenResponse issueCallToken(@PathVariable Long callId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return liveKitCallTokenService.issueCallToken(callId, principal.getName());
    }
}
