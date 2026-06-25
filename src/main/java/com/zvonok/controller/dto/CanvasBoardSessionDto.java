package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;
import com.zvonok.model.enumeration.CanvasDrawingAccess;
import com.zvonok.model.enumeration.CanvasTemplateType;
import com.zvonok.model.enumeration.CanvasTimerStatus;

import java.time.Instant;

public record CanvasBoardSessionDto(
		Long id,
		Long callId,
		Long roomId,
		CanvasBoardMode mode,
		CanvasBackground background,
		String createdBy,
		Instant createdAt,
		boolean active,
		CanvasDrawingAccess drawingAccess,
		String selectedDrawerUsername,
		CanvasTemplateType templateType,
		Instant timerStartedAt,
		Integer timerDurationSeconds,
		CanvasTimerStatus timerStatus,
		String backgroundImageUrl,
		String backgroundImageCreatedBy,
		Instant backgroundImageCreatedAt,
		String presenterUsername,
		boolean presenterModeEnabled
) {
}
