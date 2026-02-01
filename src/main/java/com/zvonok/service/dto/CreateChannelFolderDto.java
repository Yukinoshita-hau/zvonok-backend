package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "CreateChannelFolderDto",
		description = "Запрос на создание папки каналов на сервере.")
public class CreateChannelFolderDto {

	@Schema(description = "Название папки каналов.", example = "Текстовые каналы", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	private String name;

	@Schema(description = "ID сервера, в котором создаётся папка.", example = "42",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long serverId;

	@Schema(description = "Позиция папки в списке (чем меньше — тем выше). Если не передано, используется 0.",
			example = "0", defaultValue = "0", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Integer position;
}
