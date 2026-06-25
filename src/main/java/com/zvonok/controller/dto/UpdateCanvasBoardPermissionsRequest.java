package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasDrawingAccess;

public record UpdateCanvasBoardPermissionsRequest(
		CanvasDrawingAccess drawingAccess,
		String selectedDrawerUsername
) {
}
