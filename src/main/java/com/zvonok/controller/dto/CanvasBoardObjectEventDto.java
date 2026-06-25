package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasBoardObjectEventType;

import java.time.Instant;

public record CanvasBoardObjectEventDto(
		CanvasBoardObjectEventType type,
		Long boardId,
		CanvasStickyNoteDto note,
		Long noteId,
		String userId,
		Instant timestamp
) {
}
