package com.zvonok.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrokerPath {
    USER_EVENTS_QUEUE_PATH("/queue/user-events");

    private final String path;
}
