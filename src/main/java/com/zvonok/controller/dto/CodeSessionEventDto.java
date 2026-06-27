package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CodeSessionEventType;

import java.time.Instant;

public record CodeSessionEventDto(
		CodeSessionEventType type,
		Long sessionId,
		Long callSessionId,
		Long roomId,
		Long senderId,
		String senderUsername,
		Object payload,
		Instant createdAt
) {
}
