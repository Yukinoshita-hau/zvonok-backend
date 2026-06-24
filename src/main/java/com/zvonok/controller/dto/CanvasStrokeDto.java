package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasTool;

import java.util.List;

public record CanvasStrokeDto(
		String id,
		String userId,
		String color,
		Integer width,
		CanvasTool tool,
		List<CanvasPointDto> points
) {
}
