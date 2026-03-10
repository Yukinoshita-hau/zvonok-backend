package com.zvonok.service.dto;

import com.zvonok.controller.dto.FriendRequestResponse;
import com.zvonok.service.enums.FriendEventType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FriendEventMessage {
	FriendEventType type;
	FriendRequestResponse payload;
}
