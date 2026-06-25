package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasTemplateType;

public record UpdateCanvasBoardTemplateRequest(
		CanvasTemplateType templateType
) {
}
