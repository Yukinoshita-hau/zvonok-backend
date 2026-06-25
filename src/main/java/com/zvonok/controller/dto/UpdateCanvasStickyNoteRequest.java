package com.zvonok.controller.dto;

public record UpdateCanvasStickyNoteRequest(
		String text,
		String color,
		Double x,
		Double y,
		Double width,
		Double height,
		Integer zIndex
) {
}
