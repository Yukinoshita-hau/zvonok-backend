package com.zvonok.controller.dto;

import java.time.Instant;

public record CanvasNoteVoteDto(
		Long noteId,
		String userId,
		Instant createdAt
) {
}
