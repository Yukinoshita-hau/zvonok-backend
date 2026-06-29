package com.zvonok.exception_handler.enumeration;

import lombok.Getter;

@Getter
public enum HttpResponseMessage {
	// Room
	HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE("Room was not found"),
	HTTP_INVALID_ROOM_SIZE_RESPONSE_MESSAGE("Invalid room size"),
	HTTP_ROOM_SIZE_MAX_FIFTEEN_MEMBERS_RESPONSE_MESSAGE("Maximum of 15 members in group chat"),

	// RoomReadState
	HTTP_ROOM_READ_STATE_NOT_FOUND_RESPONSE_MESSAGE("RoomReedState was not found"),

	// User
	HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE("User was not found"),
	HTTP_USER_NOT_MEMBER_ROOM_RESPONSE_MESSAGE("User is not a member of the room"),
	HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE("User with this username already exist"),
	HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE("User with this email already exist"),
	HTTP_OWNER_CAN_NOT_LEAVE_SERVER_RESPONSE_MESSAGE("The owner cannot leave the server. First pass the ownership."),
	HTTP_YOU_NOT_MEMBER_THIS_SERVER_RESPONSE_MESSAGE("You are not a member of this server"),
	HTTP_YOU_ALREADY_MEMBER_THIS_SERVER_RESPONSE_MESSAGE("You are already a member of this server"),
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

	// Channel
	HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE("Channel was not found"),

	// Server
	HTTP_SERVER_NOT_FOUND_RESPONSE_MESSAGE("Server was not found"),
	HTTP_SERVER_NOT_ACTIVE_RESPONSE_MESSAGE("Server is not active now"),
	HTTP_SERVER_NAME_NOT_VALID_RESPONSE_MESSAGE("Name must be between 5 and 100 characters"),
	HTTP_SERVER_MAX_MEMBERS_NOT_VALID_RESPONSE_MESSAGE("MaxMembers must be between 10 and 10000 members"),

	// ServerRole
	HTTP_SERVER_ROLE_NOT_FOUND_RESPONSE_MESSAGE("Role was not found"),

	// ServerMemberRole
	HTTP_SERVER_MEMBER_ROLE_NOT_FOUND_RESPONSE_MESSAGE("MemberRole was not found"),

	// ServerMember
	HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE("Member was not found"),
	HTTP_SERVER_MAXIMUM_NUMBER_OF_SERVER_MEMBERS_RESPONSE_MESSAGE("Maximum number of members reached"),
	HTTP_SERVER_BAN_NOT_FOUND_RESPONSE_MESSAGE("Ban record was not found"),
	HTTP_SERVER_MEMBER_ALREADY_WAS_KICKED_RESPONSE_MESSAGE("Member already kicked"),

	// ChannelFolder
	HTTP_CHANNEL_FOLDER_NOT_FOUND_RESPONSE_MESSAGE("Channel folder was not found"),

	// Permissions
	HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE("Not enough rights to manage the server"),
	HTTP_INSUFFICIENT_MESSAGE_PERMISSIONS_RESPONSE_MESSAGE("Not enough rights to manage the message"),	
	HTTP_USER_BANNED_RESPONSE_MESSAGE("User is banned from this server"),

	// Authorization and validation data
	HTTP_INVALID_JWT_RESPONSE_MESSAGE("JWT token not valid or missing!"),
	HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE("Invalid user or password"),
	HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE("Refresh token is invalid"),
	HTTP_REFRESH_TOKEN_EXPIRED_RESPONSE_MESSAGE("Refresh token has expired"),
	HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE("Refresh token is revoked"),
	HTTP_REFRESH_TOKEN_NOT_TRANSFERRED("Refresh token not transferred"),
	HTTP_REDEFINITION_RESPONSE_MESSAGE("Override can be either for the role or for the user"),
	HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE("Message was not found"),
	HTTP_MESSAGE_EMPTY_RESPONSE_MESSAGE("Message content or attachments are required"),
	HTTP_MESSAGE_ATTACHMENT_NOT_FOUND_RESPONSE_MESSAGE("Message attachment was not found"),
	HTTP_MESSAGE_ATTACHMENT_TOO_MANY_RESPONSE_MESSAGE("Maximum 10 attachments are allowed per message"),
	HTTP_MESSAGE_ATTACHMENT_TYPE_INVALID_RESPONSE_MESSAGE("Only JPEG, PNG, WebP, GIF images and MP4, WebM, QuickTime videos are allowed"),
	HTTP_MESSAGE_ATTACHMENT_IMAGE_TOO_LARGE_RESPONSE_MESSAGE("Image attachment size must not exceed 10 MB"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_TOO_LARGE_RESPONSE_MESSAGE("Video attachment size must not exceed 100 MB"),
	HTTP_MESSAGE_ATTACHMENT_AUDIO_TOO_LARGE_RESPONSE_MESSAGE("Audio attachment size must not exceed 25 MB"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_TOO_LARGE_RESPONSE_MESSAGE("Video note attachment size must not exceed 50 MB"),
	HTTP_MESSAGE_ATTACHMENT_DURATION_INVALID_RESPONSE_MESSAGE("Attachment duration must not be negative"),
	HTTP_MESSAGE_ATTACHMENT_AUDIO_DURATION_TOO_LONG_RESPONSE_MESSAGE("Audio attachment duration must not exceed 5 minutes"),
	HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_DURATION_TOO_LONG_RESPONSE_MESSAGE("Video note attachment duration must not exceed 1 minute"),
	HTTP_MESSAGE_ATTACHMENT_UPLOAD_FAILED_RESPONSE_MESSAGE("Failed to upload message attachment"),
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

	// Code runner
	HTTP_UNSUPPORTED_EXECUTION_LANGUAGE_RESPONSE_MESSAGE("Unsupported execution language"),
	HTTP_CODE_RUNNER_UNAVAILABLE_RESPONSE_MESSAGE("Code runner is unavailable"),

	// Code session
	HTTP_CODE_SESSION_NOT_FOUND_RESPONSE_MESSAGE("Code session not found"),
	HTTP_CODE_SESSION_CLOSED_RESPONSE_MESSAGE("Code session is closed"),
	HTTP_CODE_SESSION_CREATE_FOR_ENDED_CALL_RESPONSE_MESSAGE("Cannot create code session for ended call"),
	HTTP_CODE_SESSION_MUTATE_FOR_ENDED_CALL_RESPONSE_MESSAGE("Cannot mutate code session for ended call"),
	HTTP_CODE_SESSION_USER_NOT_ACTIVE_CALL_PARTICIPANT_RESPONSE_MESSAGE("User is not active call participant"),
	HTTP_CODE_SESSION_EDIT_DENIED_RESPONSE_MESSAGE("Only call host or active code editor can edit code session"),
	HTTP_CODE_SESSION_MANAGE_DENIED_RESPONSE_MESSAGE("Only code session creator or call host can manage code session"),
	HTTP_CODE_SESSION_RUN_DENIED_RESPONSE_MESSAGE("Only call host or active code editor can run code session"),
	HTTP_CODE_SESSION_EDITOR_REQUIRED_RESPONSE_MESSAGE("Code session editor username is required"),
	HTTP_CODE_SESSION_EDITOR_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE("Code session editor must be active call participant"),
	HTTP_CODE_SESSION_LANGUAGE_REQUIRED_RESPONSE_MESSAGE("Code session language is required"),
	HTTP_CODE_SESSION_CODE_REQUIRED_RESPONSE_MESSAGE("Code session code is required"),
	HTTP_CODE_SESSION_STDIN_REQUIRED_RESPONSE_MESSAGE("Code session stdin is required"),
	HTTP_CODE_SESSION_CODE_TOO_LARGE_RESPONSE_MESSAGE("Code session code must not exceed 50000 characters"),
	HTTP_CODE_SESSION_STDIN_TOO_LARGE_RESPONSE_MESSAGE("Code session stdin must not exceed 20000 characters"),
	HTTP_CODE_SESSION_CURSOR_INVALID_RESPONSE_MESSAGE("Code session cursor position is invalid"),

	// S3
	HTTP_FILE_NOT_FOUND_RESPONSE_MESSAGE("file was not found"),

	// MessageReadStatus
	HTTP_MESSAGE_READ_STATUS_RESPONSE_MESSAGE("MessageReadStatus was not found"),

	// Canvas
	HTTP_CANVAS_USER_NOT_ACTIVE_CALL_PARTICIPANT_RESPONSE_MESSAGE("User is not active call participant"),
	HTTP_CANVAS_BOARD_NOT_FOUND_RESPONSE_MESSAGE("Canvas board not found"),
	HTTP_CANVAS_BOARD_CLOSED_RESPONSE_MESSAGE("Canvas board is closed"),
	HTTP_CANVAS_BOARD_MODE_AND_BACKGROUND_REQUIRED_RESPONSE_MESSAGE("Canvas board mode and background are required"),
	HTTP_CANVAS_SCREEN_OVERLAY_TRANSPARENT_BACKGROUND_REQUIRED_RESPONSE_MESSAGE("SCREEN_OVERLAY board must use TRANSPARENT background"),
	HTTP_CANVAS_WHITEBOARD_TRANSPARENT_BACKGROUND_NOT_ALLOWED_RESPONSE_MESSAGE("WHITEBOARD board must use WHITE or BLACK background"),
	HTTP_CANVAS_OVERLAY_OWNER_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE("Canvas overlay owner must be active call participant"),
	HTTP_CANVAS_DRAW_EVENT_TYPE_REQUIRED_RESPONSE_MESSAGE("Canvas draw event type is required"),
	HTTP_CANVAS_STROKE_ID_REQUIRED_RESPONSE_MESSAGE("Canvas strokeId is required"),
	HTTP_CANVAS_POINT_COORDINATES_REQUIRED_RESPONSE_MESSAGE("Canvas point coordinates are required"),
	HTTP_CANVAS_COORDINATES_NOT_NORMALIZED_RESPONSE_MESSAGE("Canvas coordinates must be normalized between 0.0 and 1.0"),
	HTTP_CANVAS_STROKE_COLOR_REQUIRED_RESPONSE_MESSAGE("Canvas stroke color is required"),
	HTTP_CANVAS_STROKE_WIDTH_INVALID_RESPONSE_MESSAGE("Canvas stroke width must be between 1 and 64"),
	HTTP_CANVAS_TOOL_REQUIRED_RESPONSE_MESSAGE("Canvas tool is required"),
	HTTP_CANVAS_CREATE_FOR_ENDED_CALL_RESPONSE_MESSAGE("Cannot create canvas board for ended call"),
	HTTP_CANVAS_MUTATE_FOR_ENDED_CALL_RESPONSE_MESSAGE("Cannot mutate canvas board for ended call"),
	HTTP_CANVAS_UNSUPPORTED_TRANSIENT_EVENT_RESPONSE_MESSAGE("Unsupported transient canvas event"),
	HTTP_CANVAS_UNSUPPORTED_PERSISTENT_EVENT_RESPONSE_MESSAGE("Unsupported persistent canvas event"),
	HTTP_CANVAS_STROKE_ALREADY_EXISTS_RESPONSE_MESSAGE("Canvas stroke already exists"),
	HTTP_CANVAS_BOARD_STROKE_LIMIT_EXCEEDED_RESPONSE_MESSAGE("Canvas board stroke limit exceeded"),
	HTTP_CANVAS_STROKE_NOT_FOUND_RESPONSE_MESSAGE("Canvas stroke not found"),
	HTTP_CANVAS_STROKE_ALREADY_ENDED_RESPONSE_MESSAGE("Cannot add points to ended canvas stroke"),
	HTTP_CANVAS_STROKE_POINT_LIMIT_EXCEEDED_RESPONSE_MESSAGE("Canvas stroke point limit exceeded"),
	HTTP_CANVAS_BOARD_MANAGE_DENIED_RESPONSE_MESSAGE("Only board creator or call host can manage board"),
	HTTP_CANVAS_DRAWING_ACCESS_REQUIRED_RESPONSE_MESSAGE("Canvas drawing access is required"),
	HTTP_CANVAS_SELECTED_DRAWER_REQUIRED_RESPONSE_MESSAGE("Selected drawer username is required"),
	HTTP_CANVAS_SELECTED_DRAWER_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE("Selected drawer must be active call participant"),
	HTTP_CANVAS_DRAW_DENIED_RESPONSE_MESSAGE("User is not allowed to draw on this canvas board"),
	HTTP_CANVAS_STROKE_OWNER_REQUIRED_RESPONSE_MESSAGE("Only stroke owner can modify this canvas stroke"),
	HTTP_CANVAS_TEMPLATE_REQUIRED_RESPONSE_MESSAGE("Canvas template type is required"),
	HTTP_CANVAS_REACTION_REQUIRED_RESPONSE_MESSAGE("Canvas reaction is required"),
	HTTP_CANVAS_TIMER_DURATION_INVALID_RESPONSE_MESSAGE("Canvas timer duration must be between 10 and 7200 seconds"),
	HTTP_CANVAS_STICKY_NOTE_NOT_FOUND_RESPONSE_MESSAGE("Canvas sticky note not found"),
	HTTP_CANVAS_STICKY_NOTE_KEY_REQUIRED_RESPONSE_MESSAGE("Canvas sticky note key is required"),
	HTTP_CANVAS_STICKY_NOTE_KEY_ALREADY_EXISTS_RESPONSE_MESSAGE("Canvas sticky note key already exists"),
	HTTP_CANVAS_STICKY_NOTE_TEXT_INVALID_RESPONSE_MESSAGE("Canvas sticky note text must be between 1 and 1000 characters"),
	HTTP_CANVAS_STICKY_NOTE_COLOR_REQUIRED_RESPONSE_MESSAGE("Canvas sticky note color is required"),
	HTTP_CANVAS_STICKY_NOTE_SIZE_INVALID_RESPONSE_MESSAGE("Canvas sticky note width and height must be normalized between 0.0 and 1.0"),
	HTTP_CANVAS_STICKY_NOTE_DELETE_DENIED_RESPONSE_MESSAGE("Only note creator, board creator or call host can delete sticky note"),
	HTTP_CANVAS_BACKGROUND_FILE_REQUIRED_RESPONSE_MESSAGE("Canvas background image file is required"),
	HTTP_CANVAS_BACKGROUND_FILE_TYPE_INVALID_RESPONSE_MESSAGE("Canvas background image must be PNG or JPEG"),
	HTTP_CANVAS_BACKGROUND_FILE_SIZE_INVALID_RESPONSE_MESSAGE("Canvas background image size must not exceed 5 MB"),
	HTTP_CANVAS_BACKGROUND_UPLOAD_FAILED_RESPONSE_MESSAGE("Failed to upload canvas background image"),
	HTTP_CANVAS_PRESENTER_REQUIRED_RESPONSE_MESSAGE("Canvas presenter username is required"),
	HTTP_CANVAS_PRESENTER_NOT_ACTIVE_PARTICIPANT_RESPONSE_MESSAGE("Canvas presenter must be active call participant"),
	HTTP_CANVAS_VIEWPORT_PRESENTER_REQUIRED_RESPONSE_MESSAGE("Only current canvas presenter can broadcast viewport changes"),
	HTTP_CANVAS_VIEWPORT_ZOOM_INVALID_RESPONSE_MESSAGE("Canvas viewport zoom must be between 0.25 and 4.0");

	private final String message;

	HttpResponseMessage(String message) {
		this.message = message;
	}
}
