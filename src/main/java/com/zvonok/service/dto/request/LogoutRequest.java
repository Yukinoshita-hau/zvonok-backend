package com.zvonok.service.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.zvonok.utils.StrictBooleanDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "LogoutRequest",
		description = "Запрос на выход из аккаунта (инвалидация refresh token).")
public class LogoutRequest {

	@Schema(description = "Refresh token. Обязателен, фиксированной длины 59 символов.",
			example = "f8289b75-07ea-4cab-93bb-8c89f164dc15.zDrAPoYawtdRsAxf4ffkZw", minLength = 59,
			maxLength = 59, nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "RefreshToken is required")
	@Size(min = 59, max = 59, message = "RefreshToken must be 59 characters")
	private String refreshToken;

	@Schema(description = "Если true — выйти со всех устройств (инвалидировать все refresh токены пользователя). Если false — только текущую сессию.",
			example = "false", defaultValue = "false", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	private boolean allDevices = false;

	@Schema(hidden = true)
	public boolean hasRefreshToken() {
		return refreshToken != null && !refreshToken.isBlank();
	}
}

