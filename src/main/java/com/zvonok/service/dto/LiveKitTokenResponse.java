package com.zvonok.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LiveKitTokenResponse {
	private String serverUrl;

	private String participantToken;

	private Long callId;

	private LocalDateTime expiresAt;
}
