package com.zvonok.logging;

public class LogEventConstants {
	public static final String EVENT_SUCCES_RESULT = "SUCCESS";
	public static final String EVENT_FAILED_RESULT = "FAILED";

	// Actions
	public static final String EVENT_REGISTERED_ACTION = "USER_REGISTERED";
	public static final String EVENT_LOGIN_ACITON = "USER_LOGIN";

	public static final String EVENT_CREATE_SERVER_ACTION = "CREATE_SERVER";
	public static final String EVENT_UPDATE_SERVER_ACTION = "UPDATE_SERVER";
	public static final String EVENT_DELETE_SERVER_ACTION = "DELETE_SERVER";
	public static final String EVENT_JOIN_BY_INVITE_CODE_ACTION = "JOIN_SERVER_BY_INVITE_CODE";
	public static final String EVENT_LEAVE_SERVER_ACION = "LEAVE_SERVER";
	public static final String EVENT_KICK_MEMBER_ACTION = "KICK_MEMBER";

	public static final String EVENT_SEND_PRIVATE_MESSAGE_ACTION = "SEND_PRIVATE_MESSAGE";
	public static final String EVENT_SEND_GROUP_MESSAGE_ACTION = "SEND_GROUP_MESSAGE";
	public static final String EVENT_SEND_CHANNEL_MESSAGE_ACTION = "SEND_CHANNEL_MESSAGE";
	public static final String EVENT_UPDATE_MESSAGE_ACTION = "UPDATE_MESSAGE";
	public static final String EVENT_DELETE_MESSAGE_ACTION = "DELETE_MESSAGE";

	public static final String EVENT_CHECK_SERVER_PERMISSION_ACTION = "CHECK_SERVER_PERMISSION";
	public static final String EVENT_CHECK_CHANNEL_PERMISSION_ACTION = "CHECK_CHANNEL_PERMISSION";
	public static final String EVENT_CHECK_FOLDER_PERMISSION_ACTION = "CHECK_FOLDER_PERMISSION";
}
