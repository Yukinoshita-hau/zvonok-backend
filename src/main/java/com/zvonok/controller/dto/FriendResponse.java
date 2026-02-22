package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@Schema(name = "FriendResponse", description = "Ответ с данными дружбы и информацией о друге.")
public class FriendResponse {

	@Schema(description = "ID связи (дружбы).", example = "5001",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	Long friendshipId;

	@Schema(description = "ID пользователя-друга.", example = "42",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	Long friendId;

	@Schema(description = "Username пользователя-друга.", example = "aliya",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	String friendUsername;

	@Schema(description = "URL аватара пользователя-друга.",
			example = "https://cdn.zvonok.app/avatars/42.png",
			accessMode = Schema.AccessMode.READ_ONLY)
	String friendAvatarUrl;

	@Schema(description = "Статус пользователя-друга.", example = "ONLINE",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	UserStatus friendStatus;

	@Schema(description = "Дата и время, с которых пользователи являются друзьями (ISO-8601).",
			example = "2026-01-15T10:00:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	LocalDateTime friendshipSince;
}

