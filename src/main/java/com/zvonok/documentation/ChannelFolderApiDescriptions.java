package com.zvonok.documentation;

public final class ChannelFolderApiDescriptions {
	// 2xx Success

	public static final String CHANNEL_FOLDER_GET_ALL_FOLDER_SUCCESS =
			"Список папок каналов сервера успешно получен.";

	public static final String CHANNEL_FOLDER_CREATE_FOLDER_SUCCESS =
			"Папка каналов успешно создана.";

	public static final String CHANNEL_FOLDER_UPDATE_FOLDER_SUCCESS =
			"Папка каналов успешно обновлена.";

	public static final String CHANNEL_FOLDER_DELETE_FOLDER_SUCCESS =
			"Папка каналов успешно удалена.";

	// 4xx Client Errors

	private ChannelFolderApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
