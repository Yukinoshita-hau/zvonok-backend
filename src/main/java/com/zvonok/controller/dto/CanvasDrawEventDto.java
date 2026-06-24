package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasDrawEventType;
import com.zvonok.model.enumeration.CanvasTool;

import java.time.Instant;

public record CanvasDrawEventDto(
		CanvasDrawEventType type,
		Long boardId,
		String strokeId,
		String userId,
		Double x,
		Double y,
		String color,
		Integer width,
		CanvasTool tool,
		Instant timestamp
) {
}
