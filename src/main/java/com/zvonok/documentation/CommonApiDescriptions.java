package com.zvonok.documentation;

public final class CommonApiDescriptions {
	
	public static final String VALIDATION_FAILED = "Ошибка валидности входных данных";

	public static final String AUTOCEIFICATION_FAILED = "Требуется аутентификация / не предоставлены корректные учетные данные";

	public static final String NOT_ENOUGH_RIGHTS = "Недостаточно прав для выполнения операции";

	private CommonApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
