package com.zvonok.documentation;

public final class RoomApiDescriptions {
	// 2xx Success
	public static final String ROOM_GET_SUCCESS = "Успешное получение комнаты по идентификатору";

	public static final String ROOM_UPDATE_SUCCESS = "Успешное обновление комнаты";

	public static final String ROOM_DELETE_SUCCESS = "Успешное удаление комнаты";

	public static final String ROOM_GET_PRIVATE_MESSAGES_SUCCESS = "Успешное получение приватных сообщений";
	// 4xx Client Errors
	public static final String ROOM_NOT_FOUND = "Комната не найдена";

	private RoomApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
