package com.zvonok.service.dto.code;

public record CodeRunResponseDto(
		ExecutionResponseStatus status,
		String stdout,
		Integer exitCode,
		long executionTimeMs
) {
}
