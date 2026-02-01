package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "UpdateServerRoleRequest",
		description = "Данные для обновления роли на сервере. Обычно передаются только изменяемые поля.")
public class UpdateServerRoleRequest {
	@Schema(description = "Название роли (до 50 символов).", example = "moderator", maxLength = 50)
	@Size(max = 50)
	private String name;

	@Schema(description = "Цвет роли в HEX формате #RRGGBB.", example = "#FFAA00",
			pattern = "^#([A-Fa-f0-9]{6})$")
	@Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a hex value like #FFFFFF")
	private String color;

	@Schema(description = "Позиция роли (чем больше — тем выше в списке).", minimum = "0",
			example = "10")
	private Integer position;

	@Schema(description = "Битовая маска прав роли (permissions bitset).", example = "1024")
	private Long serverPermissions;

	@Schema(description = "Можно ли упоминать роль (ping).", example = "true")
	private Boolean mentionable;

	@Schema(description = "Активна ли роль (не скрыта/не отключена).", example = "true")
	private Boolean active;
}

