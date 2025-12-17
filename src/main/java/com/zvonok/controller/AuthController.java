package com.zvonok.controller;

import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.AuthService;
import com.zvonok.service.dto.AuthResponse;
import com.zvonok.service.dto.request.LoginRequest;
import com.zvonok.service.dto.request.LogoutRequest;
import com.zvonok.service.dto.request.RegisterRequest;
import com.zvonok.service.dto.request.TokenRefreshRequest;
import com.zvonok.service.dto.response.LogoutResponse;
import com.zvonok.service.dto.response.MeResponse;
import com.zvonok.exception.InvalidJwtException;
import com.zvonok.exception.InvalidRefreshTokenException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(
                request.getUsernameOrEmail(),
                request.getPassword()
        );
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new InvalidJwtException(HttpResponseMessage.HTTP_INVALID_JWT_RESPONSE_MESSAGE.getMessage());
        }

        if (!request.hasRefreshToken()) {
                throw new InvalidRefreshTokenException(HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage());
            }

        if (request.isAllDevices()) {
            authService.logoutFromAllDevices(request.getRefreshToken());
        } else {
            authService.logout(request.getRefreshToken());
        }
        LogoutResponse response = new LogoutResponse("Logout successful", request.isAllDevices());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        String username = principal.getName();

        MeResponse response = new MeResponse();
        response.setUsername(username);
        response.setMessage("Ты успешно аутефицировался!");
        response.setTime(LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}
