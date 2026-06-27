package com.zvonok.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCodeSessionRequestDto(
		@NotNull
		Long callSessionId,

		@NotBlank
		String language,

		@Size(max = 50_000)
		String initialCode,

		@Size(max = 20_000)
		String stdin
) {
}
