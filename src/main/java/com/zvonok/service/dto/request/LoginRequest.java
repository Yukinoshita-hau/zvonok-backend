package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "LoginRequest", description = "Запрос на вход по username или email и паролю.")
public class LoginRequest {

	@Schema(description = "Username или email пользователя.", example = "murat", minLength = 5,
			maxLength = 100, nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "Username or Email is required")
	@Size(min = 5, max = 100, message = "Username or email must be between 5 and 100 characters")
	private String usernameOrEmail;

	@Schema(description = "Пароль пользователя.", example = "P@ssw0rd123", minLength = 6,
			maxLength = 100, nullable = false, requiredMode = Schema.RequiredMode.REQUIRED,
			accessMode = Schema.AccessMode.WRITE_ONLY)
	@NotBlank(message = "Password is required")
	@Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
	private String password;
}

