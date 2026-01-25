package com.zvonok.documentation;

public final class AuthApiDescriptions {

	// 2xx Success
	public static final String AUTH_REGISTER_SUCCESS =
			"Пользователь успешно зарегистрирован и авторизован";

	public static final String AUTH_LOGIN_SUCCESS = "Аутентификация выполнена успешно";

	public static final String AUTH_REFRESH_SUCCESS = "Токены успешно обновлены";

	public static final String AUTH_LOGOUT_SUCCESS = "Выход выполнен успешно";

	public static final String AUTH_ME_SUCCESS = "Данные текущего пользователя успешно получены";

	// 4xx Client Errors
	public static final String AUTH_USER_ALREADY_EXISTS =
			"Пользователь с указанным email или username уже существует";

	public static final String AUTH_INVALID_CREDENTIALS =
			"Неверные учётные данные";

	public static final String AUTH_REFRESH_TOKEN_REVOKED_OR_EXPIRED =
			"Refresh-токен недействителен, отозван или истёк";


	private AuthApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
