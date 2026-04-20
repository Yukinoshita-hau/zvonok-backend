package com.zvonok.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class WebSocketMessageRequest {
	private final String content;
	private final Long replyToMessageId;
}
