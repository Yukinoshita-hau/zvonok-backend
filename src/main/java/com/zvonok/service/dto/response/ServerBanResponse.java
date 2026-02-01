package com.zvonok.service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(name = "ServerBanResponse", description = "Информация о бане пользователя на сервере.")
public class ServerBanResponse {

	@Schema(description = "ID забаненного пользователя.", example = "123",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long userId;

	@Schema(description = "Username забаненного пользователя.", example = "spammer42",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String username;

	@Schema(description = "Причина бана (может отсутствовать).", example = "Спам",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String reason;

	@Schema(description = "Дата и время, когда бан был выдан (ISO-8601).",
			example = "2026-02-01T17:10:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime createdAt;

	@Schema(description = "Дата и время окончания бана. Если null — бан бессрочный.",
			example = "2026-02-10T12:00:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime expiresAt;

	@Schema(description = "ID пользователя/модератора, который выдал бан.", example = "7",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long bannedById;

	@Schema(description = "Username пользователя/модератора, который выдал бан.", example = "admin",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String bannedByUsername;
}

