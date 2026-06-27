package com.zvonok.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CodeAccessRequestDto(
		@NotBlank
		String username
) {
}
