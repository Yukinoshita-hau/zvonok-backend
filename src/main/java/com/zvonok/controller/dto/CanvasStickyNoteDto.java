package com.zvonok.controller.dto;

import java.time.Instant;

public record CanvasStickyNoteDto(
		Long id,
		Long boardId,
		String noteKey,
		String createdBy,
		String text,
		String color,
		Double x,
		Double y,
		Double width,
		Double height,
		Integer zIndex,
		Instant createdAt,
		Instant updatedAt
) {
}
