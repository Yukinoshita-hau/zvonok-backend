package com.zvonok.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(name = "UpdateMessageRequest", description = "Запрос на обновление сообщения.")
@Data
public class UpdateMessageRequest {
	@Schema(description = "Текст сообщения. Не может быть пустым или состоять только из пробелов.",
			example = "Исправил опечатку в предыдущем сообщении", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1)
	@NotBlank(message = "Content is required")
	private String content;
}

