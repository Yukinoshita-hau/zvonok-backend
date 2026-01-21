package com.zvonok.controller;

import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.AuthService;
import com.zvonok.service.dto.AuthResponse;
import com.zvonok.service.dto.request.LoginRequest;
import com.zvonok.service.dto.request.LogoutRequest;
import com.zvonok.service.dto.request.RegisterRequest;
import com.zvonok.service.dto.request.TokenRefreshRequest;
import com.zvonok.service.dto.response.LogoutResponse;
import com.zvonok.service.dto.response.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.zvonok.documentation.ApiResponseDescriptions;
import com.zvonok.exception.InvalidRefreshTokenException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Авторизационный контроллер",
		description = "Контролер отвечающий за авторизацию пользователя и смежным функционалом")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;

	@Operation(summary = "Регистрирует пользователя",
			description = "Создаёт пользователя в бд и далее автоматически авторизовывает его возвращаю стандартный авторизационный ответ")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ApiResponseDescriptions.AUTH_REGISTER_SUCCESS),
			@ApiResponse(responseCode = "400",
					description = ApiResponseDescriptions.VALIDATION_FAILED,
					content = {@Content(mediaType = "application/json",
							schema = @Schema(implementation = AuthResponse.class))}),
			@ApiResponse(responseCode = "409",
					description = ApiResponseDescriptions.AUTH_USER_ALREADY_EXISTS)})
	@PostMapping("/register")
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request.getUsername(), request.getEmail(),
				request.getPassword());
	}

	@Operation(summary = "Позволяет пользователю войти в систему")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Успешный вход"),
			@ApiResponse(responseCode = "400",
					description = ApiResponseDescriptions.VALIDATION_FAILED),
			@ApiResponse(responseCode = "401",
					description = ApiResponseDescriptions.AUTH_INVALID_CREDENTIALS)})
	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.getUsernameOrEmail(), request.getPassword());
	}

	@Operation(summary = "Переавторизовывает пользователя по refresh токену",
			description = "Переавторизовывает пользователя по refresh токену выдовая новые jwt и refresh токены и помечая использованный refresh токен как revoked (отменённый)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ApiResponseDescriptions.AUTH_REFRESH_SUCCESS),
			@ApiResponse(responseCode = "400",
					description = ApiResponseDescriptions.VALIDATION_FAILED),
			@ApiResponse(responseCode = "401",
					description = ApiResponseDescriptions.AUTH_REFRESH_TOKEN_REVOKED_OR_EXPIRED)})
	@PostMapping("/refresh")
	public AuthResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
		return authService.refresh(request.getRefreshToken());
	}

	@Operation(summary = "Деавторизовывает пользователя по refresh токену",
			description = "Произоводит Деавторизацию пользователю по refresh токену методом отзывания refresh токена(ов)")
	@SecurityRequirement(name = "JWT")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ApiResponseDescriptions.AUTH_LOGOUT_SUCCESS),
			@ApiResponse(responseCode = "400",
					description = ApiResponseDescriptions.VALIDATION_FAILED),
			@ApiResponse(responseCode = "401",
					description = ApiResponseDescriptions.AUTH_REFRESH_TOKEN_REVOKED_OR_EXPIRED),
			@ApiResponse(responseCode = "403",
					description = ApiResponseDescriptions.AUTH_ACCESS_TOKEN_NOT_VALID),})
	@PostMapping("/logout")
	public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {

		if (!request.hasRefreshToken()) {
			throw new InvalidRefreshTokenException(
					HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage());
		}

		if (request.isAllDevices()) {
			authService.logoutFromAllDevices(request.getRefreshToken());
		} else {
			authService.logout(request.getRefreshToken());
		}
		LogoutResponse response = new LogoutResponse("Logout successful", request.isAllDevices());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Проверка идентификации по токену",
			description = "Проверка jwt токена на валидность и возврат информации по токену")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ApiResponseDescriptions.AUTH_ME_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = ApiResponseDescriptions.AUTH_ACCESS_TOKEN_NOT_VALID),})
	@SecurityRequirement(name = "JWT")
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
}
