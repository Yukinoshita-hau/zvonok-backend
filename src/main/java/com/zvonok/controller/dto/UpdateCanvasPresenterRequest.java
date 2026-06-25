package com.zvonok.controller.dto;

public record UpdateCanvasPresenterRequest(
		Boolean presenterModeEnabled,
		String presenterUsername
) {
}
