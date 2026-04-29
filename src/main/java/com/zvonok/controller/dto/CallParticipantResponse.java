package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CallParticipantStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CallParticipantResponse(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        CallParticipantStatus status,
        LocalDateTime joinedAt,
        LocalDateTime acceptedAt,
        LocalDateTime leftAt
) {
}
