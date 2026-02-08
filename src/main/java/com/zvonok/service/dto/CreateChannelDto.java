package com.zvonok.service.dto;

import com.zvonok.model.enumeration.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "CreateChannelDto", description = "Запрос на создание канала в папке каналов.")
public class CreateChannelDto {

	@Schema(description = "Название канала.", example = "general", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(min = 3, max = 100, message = "Name must be between 3 and 100")
	private String name;

	@Schema(description = "ID папки каналов, в которой создаётся канал.", example = "15",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long folderId;

	@Schema(description = "Тип канала.", example = "TEXT", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private ChannelType type;

	@Schema(description = "Позиция канала в списке (чем меньше — тем выше).", example = "0",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull
	private Integer position;

	@Schema(description = "Лимит участников (актуально для голосовых каналов). По умолчанию 10.",
			example = "100", defaultValue = "100", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
@NotNull()
	private Integer userLimit = 100;

	@Schema(description = "Тема канала (короткое описание).", example = "Обсуждение задач")
	@NotBlank
	@Size(max = 2000, message = "topic length must be not more than 2000")
	private String topic;
}
