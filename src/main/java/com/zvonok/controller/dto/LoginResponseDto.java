package com.zvonok.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(name = "LoginResponseDto",
		description = "Ответ успешной аутентификации: access токен и параметры его использования.")
public class LoginResponseDto {

	@Schema(description = "JWT access token для авторизации запросов.",
			example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMTYiLCJ1c2VySWQiO...",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String accessToken;

	@Schema(description = "Тип токена для заголовка Authorization.", example = "Bearer",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String tokenType;

	@Schema(description = "Время жизни access token в секундах.", example = "90000",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private long expiresIn;
}
