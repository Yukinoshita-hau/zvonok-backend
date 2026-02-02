package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(name = "CreateServerBanRequest", description = "Запрос на бан пользователя на сервере.")
public class CreateServerBanRequest {

	@Schema(description = "ID пользователя, которого нужно забанить.", example = "123",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long targetUserId;

	@Schema(description = "Причина бана (до 255 символов).", example = "Спам в чатах",
			maxLength = 255)
	@NotBlank
	@Size(max = 255, message = "reason length must be not more than 255 characters")
	private String reason;

	@Schema(description = "Дата и время окончания бана. Если не указано — бан бессрочный.",
			example = "2026-02-10T12:00:00", nullable = true)
	@Future(message = "The end date of the ban must be in the future")
	private LocalDateTime expiresAt;
}

