package com.zvonok.controller.dto;

public record CreateCanvasStickyNoteRequest(
		String noteKey,
		String text,
		String color,
		Double x,
		Double y,
		Double width,
		Double height
) {
}
