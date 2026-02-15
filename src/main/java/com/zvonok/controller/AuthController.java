package com.zvonok.controller;

import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.AuthService;
import com.zvonok.service.dto.AuthResponse;
import com.zvonok.service.dto.request.LoginRequest;
import com.zvonok.service.dto.request.LogoutRequest;
import com.zvonok.service.dto.request.RegisterRequest;
import com.zvonok.service.dto.response.LogoutResponse;
import com.zvonok.service.dto.response.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.zvonok.config.CookieProperties;
import com.zvonok.controller.dto.LoginResponseDto;
import com.zvonok.documentation.AuthApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse409;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Авторизационный контроллер", description = "Контролер отвечающий за авторизацию пользователя и смежным функционалом")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;
	private final CookieProperties cookieProperties;

	@Operation(summary = "Регистрирует пользователя", description = "Создаёт пользователя в бд и далее автоматически авторизовывает его возвращаю стандартный авторизационный ответ")
	@ApiResponse(responseCode = "201", description = AuthApiDescriptions.AUTH_REGISTER_SUCCESS)
	@ApiResponse400
	@ApiResponse409(description = AuthApiDescriptions.AUTH_USER_ALREADY_EXISTS)
	@PostMapping("/register")
	public ResponseEntity<LoginResponseDto> register(@Valid @RequestBody RegisterRequest request) {
		AuthResponse authData = authService.register(request.getUsername(), request.getEmail(),
				request.getPassword());
		return ResponseEntity.status(201)
				.header("Set-Cookie", buildRefreshCookie(authData.getRefreshToken()).toString()).body(
						buildLoginResponseDto(authData));
	}

	@Operation(summary = "Позволяет пользователю войти в систему")
	@ApiResponse(responseCode = "200", description = "Успешный вход")
	@ApiResponse400
	@PostMapping("/login")
	public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequest request) {
		AuthResponse authData = authService.login(request.getUsernameOrEmail(), request.getPassword());
		return ResponseEntity.status(200)
				.header("Set-Cookie", buildRefreshCookie(authData.getRefreshToken()).toString())
				.body(buildLoginResponseDto(authData));
	}

	@Operation(summary = "Переавторизовывает пользователя по refresh токену", description = "Переавторизовывает пользователя по refresh токену выдовая новые jwt и refresh токены и помечая использованный refresh токен как revoked (отменённый)")
	@ApiResponse(responseCode = "200", description = AuthApiDescriptions.AUTH_REFRESH_SUCCESS)
	@ApiResponse400
	@PostMapping("/refresh")
	public ResponseEntity<LoginResponseDto> refresh(@CookieValue(value = "refreshToken") String refreshCookie) {
		AuthResponse authData = authService.refresh(refreshCookie);
		return ResponseEntity.status(200)
				.header("Set-Cookie", buildRefreshCookie(authData.getRefreshToken()).toString())
				.body(buildLoginResponseDto(authData));
	}

	@Operation(summary = "Деавторизовывает пользователя по refresh токену", description = "Произоводит деавторизацию пользователю по refresh токену методом отзывания refresh токена(ов)")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = AuthApiDescriptions.AUTH_LOGOUT_SUCCESS)
	@ApiResponse400
	@PostMapping("/logout")
	public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request,
			@CookieValue(value = "refreshToken") String refreshCookie,
			@AuthenticationPrincipal UserPrincipal principal) {

		if (request.isAllDevices()) {
			authService.logoutFromAllDevices(refreshCookie);
		} else {
			authService.logout(refreshCookie);
		}
		LogoutResponse response = new LogoutResponse("Logout successful", request.isAllDevices());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Проверка идентификации по токену", description = "Проверка jwt токена на валидность и возврат информации по токену")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = AuthApiDescriptions.AUTH_ME_SUCCESS)
	@GetMapping("/me")
	public ResponseEntity<MeResponse> getCurrentUser(
			@AuthenticationPrincipal UserPrincipal principal) {

		String username = principal.getName();

		MeResponse response = new MeResponse();
		response.setUsername(username);
		response.setMessage("Ты успешно аутефицировался!");
		response.setTime(LocalDateTime.now());

		return ResponseEntity.ok(response);
	}

	private LoginResponseDto buildLoginResponseDto(AuthResponse authData) {
		return new LoginResponseDto(authData.getAccessToken(), authData.getTokenType(), authData.getExpiresIn());
	}

	private ResponseCookie buildRefreshCookie(String refreshToken) {
		return ResponseCookie.from("refreshToken", refreshToken).httpOnly(true)
				.secure(cookieProperties.isSecure())
				.path(cookieProperties.getPath()).sameSite(cookieProperties.getSameSite())
				.maxAge(cookieProperties.getMaxAge()).build();
	}
}
