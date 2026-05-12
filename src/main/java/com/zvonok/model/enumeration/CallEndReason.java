package com.zvonok.model.enumeration;

public enum CallEndReason {
    HOST_ENDED,
	USER_LEFT,
	NO_ACTIVE_PARTICIPANTS,
    LIVEKIT_ROOM_MISSING,
    STALE_CLEANUP,
    DECLINED,
    CANCELLED
}
