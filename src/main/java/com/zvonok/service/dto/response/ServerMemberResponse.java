package com.zvonok.service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(name = "ServerMemberResponse", description = "Ответ с данными участника сервера.")
public class ServerMemberResponse {

	@Schema(description = "ID пользователя.", example = "42",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long userId;

	@Schema(description = "Username пользователя.", example = "murat",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String username;

	@Schema(description = "URL аватара пользователя.",
			example = "https://cdn.zvonok.app/avatars/42.png",
			accessMode = Schema.AccessMode.READ_ONLY)
	private String avatarUrl;

	@Schema(description = "Никнейм пользователя на сервере (может отсутствовать).",
			example = "muratik", accessMode = Schema.AccessMode.READ_ONLY)
	private String nickname;

	@Schema(description = "Дата и время вступления на сервер (ISO-8601).",
			example = "2026-01-20T12:00:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime joinedAt;

	@Schema(description = "Список ролей участника.", example = "[\"user\", \"moderator\"]",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private List<String> roles;

	@Schema(description = "Является ли пользователь владельцем сервера.", example = "false",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Boolean isOwner;
}
