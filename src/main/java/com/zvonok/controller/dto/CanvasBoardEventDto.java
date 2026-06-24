package com.zvonok.controller.dto;

public record CanvasBoardEventDto(
		String type,
		CanvasBoardSessionDto board
) {
}
