package com.zvonok.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeLanguageChangeRequestDto(
		@NotBlank
		String language,

		@Size(max = 50_000)
		String code
) {
}
