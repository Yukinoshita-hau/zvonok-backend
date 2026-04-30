package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.CallParticipantStatus;
import lombok.Builder;

@Builder
public record CallParticipantResponse (
	Long userId,
	String username,
	String displayName,
	String avatarUrl,
	CallParticipantStatus status,
	LocalDateTime joinedAt,
	LocalDateTime acceptedAt,
	LocalDateTime leftAt
) {}
