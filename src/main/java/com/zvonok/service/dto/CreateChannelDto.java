package com.zvonok.service.dto;

import com.zvonok.model.enumeration.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "CreateChannelDto", description = "Запрос на создание канала в папке каналов.")
public class CreateChannelDto {

	@Schema(description = "Название канала.", example = "general", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Schema(description = "ID папки каналов, в которой создаётся канал.", example = "15",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long folderId;

	@Schema(description = "Тип канала.", example = "TEXT", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private ChannelType type;

	@Schema(description = "Позиция канала в списке (чем меньше — тем выше).", example = "0",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer position;

	@Schema(description = "Лимит участников (актуально для голосовых каналов). По умолчанию 100.",
			example = "100", defaultValue = "100", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer userLimit = 100;

	@Schema(description = "Тема канала (короткое описание).", example = "Обсуждение задач")
	private String topic;
}
