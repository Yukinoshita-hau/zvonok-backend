package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(name = "AuthResponse",
		description = "Ответ успешной аутентификации: access/refresh токены и параметры их использования.")
public class AuthResponse {

	@Schema(description = "JWT access token для авторизации запросов.",
			example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMTYiLCJ1c2VySWQiO...",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String accessToken;

	@Schema(description = "Refresh token для обновления access token.",
			example = "f8289b75-07ea-4cab-93bb-8c89f164dc15.zDrAPoYawtdRsAxf4ffkZw", minLength = 59,
			maxLength = 59, accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String refreshToken;

	@Schema(description = "Тип токена для заголовка Authorization.", example = "Bearer",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String tokenType;

	@Schema(description = "Время жизни access token в секундах.", example = "90000",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private long expiresIn;
}
