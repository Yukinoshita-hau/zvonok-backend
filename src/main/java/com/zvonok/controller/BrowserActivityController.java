package com.zvonok.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.BrowserActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/calls/{callId}/browser-activity")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "browser.activity.enabled", havingValue = "true")
public class BrowserActivityController {
    private final BrowserActivityService activities;
    public record Command(@NotBlank @Size(max = 32) String op, JsonNode body,
                          @Size(max = 36) String epoch, @Size(max = 20) String generation) {}

    @GetMapping
    public ResponseEntity<BrowserActivityService.View> find(@PathVariable Long callId, @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activities.find(callId, user.getName()));
    }

    @PostMapping
    public ResponseEntity<BrowserActivityService.View> create(@PathVariable Long callId, @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activities.create(callId, user.getName()));
    }

    @PostMapping("/{activityId}/commands")
    public ResponseEntity<JsonNode> command(@PathVariable Long callId, @PathVariable UUID activityId,
            @Valid @RequestBody Command command, @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activities.command(callId, activityId,
                user.getName(), command.op(), command.body(), command.epoch(), command.generation()));
    }
}
