package com.zvonok.controller.dto;

import com.zvonok.service.dto.code.ExecutionResponseStatus;

import java.time.Instant;

public record CodeSessionDto(
		Long id,
		Long callSessionId,
		Long roomId,
		boolean active,
		String language,
		String code,
		String stdin,
		String lastOutput,
		ExecutionResponseStatus lastStatus,
		Integer lastExitCode,
		Long lastExecutionTimeMs,
		String createdBy,
		String activeEditor,
		Instant createdAt,
		Instant updatedAt
) {
}
