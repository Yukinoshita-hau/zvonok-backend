package com.zvonok.service.enums;

import lombok.Getter;

@Getter
public enum BrokerPath {
	FRIEND_REQUESTS_QUEUE_PATH("/queue/friend-requests"),

	ERRORS_QUEUE_PATH("/queue/errors"),

	ROOM_EVENTS_QUEUE_PATH("/queue/room-events");

	private final String path;

	BrokerPath(String path) {
		this.path = path;
	}
}
