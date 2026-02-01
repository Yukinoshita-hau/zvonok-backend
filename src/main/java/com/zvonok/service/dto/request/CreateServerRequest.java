package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "CreateServerRequest", description = "Запрос на создание сервера.")
@Data
public class CreateServerRequest {
	@Schema(description = "Название сервера (5–100 символов).", example = "Zvonok Dev",
			minLength = 5, maxLength = 100, nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "Server name cannot be empty")
	@Size(min = 5, max = 100, message = "Name must be between 5 and 100 characters")
	private String name;

	@Schema(description = "Максимальное количество участников (10–10000).", example = "1000",
			minimum = "10", maximum = "10000", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "MaxMembers cannot be empty")
	@Min(value = 10)
	@Max(10000)
	private Integer maxMembers;
}
