package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

@Data
@Schema(name = "UpdateChannelDto",
		description = "Данные для обновления канала. Передаются только изменяемые поля.")
public class UpdateChannelDto {
	@Schema(description = "Название канала.", example = "general", maxLength = 100, nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private String name;

	@Schema(description = "Позиция канала в списке (чем меньше — тем выше).", example = "0",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer position;

	@Schema(description = "Лимит участников.", example = "10", nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer userLimit;

	@Schema(description = "Slow mode: задержка между сообщениями в секундах.", example = "5",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer slowModeSeconds;

	@Schema(description = "Тема канала (короткое описание).", example = "Обсуждение релизов",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private String topic;

	@Schema(description = "NSFW-флаг (контент 18+).", example = "false", nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private Boolean nsfw;

	@Schema(description = "Активен ли канал (не скрыт/не архивирован).", example = "true",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Boolean active;
}

