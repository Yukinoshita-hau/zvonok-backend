package com.zvonok.documentation;

public final class ChannelApiDescriptions {
	// 2xx Success
	public static final String CHANNEL_GET_SUCCESS = "Успешное получение каналов";	

	public static final String CHANNEl_CREATE_SUCCESS = "Успешное создание сервера";	

	public static final String CHANNEL_DELETE_SUCCESS = "Успешное удаление сервера";

	public static final String CHANNEL_UPDATE_SUCCESS = "Успешное обновление сервера";
	// 4xx Client Errors


	private ChannelApiDescriptions() {
		throw new AssertionError("No instances");
	}
}
