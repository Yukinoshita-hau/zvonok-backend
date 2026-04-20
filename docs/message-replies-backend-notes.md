# Message replies backend notes

## 1) Модули и файлы reply support
- `Message` entity хранит `replyToMessageId` как nullable-ссылку на parent message (single-level).  
- WS create-flow для DM/room/channel идёт через `ChatController` -> `MessageService`.  
- История room/private и realtime payload room/DM собираются в `MessageService`.  
- История channel payload собирается в `ChannelService`; realtime channel payload в `MessageService`.

Ключевые файлы:
- `src/main/java/com/zvonok/model/Message.java`
- `src/main/java/com/zvonok/controller/ChatController.java`
- `src/main/java/com/zvonok/service/MessageService.java`
- `src/main/java/com/zvonok/service/ChannelService.java`
- `src/main/java/com/zvonok/controller/dto/ShortMessageWrapped.java`
- `src/main/java/com/zvonok/controller/dto/ChannelMessageResponse.java`
- `src/main/java/com/zvonok/controller/dto/MessageResponse.java`
- `src/main/java/com/zvonok/controller/dto/ReplyPreviewDto.java`
- `src/main/java/com/zvonok/controller/dto/WebSocketMessageRequest.java`

## 2) Как reply metadata проходит по flow

### Create flow (WebSocket)
1. Клиент шлёт payload в `/app/chat/private/{receiverUsername}`, `/app/chat/send/{roomId}` или `/app/chat/channel/{channelId}`.
2. `ChatController` поддерживает backward compatibility:
   - старый формат: plain string (без reply);
   - новый формат: object с `content` + `replyToMessageId` (или `parentMessageId` как alias).
3. `MessageService` валидирует parent:
   - parent существует;
   - parent не deleted;
   - parent в том же room/channel context.
4. Сообщение сохраняется с `replyToMessageId`.
5. В outgoing DTO добавляются:
   - `replyToMessageId`;
   - `replyPreview` (один уровень, compact).

### Fetch flow (REST history)
- Room history (`/rooms/{roomId}/messages`) и private history (`/rooms/private/{userId}/messages`) получают reply metadata через `MessageService`.
- Channel history (`.../channels/{channelId}/messages`) получает reply metadata через `ChannelService`.
- Для page/history используется batch-load parent сообщений (`findAllById`) для снижения N+1.

### WebSocket realtime flow
- `MESSAGE`, `MESSAGE_EDIT`, `MESSAGE_DELETE` для room/DM и channel сохраняют существующие event names/types.
- В payload добавляется `replyToMessageId` + `replyPreview`.
- Контракт не строит recursive parent/children tree.

## 3) Где хранится parentMessageId / replyToMessageId
- Хранится в `message.reply_to_message_id` через поле `Message.replyToMessageId` (nullable).
- Для старых сообщений значение `null` и они остаются валидными.

## 4) Ограничения текущей реализации
- Только single-level reference (одна ссылка на parent).
- Нет thread/discussion model.
- Preview parent возвращается компактно; полное родительское сообщение не сериализуется.
- При hard-missing parent (если запись недоступна) возвращается fallback preview c `deleted=true`.

## 5) Что безопасно менять / что рискованно

### Безопасно
- Добавлять поля внутрь `replyPreview` (backward-compatible, если nullable).
- Менять правила обрезки snippet (например, длину) без изменения event names.

### Рискованно
- Менять существующие event names (`MESSAGE`, `MESSAGE_EDIT`, `MESSAGE_DELETE`).
- Ломать dual-format WS input (string + object), так как это важно для backward compatibility.
- Добавлять eager/self-referential загрузку parent как entity relationship без контроля, можно получить лишнюю нагрузку.

## 6) Известные edge cases
- Обычное сообщение (без reply): `replyToMessageId = null`, `replyPreview = null`.
- Reply на reply: разрешён как обычная ссылка на одно parent сообщение.
- Reply на deleted parent: запрещён на момент создания.
- Parent удалён после создания replies: reply остаётся, preview показывает deleted state.
- Parent из другого room/channel: валидация возвращает `400`.
- Parent не существует: `404` (`MessageNotFoundException`).

## 7) Ручная проверка reply feature
1. Отправить обычное сообщение через WS string payload — должно работать как раньше.
2. Отправить reply через WS object payload с `replyToMessageId` в том же room/channel.
3. Получить history room/channel и проверить наличие `replyToMessageId` + `replyPreview`.
4. Попробовать reply на message из другого context — получить validation error.
5. Удалить parent message, затем:
   - убедиться, что reply не удалился;
   - убедиться, что preview у reply в deleted state.
6. Отредактировать parent и проверить, что его собственный `MESSAGE_EDIT` приходит корректно.

## 8) Assumptions / open questions
- **Assumption:** для создания сообщений приоритетно используется WebSocket flow (`ChatController`), REST create endpoint не используется.
- **Assumption:** `replyToMessageId` как naming уже принят в части контракта (channel DTO), поэтому оставлен как основной backend-field.
- **Open question:** нужен ли отдельный broadcast/update для уже существующих reply-сообщений при edit parent (сейчас обновление выполняется через стандартный edit event parent).
