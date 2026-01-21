package com.zvonok.controller;

import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.request.CreateServerRequest;
import com.zvonok.service.dto.request.UpdateServerRequest;
import com.zvonok.service.dto.request.UpdateServerMemberNicknameRequest;
import com.zvonok.service.dto.response.ServerResponse;
import io.swagger.v3.oas.annotations.Parameter;
import com.zvonok.service.dto.response.ServerMemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/server")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final UserService userService;

    @PostMapping("/create")
    public ResponseEntity<ServerResponse> createServer(
            @Valid @RequestBody CreateServerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        ServerResponse response = serverService.createServer(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ServerResponse>> getMyServers(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        List<ServerResponse> servers = serverService.getUserServers(userId);
        return ResponseEntity.ok(servers);
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<ServerResponse> getServer(
            @PathVariable @Parameter(description = "Идентификатор сервера", example = "1", required = true) Long serverId,
            @AuthenticationPrincipal UserPrincipal principal) {
//        Long userId = getCurrentUserId(principal);

        // Проверяем доступ к серверу
//        serverService.hasAccessToServer(userId, serverId);

        ServerResponse server = serverService.getServerResponse(serverId);
        return ResponseEntity.ok(server);
    }

    @PostMapping("/join/{inviteCode}")
    public ResponseEntity<ServerResponse> joinServer(
            @PathVariable String inviteCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        ServerResponse response = serverService.joinServerByInviteCode(inviteCode, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{serverId}")
    public ResponseEntity<ServerResponse> updateServer(
            @PathVariable Long serverId,
            @Valid @RequestBody UpdateServerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        ServerResponse response = serverService.updateServer(serverId, request, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{serverId}/regenerate-invite")
    public ResponseEntity<Map<String, String>> regenerateInviteCode(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        String newInviteCode = serverService.regenerateInviteCode(serverId, userId);
        return ResponseEntity.ok(Map.of("inviteCode", newInviteCode));
    }

    @PostMapping("/{serverId}/leave")
    public ResponseEntity<Void> leaveServer(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        serverService.leaveServer(serverId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{serverId}/members")
    public ResponseEntity<List<ServerMemberResponse>> getServerMembers(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        List<ServerMemberResponse> members = serverService.getServerMembers(serverId, userId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{serverId}/members/{targetUserId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable Long serverId,
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        serverService.kickMember(serverId, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{serverId}/members/{targetUserId}/nickname")
    public ResponseEntity<ServerMemberResponse> updateMemberNickname(
            @PathVariable Long serverId,
            @PathVariable Long targetUserId,
            @Valid @RequestBody UpdateServerMemberNicknameRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        ServerMemberResponse response = serverService.updateMemberNickname(serverId, targetUserId, request.getNickname(), userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> deleteServer(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = getCurrentUserId(principal);
        serverService.deleteServer(serverId, userId);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId(UserPrincipal principal) {
        User user = userService.getUser(principal.getUsername());
        return user.getId();
    }
}
