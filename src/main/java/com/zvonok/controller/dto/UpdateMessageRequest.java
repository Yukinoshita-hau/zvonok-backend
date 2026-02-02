package com.zvonok.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "UpdateMessageRequest", description = "Запрос на обновление сообщения.")
@Data
public class UpdateMessageRequest {
	@Schema(description = "Текст сообщения. Не может быть пустым или состоять только из пробелов.",
			example = "Исправил опечатку в предыдущем сообщении", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1)
	@NotBlank(message = "Content is required")
	@Size(min = 1, max = 2500, message = "Content must be between 1 and 2500 characters")
	private String content;
}

