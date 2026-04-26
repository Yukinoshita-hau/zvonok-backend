package com.zvonok.service.enums;

import lombok.Getter;

@Getter
public enum BrokerPath {
	FRIEND_REQUESTS_QUEUE_PATH("/queue/friend-requests"),

	ERRORS_QUEUE_PATH("/queue/errors"),

	ROOM_EVENTS_QUEUE_PATH("/queue/room-events"),

	UPDATED_USER_QUEUE_PATH("/queue/users");

	private final String path;

	BrokerPath(String path) {
		this.path = path;
	}
}
