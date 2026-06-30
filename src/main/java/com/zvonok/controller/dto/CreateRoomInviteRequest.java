package com.zvonok.controller.dto;

import java.time.LocalDateTime;

public record CreateRoomInviteRequest(
		LocalDateTime expiresAt,
		Integer maxUses
) {
}
