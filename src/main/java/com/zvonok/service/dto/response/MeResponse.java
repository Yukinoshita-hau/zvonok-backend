package com.zvonok.service.dto.response;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "MeResponse",
		description = "Ответ эндпоинта /me (информация о текущем пользователе).")
public class MeResponse {

	@Schema(description = "Username текущего пользователя.", example = "murat",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String username;

	@Schema(description = "Произвольное сообщение (например, приветствие или статус).",
			example = "Ты успешно аутефицировался!", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String message;

	@Schema(description = "Время формирования ответа (ISO-8601).", example = "2026-02-01T18:06:00",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime time;
}
