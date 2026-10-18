package com.zvonok.exception_handler.enumeration;

import lombok.Getter;

@Getter
public enum HttpResponseMessage {
	// Room
	HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE("Room was not found"),
	HTTP_INVALID_ROOM_SIZE_RESPONSE_MESSAGE("Invalid room size"),
	HTTP_ROOM_SIZE_MAX_FIFTEEN_MEMBERS_RESPONSE_MESSAGE("Maximum of 15 members in group chat"),
	HTTP_ROOM_GROUP_ONLY_RESPONSE_MESSAGE("This operation is available only for group rooms"),
	HTTP_ROOM_MESSAGES_CLEAR_PRIVATE_DENIED_RESPONSE_MESSAGE("Clearing all messages is not allowed for private rooms"),
	HTTP_ROOM_INVITE_NOT_FOUND_RESPONSE_MESSAGE("Room invite was not found"),
	HTTP_ROOM_INVITE_INVALID_RESPONSE_MESSAGE("Room invite is invalid or expired"),
	HTTP_BOOMBOX_SESSION_NOT_FOUND_RESPONSE_MESSAGE("Boombox session was not found"),
	HTTP_BOOMBOX_TRACK_NOT_FOUND_RESPONSE_MESSAGE("Boombox track was not found"),
	HTTP_BOOMBOX_QUEUE_ITEM_NOT_FOUND_RESPONSE_MESSAGE("Boombox queue item was not found"),
	HTTP_BOOMBOX_ACCESS_DENIED_RESPONSE_MESSAGE("Not enough rights to manage boombox"),
	HTTP_BOOMBOX_SESSION_ENDED_RESPONSE_MESSAGE("Boombox session is ended"),
	HTTP_BOOMBOX_TRACK_REQUIRED_RESPONSE_MESSAGE("Boombox track is required"),
	HTTP_BOOMBOX_TRACK_TITLE_REQUIRED_RESPONSE_MESSAGE("Boombox track title is required"),
	HTTP_BOOMBOX_TRACK_AUDIO_URL_REQUIRED_RESPONSE_MESSAGE("Boombox track audioUrl is required"),
	HTTP_BOOMBOX_TRACK_DURATION_INVALID_RESPONSE_MESSAGE("Boombox track duration must be positive"),
	HTTP_BOOMBOX_QUEUE_ITEM_OTHER_SESSION_RESPONSE_MESSAGE("Boombox queue item belongs to another session"),
	HTTP_BOOMBOX_QUEUE_ITEM_REMOVE_DENIED_RESPONSE_MESSAGE("Cannot remove currently playing boombox queue item"),
	HTTP_BOOMBOX_QUEUE_ITEM_VOTE_DENIED_RESPONSE_MESSAGE("Only waiting boombox queue items can be voted"),
	HTTP_BOOMBOX_NO_TRACK_TO_PLAY_RESPONSE_MESSAGE("Boombox queue does not contain playable tracks"),
	HTTP_BOOMBOX_SEEK_POSITION_INVALID_RESPONSE_MESSAGE("Boombox seek position is outside current track duration"),

	// RoomReadState
	HTTP_ROOM_READ_STATE_NOT_FOUND_RESPONSE_MESSAGE("RoomReedState was not found"),

	// User
	HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE("User was not found"),
	HTTP_USER_NOT_MEMBER_ROOM_RESPONSE_MESSAGE("User is not a member of the room"),
	HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE("User with this username already exist"),
	HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE("User with this email already exist"),
	HTTP_FRIEND_REQUEST_NOT_FOUND_RESPONSE_MESSAGE("Friend request was not found"),
	HTTP_FRIEND_REQUEST_ALREADY_EXISTS_RESPONSE_MESSAGE("Friend request already exists"),
	HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE("You cannot perform this action on the friend request"),
	HTTP_CANNOT_ADD_SELF_AS_FRIEND_RESPONSE_MESSAGE("You cannot add yourself as a friend"),
	HTTP_FRIENDSHIP_ALREADY_EXISTS_RESPONSE_MESSAGE("Users are already friends"),
	HTTP_FRIENDSHIP_NOT_FOUND_RESPONSE_MESSAGE("Friendship was not found"),
	HTTP_INCORRECT_USER_USERNAME_LENGTH_RESPONSE_MESSAGE("Username must be between 3 and 50 characters"),
	HTTP_INCORRECT_USER_EMAIL_LENGTH_RESPONSE_MESSAGE("Email must be between 5 and 100 characters"),
	HTTP_INCORRECT_USER_PASSWORD_LENGTH_RESPONSE_MESSAGE("Password must be between 5 and 100 characters"),
	HTTP_INCORRECT_USER_USERNAME_TYPE_RESPONSE_MESSAGE("Username can not be null"),
	HTTP_INCORRECT_USER_EMAIL_TYPE_RESPONSE_MESSAGE("Email can not be null"),
	HTTP_INCORRECT_USER_PASSWORD_TYPE_RESPONSE_MESSAGE("Password can not be null"),
	HTTP_USER_NOT_YOUR_FRIEND_RESPONSE_MESSAGE(" is not your friend"),
	HTTP_USER_THEME_INVALID_RESPONSE_MESSAGE("User custom theme contains invalid color values"),







	// Permissions
	HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE("Insufficient permissions"),
	HTTP_INSUFFICIENT_MESSAGE_PERMISSIONS_RESPONSE_MESSAGE("Not enough rights to manage the message"),	

	// Authorization and validation data
	HTTP_INVALID_JWT_RESPONSE_MESSAGE("JWT token not valid or missing!"),
	HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE("Invalid user or password"),
	HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE("Refresh token is invalid"),
	HTTP_REFRESH_TOKEN_EXPIRED_RESPONSE_MESSAGE("Refresh token has expired"),
	HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE("Refresh token is revoked"),
	HTTP_REFRESH_TOKEN_NOT_TRANSFERRED("Refresh token not transferred"),
	HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE("Message was not found"),
	HTTP_MESSAGE_EMPTY_RESPONSE_MESSAGE("Message content or attachments are required"),
	HTTP_MESSAGE_ATTACHMENT_NOT_FOUND_RESPONSE_MESSAGE("Message attachment was not found"),
	HTTP_MESSAGE_ATTACHMENT_TOO_MANY_RESPONSE_MESSAGE("Maximum 10 attachments are allowed per message"),
	HTTP_MESSAGE_ATTACHMENT_TYPE_INVALID_RESPONSE_MESSAGE("Only JPEG, PNG, WebP, GIF images and MP4, WebM, QuickTime videos are allowed"),
	HTTP_MESSAGE_ATTACHMENT_IMAGE_TOO_LARGE_RESPONSE_MESSAGE("Image attachment size must not exceed 10 MB"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_TOO_LARGE_RESPONSE_MESSAGE("Video attachment size must not exceed 100 MB"),
	HTTP_MESSAGE_ATTACHMENT_AUDIO_TOO_LARGE_RESPONSE_MESSAGE("Audio attachment size must not exceed 25 MB"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_TOO_LARGE_RESPONSE_MESSAGE("Video note attachment size must not exceed 100 MB"),
	HTTP_MESSAGE_ATTACHMENT_DURATION_INVALID_RESPONSE_MESSAGE("Attachment duration must not be negative"),
	HTTP_MESSAGE_ATTACHMENT_AUDIO_DURATION_TOO_LONG_RESPONSE_MESSAGE("Audio attachment duration must not exceed 5 minutes"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_DURATION_TOO_LONG_RESPONSE_MESSAGE("Video note attachment duration must not exceed 1 minute"),
	HTTP_MESSAGE_ATTACHMENT_UPLOAD_FAILED_RESPONSE_MESSAGE("Failed to upload message attachment"),
	HTTP_UPLOAD_FILE_TOO_LARGE_RESPONSE_MESSAGE("Uploaded file is too large. Max file size is 100 MB"),
	HTTP_AUTH_PRINCEPAL_REQUIRED_RESPONSE_MESSAGE("Authenticated principal required for this WebSocket operation"),

	// CallSession
	HTTP_CALL_SESSION_NOT_FOUND_RESPONSE_MESSAGE("Call session not found"),
	HTTP_CALL_SESSION_ALREADY_END_RESPONSE_MESSAGE("Call session already end"),

	// CallParticipant
	HTTP_CALL_PARTICIPANT_INCORRECT_STATUS_RESPONSE_MESSAGE("Incorrect call participant status"),

	// Conference
	HTTP_CONFERENCE_NOT_FOUND_RESPONSE_MESSAGE("Conference not found"),
	HTTP_ONLY_HOST_CAN_END_CONFERENCE_RESPONSE_MESSAGE("Only host can end conference"),
	HTTP_CONFERENCE_END_RESPONSE_MESSAGE("Conference is ended"),

	// LiveKit
	HTTP_LIVEKIT_ROOM_RESPONSE_MESSAGE("LikeKit room error"),
	HTTP_LIVEKIT_ROOM_NOT_FOUND_RESPONSE_MESSAGE("LiveKit room was not found"),
	HTTP_LIVEKIT_TOKEN_GENERATE_ERROR_RESPONSE_MESSAGE("Failed to generate LiveKit token"),
	HTTP_LIVEKIT_CALL_STATE_CONFLICT_RESPONSE_MESSAGE("Call room no longer exist"),

	// S3
	HTTP_FILE_NOT_FOUND_RESPONSE_MESSAGE("file was not found"),

	// MessageReadStatus
	HTTP_MESSAGE_READ_STATUS_RESPONSE_MESSAGE("MessageReadStatus was not found");

	private final String message;

	HttpResponseMessage(String message) {
		this.message = message;
	}
}
