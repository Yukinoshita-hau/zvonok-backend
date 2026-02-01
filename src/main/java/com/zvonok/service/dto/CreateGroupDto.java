package com.zvonok.service.dto;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(name = "CreateGroupDto", description = "Запрос на создание групповой комнаты.")
public class CreateGroupDto {
	@Schema(description = "Название комнаты.", example = "Пати на вечер", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
	private String roomName;

	@Schema(description = "Список usernames участников комнаты.",
			example = "[\"murat\", \"aliya\", \"timur\"]", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private List<String> roomMemberUsernames;
}
