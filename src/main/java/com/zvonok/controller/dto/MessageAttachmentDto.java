package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.AttachmentType;

public record MessageAttachmentDto(
		Long id,
		AttachmentType type,
		String url,
		String downloadUrl,
		String originalFileName,
		String contentType,
		Long sizeBytes,
		Integer width,
		Integer height,
		Long durationMs,
		String waveform
) {
}
