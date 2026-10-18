package com.zvonok.repository;

/** Scalar result: does not hydrate messages, senders or read-state entities. */
public interface RoomUnreadSummary {
    Long getRoomId();
    Long getUnreadCount();
    Long getFirstUnreadMessageId();
}
