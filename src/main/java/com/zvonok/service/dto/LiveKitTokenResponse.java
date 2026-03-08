package com.zvonok.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiveKitTokenResponse {
	private String serverUrl;

	private String participantToken;
}
