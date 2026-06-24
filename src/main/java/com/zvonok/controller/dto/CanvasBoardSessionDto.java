package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;

import java.time.Instant;
public record CanvasBoardSessionDto(
		Long id,
		Long callId,
		Long roomId,
		CanvasBoardMode mode,
		CanvasBackground background,
		String createdBy,
		Instant createdAt,
		boolean active
) {
}
