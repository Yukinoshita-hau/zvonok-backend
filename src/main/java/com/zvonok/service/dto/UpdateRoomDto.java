package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "UpdateRoomDto", description = "Данные для обновления комнаты.")
@Data
public class UpdateRoomDto {
	@Schema(description = "Название комнаты (до 100 символов).", example = "Общая", maxLength = 100,
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Size(max = 100, message = "Room name must not exceed 100 characters")
	private String name;
}

