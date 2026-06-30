package com.zvonok.controller.dto;

import java.time.LocalDateTime;

public record AddedRoomMemberDto(
		Long userId,
		String username,
		String avatarUrl,
		LocalDateTime joinedAt
) {
}
