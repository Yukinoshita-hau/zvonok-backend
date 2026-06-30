package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.UserStatus;

import java.time.LocalDateTime;

public record UserMiniProfileDto(
		Long userId,
		String username,
		String displayName,
		String avatarUrl,
		UserStatus status,
		LocalDateTime lastSeenAt,
		String about,
		String friendshipStatus,
		Long privateRoomId
) {
}
