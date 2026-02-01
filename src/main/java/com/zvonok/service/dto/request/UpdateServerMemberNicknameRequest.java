package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "UpdateServerMemberNicknameRequest",
		description = "Запрос на обновление никнейма участника на сервере.")
public class UpdateServerMemberNicknameRequest {

	@Schema(description = "Никнейм участника (до 50 символов).", example = "muratik",
			maxLength = 50, nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50, message = "Nickname must be at most 50 characters")
	private String nickname;
}

