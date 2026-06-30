package com.zvonok.controller.dto;

public record JoinRoomInviteResponse(
		Long roomId,
		boolean joined,
		boolean alreadyMember
) {
}
