package com.zvonok.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CodeStdinSyncRequestDto(
		@NotNull
		@Size(max = 20_000)
		String stdin
) {
}
