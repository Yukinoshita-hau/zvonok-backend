package com.zvonok.controller.dto;

import java.time.LocalDateTime;

public record CreateRoomInviteResponse(
		Long roomId,
		String inviteToken,
		String inviteUrl,
		LocalDateTime expiresAt,
		Integer maxUses
) {
}
