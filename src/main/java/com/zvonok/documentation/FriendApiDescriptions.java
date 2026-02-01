package com.zvonok.documentation;

public final class FriendApiDescriptions {
	// 2xx Success
	public static final String FRIEND_GET_LIST_SUCCESS = "Успешное получение списка друзей";

	public static final String FRIEND_GET_INCOMING_REQUESTS_SUCCESS = "Успешное получение списка входящих заявок в друзья";

	public static final String FRIEND_GET_OUTGOING_REQUESTS_SUCCESS = "Успешное получение списка исходящих заявок в друзья";

	public static final String FRIEND_SEND_REQUEST_SUCCESS = "Успешная отправка запроса в друзья";

	public static final String FRIEND_ACCEPT_REQUEST_SUCCESS = "Успешное принятие запроса в друзья";

	public static final String FRIEND_REJECT_REQUEST_SUCCESS = "Успешное отклонение запроса в друзья";

	public static final String FRIEND_CANCEL_REQUEST_SUCCESS = "Успешная отмена запроса в друзья";

	public static final String FRIEND_DELETE_SUCCESS = "Успешное удаление друга";

	// 4xx Client Errors
	public static final String FRIEND_REQUEST_NOT_FOUND = "Запрос в друзья не найден";

	public static final String FRIEND_REQUEST_ALREADY_EXIST = "Запрос в друзья уже отправлен";

	public static final String FRIEND_FRIENDSHIP_NOT_FOUND = "Друг не найден";


	private FriendApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
