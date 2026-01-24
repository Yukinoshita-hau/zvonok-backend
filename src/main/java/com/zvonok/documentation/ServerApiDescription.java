package com.zvonok.documentation;

public final class ServerApiDescription {
	// 2xx Success
	public static final String SERVER_GET_SUCCESS = "Успешное получение данных об сервере";

	public static final String SERVER_UPDATE_SUCCESS = "Успешное обновление данных сервера";

	public static final String SERVER_DELETE_SUCCESS = "Успешное удаление сервера";

	public static final String SERVER_REGENERATE_INVITE_CODE_SUCCESS = "Успешная регенерация кода приглашения";

	public static final String SERVER_LEAVE_SUCCESS = "Успешное покидание сервера";

	public static final String SERVER_JOIN_BY_INVITE_CODE_SUCCESS = "Успешное присоединение к серверу по коду приглашения";

	public static final String SERVER_CREATE_SUCCESS = "Успешное создание сервера";

	public static final String SEVER_GET_MEMBERS_SUCCESS = "Успешная выдача списка участников сервера";

	public static final String SERVER_GET_MY_SUCCESS = "Успещное получение списка серверов пользователя"; 

	public static final String SERVER_KICK_MEMBER_SUCCESS = "Успешно выгнон пользователь";

	// 4xx Client Errors
	public static final String SERVER_NOT_FOUND = "Сервер небыл найден или не существует";

	public static final String SERVER_NOT_ENOUGH_RIGHTS = "Недостаточно прав для выполнения действий над сервером";

	public static final String SERVER_MEMBER_ALREADY_KICKED = "Пользователь уже покинул сервер";
}
