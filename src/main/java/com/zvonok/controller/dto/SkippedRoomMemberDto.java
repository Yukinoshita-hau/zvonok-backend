package com.zvonok.controller.dto;

public record SkippedRoomMemberDto(
		Long userId,
		String reason
) {
}
