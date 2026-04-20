package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.FriendRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@Schema(name = "FriendRequestResponse", description = "Ответ с данными заявки в друзья.")
public class FriendRequestResponse {

	@Schema(description = "ID заявки.", example = "1001", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	Long requestId;

	@Schema(description = "ID отправителя.", example = "10",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	Long senderId;

	@Schema(description = "Username отправителя.", example = "murat",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	String senderUsername;

	String senderDisplayName;

	@Schema(description = "ссылка на аватар отправителя.",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	String senderAvatarUrl;

	@Schema(description = "ID получателя.", example = "11",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	Long receiverId;

	@Schema(description = "Username получателя.", example = "aliya",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	String receiverUsername;

	String receiverDisplayName;

	@Schema(description = "ссылка на аватар получателя.",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	String receiverAvatarUrl;

	@Schema(description = "Статус заявки.", example = "PENDING",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	FriendRequestStatus status;

	@Schema(description = "Дата и время создания заявки (ISO-8601).",
			example = "2026-02-01T17:20:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	LocalDateTime createdAt;

	@Schema(description = "Дата и время последнего изменения (ISO-8601).",
			example = "2026-02-01T17:25:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	LocalDateTime updatedAt;
}

