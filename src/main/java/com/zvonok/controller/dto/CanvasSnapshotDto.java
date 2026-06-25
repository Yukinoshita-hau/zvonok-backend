package com.zvonok.controller.dto;

import java.util.List;

public record CanvasSnapshotDto(
		Long boardId,
		List<CanvasStrokeDto> strokes,
		List<CanvasStickyNoteDto> notes,
		List<CanvasNoteVoteDto> votes
) {
}
