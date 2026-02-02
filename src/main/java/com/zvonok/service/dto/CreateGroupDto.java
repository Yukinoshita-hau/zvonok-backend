package com.zvonok.service.dto;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Schema(name = "CreateGroupDto", description = "Запрос на создание групповой комнаты.")
public class CreateGroupDto {
	@Schema(description = "Название комнаты.", example = "Пати на вечер", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
		@NotBlank
		@Size(min = 1, max = 100, message = "Room name must between 1 and 100")
	private String roomName;

	@Schema(description = "Список usernames участников комнаты.",
			example = "[\"murat\", \"aliya\", \"timur\"]", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull
		@Size(min = 2, message = "Room size must be at least 2 member")
	private List<String> roomMemberUsernames;
}
