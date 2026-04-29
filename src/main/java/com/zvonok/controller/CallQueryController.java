package com.zvonok.controller;

import com.zvonok.controller.dto.ActiveCallResponse;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CallSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class CallQueryController {

    private final CallSessionService callSessionService;

    @GetMapping("/{roomId}/active-call")
    public ResponseEntity<ActiveCallResponse> getActiveCall(@PathVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return callSessionService.findActiveCallResponse(roomId, principal.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
