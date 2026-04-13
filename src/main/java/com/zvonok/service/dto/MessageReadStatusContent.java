package com.zvonok.service.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessageReadStatusContent {
	private final Long messageId;
	private final Long roomId;
	private final String readBy;
}
