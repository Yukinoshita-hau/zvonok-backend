package com.zvonok.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;

public record CreateCanvasBoardRequest(
		CanvasBoardMode mode,
		CanvasBackground background,
		@JsonAlias("screenShareOwnerUsername")
		String overlayOwnerUsername
) {
}
