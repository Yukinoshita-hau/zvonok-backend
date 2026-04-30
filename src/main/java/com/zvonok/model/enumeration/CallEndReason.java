package com.zvonok.model.enumeration;

public enum CallEndReason {
	USER_ENDED,
    HOST_ENDED,
    ALL_LEFT,
    EMPTY_TIMEOUT,
    ALONE_TIMEOUT,
    LIVEKIT_ROOM_FINISHED,
    STALE_CLEANUP,
    DECLINED,
    CANCELLED
}
