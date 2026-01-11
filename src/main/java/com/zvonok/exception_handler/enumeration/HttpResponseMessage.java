package com.zvonok.exception_handler.enumeration;

import lombok.Getter;

@Getter
public enum HttpResponseMessage {
    // Room
    HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE("Room was not found"),
    HTTP_ROOM_SIZE_MAX_TEN_MEMBERS_RESPONSE_MESSAGE("Maximum of 10 members in group chat"),

    // User
    HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE("User was not found"),
    HTTP_USER_NOT_MEMBER_ROOM_RESPONSE_MESSAGE("User is not a member of the room"),
    HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE("User with this username already exist"),
    HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE("User with this email already exist"),
    HTTP_OWNER_CAN_NOT_LEAVE_SERVER_RESPONSE_MESSAGE("The owner cannot leave the server. First pass the ownership."),
    HTTP_YOU_NOT_MEMBER_THIS_SERVER_RESPONSE_MESSAGE("You are not a member of this server"),
    HTTP_FRIEND_REQUEST_NOT_FOUND_RESPONSE_MESSAGE("Friend request was not found"),
    HTTP_FRIEND_REQUEST_ALREADY_EXISTS_RESPONSE_MESSAGE("Friend request already exists"),
    HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE("You cannot perform this action on the friend request"),
    HTTP_CANNOT_ADD_SELF_AS_FRIEND_RESPONSE_MESSAGE("You cannot add yourself as a friend"),
    HTTP_FRIENDSHIP_ALREADY_EXISTS_RESPONSE_MESSAGE("Users are already friends"),
    HTTP_FRIENDSHIP_NOT_FOUND_RESPONSE_MESSAGE("Friendship was not found"),
	HTTP_INCORRECT_USER_USERNAME_LENGTH_RESPONSE_MESSAGE("Username must be between 3 and 50 characters"),
	HTTP_INCORRECT_USER_EMAIL_LENGTH_RESPONSE_MESSAGE("Email must be between 5 and 100 characters"),
	HTTP_INCORRECT_USER_PASSWORD_LENGTH_RESPONSE_MESSAGE("Email must be between 5 and 100 characters"),
	HTTP_INCORRECT_USER_USERNAME_TYPE_RESPONSE_MESSAGE("Username can not be null"),
	HTTP_INCORRECT_USER_EMAIL_TYPE_RESPONSE_MESSAGE("Email can not be null"),
	HTTP_INCORRECT_USER_PASSWORD_TYPE_RESPONSE_MESSAGE("Password can not be null"),
    
    // Channel
    HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE("Channel was not found"),

    // Server
    HTTP_SERVER_NOT_FOUND_RESPONSE_MESSAGE("Server was not found"),
    HTTP_SERVER_NOT_ACTIVE_RESPONSE_MESSAGE("Server is not active now"),
    HTTP_SERVER_NAME_NOT_VALID_RESPONSE_MESSAGE("Name must be between 5 and 100 characters"),
    HTTP_SERVER_MAX_MEMBERS_NOT_VALID_RESPONSE_MESSAGE("MaxMembers must be between 10 and 10000 members"),

    // ServerRole
    HTTP_SERVER_ROLE_NOT_FOUND_RESPONSE_MESSAGE("ServerRole was not found"),

    // ServerMemberRole
    HTTP_SERVER_MEMBER_ROLE_NOT_FOUND_RESPONSE_MESSAGE("ServerMemberRole was not found"),

    // ServerMember
    HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE("ServerMember was not found"),
    HTTP_SERVER_MAXIMUM_NUMBER_OF_SERVER_MEMBERS_RESPONSE_MESSAGE("Maximum number of server members reached"),
    HTTP_SERVER_BAN_NOT_FOUND_RESPONSE_MESSAGE("Ban record was not found"),

    // ChannelFolder
    HTTP_CHANNEL_FOLDER_NOT_FOUND_RESPONSE_MESSAGE("Channel folder was not found"),

    // Permissions
    HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE("Not enough rights to manage the server"),
    HTTP_USER_BANNED_RESPONSE_MESSAGE("User is banned from this server"),

    // Authorization and validation data
    HTTP_INVALID_JWT_RESPONSE_MESSAGE("JWT token not valid or missing!"),
    HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE("Invalid user or password"),
    HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE("Refresh token is invalid"),
    HTTP_REFRESH_TOKEN_EXPIRED_RESPONSE_MESSAGE("Refresh token has expired"),
    HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE("Refresh token is revoked"),
    HTTP_REDEFINITION_RESPONSE_MESSAGE("Override can be either for the role or for the user"),
    HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE("Message was not found");

    private final String message;

    HttpResponseMessage(String message) {
        this.message = message;
    }
}
