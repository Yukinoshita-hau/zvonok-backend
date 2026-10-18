# Legacy Server/Channel: архивирование и последующая очистка

Это план отдельной согласованной операции. Stage 9 не выполняет миграцию, удаление строк,
таблиц или S3 objects. SQL ниже предназначен только для read-only инвентаризации.

## 1. Проверка фактической схемы

На отдельной согласованной копии БД получить schema-only dump, владельцев, grants,
sequences, triggers, views и зависимости. Сопоставить их с картой JPA в отчёте Stage 9.
Нельзя считать JPA source полным описанием deployed PostgreSQL.

```sql
BEGIN READ ONLY;
SELECT n.nspname, c.relname, c.relkind
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relname IN ('server','server_member','server_role','server_member_role','server_ban',
 'channel_folder','channel','channel_permission_override','folder_permission_override',
 'message','message_attachment','message_read_status','room','user');

SELECT conrelid::regclass AS source, confrelid::regclass AS target,
       conname, contype, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid IN (SELECT oid FROM pg_class WHERE relname IN
 ('server','server_member','server_role','server_member_role','server_ban','channel_folder',
  'channel','channel_permission_override','folder_permission_override','message',
  'message_attachment','message_read_status','room','user'))
OR confrelid IN (SELECT oid FROM pg_class WHERE relname IN
 ('server','server_member','server_role','server_member_role','server_ban','channel_folder',
  'channel','channel_permission_override','folder_permission_override','message'));

SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes WHERE tablename IN
 ('server','server_member','server_role','server_member_role','server_ban','channel_folder',
  'channel','channel_permission_override','folder_permission_override','message',
  'message_attachment','message_read_status');

SELECT count(*) FILTER (WHERE channel_id IS NOT NULL AND room_id IS NULL) AS archived,
       count(*) FILTER (WHERE channel_id IS NOT NULL AND room_id IS NOT NULL) AS ambiguous,
       count(*) FILTER (WHERE channel_id IS NULL AND room_id IS NULL) AS orphaned
FROM message;
ROLLBACK;
```

Проверить все входящие FK, фактические ON DELETE/UPDATE и триггеры, ссылки reply IDs,
read receipts и attachments, а также ссылки на legacy сообщения из room preview/read state.
Аномалии не исправлять автоматической конвертацией в DM/GROUP.

## 2. Архив

Согласовать retention, доступ к архиву и запрет concurrent legacy writers. Сделать
проверяемый согласованный backup схемы и данных: девять legacy таблиц плюс связанные
channel messages, attachments, read statuses и необходимые user identity references.
Сохранить counts, ID ranges, контрольные суммы, ограничения и manifest связей.
Не экспортировать passwords/JWT/refresh tokens в прикладной архив без необходимости.
Восстановить backup в изолированной БД и сверить manifest до любых удалений.

Составить S3 manifest: message_attachment.storage_key, server.banner_url и любые
другие фактические URL-колонки. В текущей MessageAttachment нет отдельного thumbnail key.
Проверить общие ссылки на каждый key (включая DM/GROUP, user/room avatars). При необходимости
согласовать retention/versioning/copy в отдельное защищённое хранилище. Никакого удаления S3 в Stage 9.

## 3. Отдельная физическая очистка

Только после согласования, restore drill, проверки retention и полного dependency graph
подготовить новую reviewable миграцию с dry run, оценкой locks, backup/PITR и rollback plan.
Сначала учесть входящие references, затем дочерние данные/joins/overrides/roles/members,
messages и родителей по фактическим FK. Не использовать слепой CASCADE.
Общий message, users, rooms, attachment/read-state tables не удалять. Перед изменением
message.channel_id проверить исторические constraints и все оставшиеся writers/readers.
SQL Stage 6 не переписывать. Реальную миграцию этот документ не содержит.

## 4. Hibernate и выпуск

Удаление JPA entities при ddl-auto=update не означает удаление физических таблиц.
Stage 9 проверяет update boot на disposable PostgreSQL с legacy tables/rows/FK/index.
В локальном application.properties default update; пример также update. Environment override
может изменить режим, поэтому перед выпуском отдельно проверить эффективное значение;
на существующей БД не применять create/create-drop/drop. После управления схемой вне ORM
предпочтителен validate/none и runtime DB role без DDL, с отдельным согласованием эксплуатации.
Само приложение с пользовательским datasource в Stage 9 не запускается.
