package com.zvonok.documentation;

public final class ServerApiDescriptions {
	// 2xx Success
	public static final String SERVER_GET_SUCCESS = "Сервер успешно получен";

	public static final String SERVER_UPDATE_SUCCESS = "Сервер успешно обновлён";

	public static final String SERVER_DELETE_SUCCESS = "Сервер успешно удалён";

	public static final String SERVER_REGENERATE_INVITE_CODE_SUCCESS = "Код приглашения успешно обновлён";

	public static final String SERVER_LEAVE_SUCCESS = "Пользователь успешно покинул сервер";

	public static final String SERVER_JOIN_BY_INVITE_CODE_SUCCESS = "Пользователь успешно присоединён к серверу";

	public static final String SERVER_CREATE_SUCCESS = "Сервер успешно создан";

	public static final String SERVER_GET_MEMBERS_SUCCESS = "Список участников сервера успешно получен";

	public static final String SERVER_GET_MY_SUCCESS = "Список серверов пользователя успешно получен"; 

	public static final String SERVER_KICK_MEMBER_SUCCESS = "Участник успешно исключён с сервера";

	public static final String SERVER_UPDATE_MEMBER_NICKNAME_SUCCESS = "Никнейм участника успешно обновлён";

	// 4xx Client Errors
	public static final String SERVER_NOT_FOUND = "Сервер не найден";

	public static final String SERVER_OR_MEMBER_NOT_FOUND = "Сервер или участник не найдены";

	public static final String SERVER_NOT_ENOUGH_RIGHTS = "Недостаточно прав для выполнения операции";

	public static final String SERVER_MEMBER_ALREADY_KICKED = "Участник уже не является членом сервера";
}
