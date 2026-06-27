package com.zvonok.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CodeContentSyncRequestDto(
		@NotNull
		@Size(max = 50_000)
		String code,

		Long revision
) {
}
