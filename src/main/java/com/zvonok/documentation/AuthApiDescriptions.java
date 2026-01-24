package com.zvonok.documentation;

public final class AuthApiDescriptions {

	// 2xx Success
	public static final String AUTH_REGISTER_SUCCESS =
			"Успешное создание пользователя и его авторизация";

	public static final String AUTH_LOGIN_SUCCESS = "Успешный вход в систему";

	public static final String AUTH_REFRESH_SUCCESS = "Успешная переавторизация";

	public static final String AUTH_LOGOUT_SUCCESS = "Успешная деавторизация";

	public static final String AUTH_ME_SUCCESS = "Успешная идентификация";

	// 4xx Client Errors
	public static final String AUTH_USER_ALREADY_EXISTS =
			"Пользователь с такими данными уже существует";

	public static final String AUTH_INVALID_CREDENTIALS =
			"Неверные учётные данные (логин или пароль)";
	public static final String AUTH_REFRESH_TOKEN_REVOKED_OR_EXPIRED =
			"Refresh токен отозван или просрочен";


	private AuthApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
