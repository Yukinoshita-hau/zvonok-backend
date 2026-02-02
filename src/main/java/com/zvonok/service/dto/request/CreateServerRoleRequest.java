package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "CreateServerRoleRequest", description = "Запрос на создание роли на сервере.")
@Data
public class CreateServerRoleRequest {
	@Schema(description = "Название роли (до 50 символов).", example = "moderator", maxLength = 50,
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50)
	private String name;

	@Schema(description = "Цвет роли в HEX формате #RRGGBB.", example = "#FFFFFF",
			pattern = "^#([A-Fa-f0-9]{6})$", defaultValue = "#FFFFFF", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a hex value like #FFFFFF")
	@NotBlank
	@Size(min = 7, max = 7, message = "color must be 7 characters long")
	private String color = "#FFFFFF";

	@Schema(description = "Позиция роли (чем больше — тем выше в списке).", example = "0",
			defaultValue = "0", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Integer position = 0;

	@Schema(description = "Битовая маска прав роли (permissions bitset).", example = "1024",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long serverPermissions;

	@Schema(description = "Можно ли упоминать роль (ping).", example = "true",
			defaultValue = "true", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private boolean mentionable = true;
}

