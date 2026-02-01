package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "UpdateChannelFolderDto",
		description = "Данные для обновления папки каналов. Все поля необязательны.")
public class UpdateChannelFolderDto {
	@Schema(description = "Название папки.", example = "Общее", nullable = true,
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String name;

	@Schema(description = "Позиция папки в списке (чем меньше — тем выше).", example = "0",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Integer position;

	@Schema(description = "Свернута ли папка у пользователя по умолчанию.", example = "false",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Boolean collapsed;

	@Schema(description = "Активна ли папка (не скрыта/не архивирована).", example = "true",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Boolean active;
}

