package com.zvonok.exception_handler.enumeration;

import lombok.Getter;

@Getter
public enum BusinessRuleMessage {
    BUSINESS_CANNOT_EDIT_DELETED_MESSAGE("Cannot edit a deleted message"),
    BUSINESS_AUTHENTICATED_PRINCIPAL_REQUIRED_MESSAGE("Authenticated principal is required to send messages"),
    BUSINESS_MESSAGE_TARGET_VALIDATION_FAILED_MESSAGE("Message must reference a room"),
    BUSINESS_USER_NOT_MEMBER_PRIVATE_ROOM_MESSAGE("User is not a member of the private room"),
    BUSINESS_USER_NOT_MEMBER_GROUP_ROOM_MESSAGE("User is not a member of the group room"),
    BUSINESS_ONLY_SENDER_CAN_EDIT_MESSAGE("Only the sender can edit the message");

    private final String message;

    BusinessRuleMessage(String message) {
        this.message = message;
    }
}


