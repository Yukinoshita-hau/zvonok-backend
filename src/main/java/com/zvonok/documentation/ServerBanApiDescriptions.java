package com.zvonok.documentation;

public final class ServerBanApiDescriptions {
	// 2xx Success
	public static final String SERVER_BAN_GET_LIST_SUCCESS = "Успешное получение списка заблокированных пользователей";	

	public static final String SERVER_BAN_USER_SUCCES = "Успешная блокировка пользователя";	

	public static final String SERVER_UNBAN_USER_SUCCES = "Успешная разблокировака пользователя";	
	// 4xx Client Errors


	private ServerBanApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
