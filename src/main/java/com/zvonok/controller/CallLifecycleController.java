package com.zvonok.controller;

import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CallService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CallLifecycleController {

    private final CallService callService;

    @PostMapping("/end")
    public ResponseEntity<Void> endCall(@RequestBody EndCallDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        callService.callEnd(principal.getName(), dto);
        return ResponseEntity.noContent().build();
    }
}
