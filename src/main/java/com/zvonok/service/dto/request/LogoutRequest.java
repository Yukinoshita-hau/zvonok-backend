package com.zvonok.service.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.zvonok.utils.StrictBooleanDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "LogoutRequest",
		description = "Запрос на выход из аккаунта (инвалидация refresh token).")
public class LogoutRequest {

	@Schema(description = "Если true — выйти со всех устройств (инвалидировать все refresh токены пользователя). Если false — только текущую сессию.",
			example = "false", defaultValue = "false", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	@JsonDeserialize(using = StrictBooleanDeserializer.class)
	private boolean allDevices = false;

}

