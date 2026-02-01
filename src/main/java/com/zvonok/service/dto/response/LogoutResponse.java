package com.zvonok.service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Value
@Schema(name = "LogoutResponse", description = "Ответ после выхода из аккаунта.")
public class LogoutResponse {

	@Schema(description = "Сообщение о результате операции выхода.",
			example = "Logout successful", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	String message;

	@Schema(description = "true — выход выполнен со всех устройств, false — только с текущего.",
			example = "false", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	boolean allDevices;
}

