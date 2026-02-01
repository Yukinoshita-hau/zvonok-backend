package com.zvonok.service.dto.response;

import com.zvonok.model.ChannelFolder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "ServerResponse",
		description = "Ответ с данными сервера (гильдии) и его структурой каналов.")
@Data
@Builder
public class ServerResponse {
	@Schema(description = "ID сервера.", example = "42", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Long id;

	@Schema(description = "Название сервера.", example = "Zvonok Dev",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Schema(description = "Инвайт-код сервера (используется для присоединения).",
			example = "Cmh6teSYDmmCKJH", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String inviteCode;

	@Schema(description = "Максимальное количество участников сервера.", example = "1000",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer maxMembers;

	@Schema(description = "Текущее количество участников.", example = "128",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long memberCount;

	@Schema(description = "ID владельца сервера.", example = "7",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long ownerId;

	@Schema(description = "Ник/имя владельца сервера.", example = "Igor",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String ownerName;

	@Schema(description = "Дата и время создания сервера.", example = "2026-02-01T12:34:56",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime createdAt;

	@Schema(description = "Список папок каналов на сервере.",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private List<ChannelFolder> channelFolders;
}

