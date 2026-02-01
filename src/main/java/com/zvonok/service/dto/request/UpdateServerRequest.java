package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Schema(name = "UpdateServerRequest",
		description = "Данные для обновления настроек сервера. Поля опциональные: передаются только изменяемые.")
@Data
public class UpdateServerRequest {
	@Schema(description = "Название сервера (до 100 символов).", example = "Zvonok Dev",
			maxLength = 100, nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Size(max = 100, message = "Название сервера не может превышать 100 символов")
	private String name;

	@Schema(description = "Лимит участников сервера (от 10 до 10000).", example = "250",
			minimum = "10", maximum = "10000", nullable = true,
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Min(value = 10, message = "Минимальное количество участников: 10")
	@Max(value = 10000, message = "Максимальное количество участников: 10000")
	private Integer maxMembers;
}

