package com.zvonok.service.dto.code;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeRunRequestDto(
		@NotBlank
		String language,

		@NotBlank
		@Size(max = 50_000)
		String code,

		@Size(max = 20_000)
		String stdin
) {
}
