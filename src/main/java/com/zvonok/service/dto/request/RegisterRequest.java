package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "Регестрационные данные")
@Data
public class RegisterRequest {
	@Schema(description = "Имя пользователя", example = "someUsername")
	@NotBlank(message = "Username is required")
	@Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
	private String username;

	@Schema(description = "почта пользователя", example = "someUsername@example.com")
	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Size(min = 5, max = 100, message = "Email must be between 5 and 100 characters")
	private String email;

	@Schema(description = "Пароль пользователя", example = "theStrongExamplePassword")
	@NotBlank(message = "Password is required")
	@Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
	private String password;
}
