package com.zvonok.service.dto;

import lombok.Getter;

@Getter
public enum Permission {
    // Базовые разрешения
    NOTHING(0L),             // Ничего
    VIEW_CHANNEL(1L << 0),         // Видеть канал
    SEND_MESSAGES(1L << 1),        // Отправлять сообщения
    READ_MESSAGE_HISTORY(1L << 2), // Читать историю сообщений

    // Форматирование сообщений
    EMBED_LINKS(1L << 3),          // Вставлять ссылки
    ATTACH_FILES(1L << 4),         // Прикреплять файлы

    // Редактирование сообщений
    EDIT_MESSAGES(1L << 5),        // Редактировать чужие сообщения

    // Голосовые каналы
    CONNECT(1L << 6),             // Подключаться к голосовым каналам
    SPEAK(1L << 7),               // Говорить в голосовых каналах
    MUTE_MEMBERS(1L << 8),        // Заглушать участников
    DEAFEN_MEMBERS(1L << 9),     // Оглушать участников
    MOVE_MEMBERS(1L << 10),       // Перемещать участников между каналами

    // Управление сообщениями
    MANAGE_MESSAGES(1L << 11),    // Управлять сообщениями (удалять чужие)

    // Управление каналами и папками
    MANAGE_CHANNELS(1L << 12),    // Управлять каналами (создавать, редактировать, удалять)
    MANAGE_PERMISSIONS(1L << 13), // Управлять разрешениями канала

    // Управление сервером
    CHANGE_NICKNAME(1L << 14),    // Изменять свой никнейм
    MANAGE_NICKNAMES(1L << 15),   // Изменять никнеймы других участников
    KICK_MEMBERS(1L << 16),       // Исключать участников
    BAN_MEMBERS(1L << 17),        // Банить участников
    MANAGE_ROLES(1L << 18),       // Управлять ролями
    MANAGE_SERVER(1L << 19),      // Управлять сервером
    CREATE_INVITE(1L << 20),      // Создавать приглашения

    // Администраторские права
    ADMINISTRATOR(1L << 21);      // Полные права администратора

    private final long value;

    Permission(long value) {
        this.value = value;
    }

    // Проверить есть ли разрешение в битовой маске
    public static boolean hasPermission(Long permissions, Permission permission) {
        return (permissions & permission.getValue()) != 0;
    }

    // Добавить разрешение к битовой маске
    public static Long addPermission(Long permissions, Permission permission) {
        return permissions | permission.getValue();
    }

    // Удалить разрешение из битовой маски
    public static Long removePermission(Long permissions, Permission permission) {
        return permissions & ~permission.getValue();
    }
}
