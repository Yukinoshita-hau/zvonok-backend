# Zvonok Core Cleanup — Stage 4

Дата: 2026-10-17 (Asia/Yekaterinburg)

## Результат

Canvas/Whiteboard, Code Sessions и связанный запуск кода удалены из основного приложения. Удалены UI, Redux/API/WebSocket интеграции, backend REST/STOMP handlers, сервисы, модели и конфигурация Code Runner. Удалённые REST paths теперь не имеют handler, а прежние STOMP destinations отклоняются существующей deny-by-default политикой.

Сохранены изменения предыдущих этапов, включая S01/S02/S03/S04/S05/F01/F02/F03, за исключением кода Canvas, который по этому запросу удалён целиком. Общие WebSocket/JWT/room/channel/message/LiveKit/S3 проверки оставлены. `.factorypath` не менялся этим этапом; он уже был изменён до начала Stage 4. Коммиты, миграции, production операции и изменения `zvonok-code-runner` не выполнялись.

## 1. Инвентаризация зависимостей

### Canvas / Whiteboard

* Backend: `CanvasBoardController`, `WSCanvasBoardController`, `CanvasBoardService`, `CanvasBoardRepository`, `CanvasStrokeRepository`, `CanvasPointRepository`, `CanvasStickyNoteRepository`, `CanvasNoteVoteRepository`.
* JPA и модели: `CanvasBoard`, `CanvasStroke`, `CanvasPoint`, `CanvasStickyNote`, `CanvasNoteVote`; перечисления `CanvasBackground`, `CanvasBoardMode`, `CanvasBoardObjectEventType`, `CanvasDrawEventType`, `CanvasDrawingAccess`, `CanvasReactionType`, `CanvasTemplateType`, `CanvasTimerStatus`, `CanvasTool`.
* DTO/ошибки: board/snapshot/draw/stroke/point/note/vote DTO и запросы создания/изменения/таймера/presenter; Canvas-specific exceptions и записи в `HttpResponseMessage`.
* HTTP: `/calls/{callId}/boards` и вложенные ресурсы board, snapshot, permissions, template, timer, notes, votes, background image, presenter.
* STOMP: SEND `/app/calls/{callId}/boards/{boardId}/draw`; SUBSCRIBE `/topic/calls/{callId}/boards`, `/topic/calls/{callId}/boards/{boardId}`.
* S3: `S3ObjectAccessService` искал фоновые изображения через `CanvasBoardRepository` и разрешал чтение только участникам активного звонка. Файлы удалены не были.
* Frontend: `CallCanvas`, `ScreenShareOverlayCanvas`, `InteractiveHost`, `CallInteractiveMenu`, Canvas API и DTO, Redux slice/selectors, Canvas middleware handlers/actions/path builders, `useCanvasDrawing`, permission/render/throttle helpers, кнопки/меню звонка, шаблоны/стили и whiteboard tiles.

### Code Sessions / исполнение кода

* Backend: `CodeSessionController`, `WSCodeSessionController`, `CodeExecutionController`, `CodeSessionService`, `CodeExecutionService`, `CodeRunnerClient`, `CodeSessionRepository`, `CodeRunnerProperties`, CodeSession entity/enum/DTO, code execution DTO и exceptions.
* HTTP: `/code-sessions` (create, active, grant/revoke editor, run, close); `/code-runs/languages` и POST `/code-runs`.
* STOMP: SEND `/app/code-sessions/{sessionId}/{content-sync,stdin-sync,language-change,cursor-sync}`; SUBSCRIBE `/topic/calls/{callId}/code-sessions` и `/topic/code-sessions/{sessionId}`.
* Frontend: `CodeSession` components/hooks, Monaco editor, cursor/content/stdin/language synchronization, APIs/types, Redux slice/actions, WebSocket publishers/subscriptions/reconnect restore, Code Session controls.
* Зависимости: в `package.json` удалены прямые `@monaco-editor/react` и `monaco-editor` (npm удалил 5 пакетов из lockfile). Отдельных Code Runner зависимостей в `pom.xml` не было; конфигурация backend URL/API key удалена из `application.properties` и example-файла.

## 2. Удалённые компоненты

Удалены все перечисленные выше feature-specific backend/frontend controllers, services, repositories, entities, DTO, enums, exceptions, APIs, hooks, components, types, store slices/selectors и UI controls. Также удалён backend `CodeExecutionServiceTest`; его проверяемая функциональность больше не имеет вызываемого endpoint или потребителя.

В `CallUi` удалены whiteboard/editor focus branches и интерактивное меню. Удалена разметка screen-share поверх изображения. LiveKit screen sharing, захват/публикация экрана, просмотр видеотрека, cinema mode, аудио демонстрации экрана и обычный фокус видеотреков сохранены.

## 3. Изменённые общие компоненты и сохранённые части

* `WebSocketAccessService`: удалены только Canvas/Code Session route patterns и repository checks. Другие SEND destinations, room/channel ACL и персональные очереди сохранены. Любой неизвестный SEND/SUBSCRIBE по-прежнему запрещён.
* `SecurityConfig`: удалены специальные authenticated matchers для `/code-runs/**` и `/code-sessions/**`; общий `anyRequest().authenticated()` сохранён. Удалённые handlers не зарегистрированы.
* `S3ObjectAccessService`: удалена Canvas background ветка и её repository зависимости. Проверки вложений через `MessageAccessService`, публичных текущих avatar URL и активных room avatar URL сохранены. Неизвестные ключи по-прежнему закрываются; `message-attachments/` остаётся явно закрытым при отсутствии доменной ссылки. Объекты S3 не удалялись.
* Frontend reconnect: обычные room/message/friend/call resync и `restoreCallSession` сохранены. Canvas board/snapshot и Code Session fetch/subscribe replay удалены. Logout по-прежнему закрывает сокет и очищает данные.
* Сохранены `WebSocketAccessService`, `WebSocketDeliveryInterceptor`, `WebSocketAuthorizationSessions`, `MessageAccessService`, `S3ObjectAccessService`, JWT/logout invalidation, LiveKit authorization, channel/room/message ACL, вложения и S3 avatar/room доступ.
* `CallUi`, screen sharing, LiveKit `useChat`, CallAudioLayer/processors/RNNoise, управление устройствами, звонки/восстановление звонков, конференции, DM/GROUP messages и Electron не перепроектировались.

## 4. Удалённые REST и STOMP интерфейсы

### REST

* Canvas: все методы `/calls/{callId}/boards` и вложенных `/{boardId}` ресурсов: snapshot, close, clear, undo, permissions, template, timer start/stop/reset, notes CRUD, vote/unvote, votes, background image, presenter.
* Code Sessions: POST `/code-sessions`; GET `/code-sessions/active`; POST `/{sessionId}/grant-editor`, `/revoke-editor`, `/run`, `/close`.
* Code Runner proxy: GET `/code-runs/languages`; POST `/code-runs`.

Тест с аутентифицированным пользователем подтверждает 404 для `/calls/40/boards`, `/code-sessions/active` и `/code-runs/languages`.

### STOMP

Удалены Canvas draw и четыре Code Session command handlers. WebSocket ACL tests подтверждают отказ для бывших Canvas/Code Session topics и SEND destinations. Существующие `/app/chat`, `/app/call`, `/app/friend`, `/topic/room.*`, `/topic/channel.*` и `/user/queue/*` правила остаются.

## 5. WebSocket reconnect и S3

После reconnect клиент по-прежнему восстанавливает сообщения комнаты, друзей и звонок. Больше нет вызовов Canvas snapshot/boards или Code Session active endpoint; тест проверяет, что нет API запросов или подписок с такими destinations после reconnect.

Для S3 удалена только привязка к Canvas background. Avatar/room и message attachment доменные проверки сохранены. Существующие Canvas background объекты остаются в storage, но общий download больше не находит для них действующую доменную ссылку и отказывает. В тесте `AmazonS3` не вызывается для старого `canvas_board_1.png`.

## 6. Карта таблиц и сохранение данных

Карта ниже отражает связи в JPA entities; текущая production DB и её физические foreign keys напрямую не инспектировались.

| Таблица | Объявленные связи | Данные после Stage 4 |
|---|---|---|
| `canvas_board` | `call_session_id` → `CallSession`; `room_id` хранится скалярным полем | Entity удалена; таблица/строки не затрагивались |
| `canvas_stroke` | `board_id` → `canvas_board` | Entity удалена; таблица/строки не затрагивались |
| `canvas_point` | `stroke_id` → `canvas_stroke` | Entity удалена; таблица/строки не затрагивались |
| `canvas_sticky_note` | `board_id` → `canvas_board` | Entity удалена; таблица/строки не затрагивались |
| `canvas_note_vote` | `board_id` → `canvas_board`; `note_id` → `canvas_sticky_note`; `user_id` скалярный | Entity удалена; таблица/строки не затрагивались |
| `code_session` | `call_session_id` → `CallSession`; `active_editor_user_id` → `User`; `room_id` скалярный | Entity удалена; таблица/строки не затрагивались |

У сущностей `CallSession`, `Room`, `Message`, `User` не было JPA коллекций/cascade, ссылающихся на удалённые entities. Единственный показанный cascade ALL — от `CanvasStroke.points` к `CanvasPoint`; это не cascade от общей сущности. Удаление entity-классов не запускает удаление строк. `spring.jpa.hibernate.ddl-auto` по умолчанию `update`; настройка не менялась. Stage 4 не запускал schema migration и не подключался к существующей БД.

Будущая отдельная миграция должна: (1) сделать backup/архив строк и ключей фоновых объектов; (2) проверить реальные FK/индексы и объёмы на целевой БД; (3) сверить отсутствие внешних потребителей и требования хранения; (4) выполнить явно утверждённую миграцию сначала в staging, с проверкой restore; (5) физически удалять таблицы только отдельным согласованным изменением. До этого таблицы и данные остаются на месте.

## 7. Code Runner и deployment

В `zvonok-backend/docker-compose.yml` есть только PostgreSQL и основной backend; runner там не запускается. Отдельный `zvonok-code-runner/docker-compose.yaml` публикует `${SERVER_PORT}:${SERVER_PORT}`, монтирует `/var/run/docker.sock` и workspace volume. В исходниках runner запросы `/internal/*` требуют `X-Internal-Api-Key`.

Репозиторий runner не менялся, сервисы не останавливались, volumes/workspace data не удалялись. Факт текущего production запуска, внешний firewall/bind и возможная публикация реального `INTERNAL_API_KEY` локально не проверялись. Рекомендация для владельца deployment: убрать host/public port mapping или ограничить его private network/firewall, остановить runner только по отдельному согласованию после проверки зависимостей и ротировать `INTERNAL_API_KEY`, если ключ был опубликован или разделялся с основным backend.

## 8. Проверки и сравнение baseline

| Проверка | Результат |
|---|---|
| Backend compile (`mvn -o -DskipTests compile`, Java 21) | SUCCESS; 330 production source files |
| Backend targeted security (`WebSocketAclSecurityTest`, `StorageHttpSecurityTest`) | SUCCESS; 80/80 |
| Полный backend suite | FAILURE из-за 14 прежних проблем в `ChannelControllerTest`/`CallSessionServiceTest`; 212 тестовых случаев, 4 assertion failures + 10 errors |
| Frontend unit tests | SUCCESS; 28/28 в 4 файлах |
| `npm run build:web` | SUCCESS; 2,019 modules; обычное предупреждение bundle >500 kB |
| `npx tsc -b --pretty false` | FAILURE из-за существующих diagnostics вне Stage 4 файлов; 70 diagnostic records; затронутые Stage 4 файлы без оставшихся diagnostics |
| `npm run build:electron` | SUCCESS; сгенерированные `electron-dist/main.js`/`preload.js` возвращены к исходным чистым версиям после проверки |
| `git diff --check` | SUCCESS в обоих рабочих репозиториях |

Frontend baseline в запросе: 29/29 тестов, 82 TypeScript diagnostics, web/electron builds successful. После Stage 4: 28 тестов (два старых Canvas/Code reconnect expectations заменены одним cleanup regression test), 70 TypeScript diagnostic records, web/electron builds successful. Снижение diagnostics включает удалённые feature diagnostics; новые ошибки в изменённых файлах не обнаружены.

Backend baseline: 204/218, включая 14 прежних падений/ошибок. После удаления feature-only tests и замены acceptance ACL cases общий набор содержит 212 случаев и те же 14 известных проблем. В Stage 4 добавлены проверки отсутствия REST handlers, отказа по удалённым STOMP routes и отсутствия feature reconnect subscriptions. Тесты не отключались для получения зелёного результата.

## 9. Работоспособность ядра и оставшиеся границы

Backend targeted security покрывает JWT/WebSocket ACL, запрет broker injection, logout invalidation, message/storage access, аватары, вложения и LiveKit token policy. Frontend tests покрывают reconnect, историю сообщений, logout, token refresh, сохранение текущего LiveKit состояния при signalling reconnect и отсутствие feature subscriptions. Web build подтверждает отсутствие битых frontend imports.

Реальный login/logout, DM/GROUP с двумя клиентами, реальное S3, browser screen sharing и медиазвонок к LiveKit в Stage 4 не запускались; медиасценарии не объявляются проверенными. Полный backend suite остаётся с 14 ранее известными ошибками. Полный TypeScript check остаётся неуспешным из-за существующих ошибок, включая RNNoise/audio, CallQuality, Channel/Room и legacy entity modules. Runner остаётся отдельным deployment риском, пока владелец окружения не проверит реальную экспозицию и ключ.

Stage 5 не начинался.
