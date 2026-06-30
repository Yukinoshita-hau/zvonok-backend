package com.zvonok.controller.dto;

public record RoomInvitePreviewDto(
		Long roomId,
		String roomName,
		String roomAvatarUrl,
		Integer membersCount,
		String createdByUsername,
		boolean alreadyMember,
		boolean expired
) {
}
