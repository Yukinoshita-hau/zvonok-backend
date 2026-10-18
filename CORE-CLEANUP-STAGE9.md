# Zvonok Core Cleanup — Stage 9

Дата: 2026-10-18, Asia/Yekaterinburg.

## Карта зависимостей до удаления

- ServerController/ServerRoleController/ServerMemberRoleController/ServerBanController → ServerService/ServerMemberService/ServerRoleService/ServerMemberRoleService/ServerBanService → соответствующие repositories/entities.
- ChannelFolderController → ChannelFolderService; ChannelController → ChannelService/MessageService → ChannelFolder/Channel/overrides/PermissionService.
- PermissionService → ServerMember/ServerRole/member-role + channel/folder overrides. InviteCodeService использует исключительно ServerRepository. Групповые приглашения независимы.
- WSChatController /chat/channel/{id} → MessageService.sendChannelMessage → /topic/channel.{id}. Общие edit/delete/read/attachment paths содержат channel branches.
- MessageAccessService → PermissionService для channel, RoomAccessService для DM/GROUP; WebSocketAccessService содержит channel allowlist. S3ObjectAccessService → MessageAccessService.
- Message.channel → channel; User.serverMemberships → server_member. Room/CallParticipant независимы. LiveKit serverUrl, STOMP transport channels, audio channelCount, BroadcastChannel сохраняются.
- Frontend App routes → ServerLayout/MyServers/ChannelChat → ChannelFolder/ChannelItemsList/MyServersCard/ServerButton/ServerIcon → server/channel API/types/slices. AppLayout fetchMyServers; NavigateBar server navigation; Redux server/channelMessage; publishActions channel subscribe/send.

## Исходное состояние и границы

Прочитаны отчёты Stage 2–8, SECURITY-P0, AGENTS.md. Проверены status и полный tracked diff обоих репозиториев; точные baseline diff/status сохранены локально в stage9-baseline. Изменения Stage 1–8 уже присутствуют и сохраняются. Упомянутого в AGENTS каталога agent/code-style нет. Карта составлена до удаления.
Production/пользовательская БД и S3 не используются. .factorypath и SQL Stage 6 не меняются, коммиты не создаются. Hub/Stage 10 не создаются.

## PostgreSQL: исходная карта по JPA

Таблицы: server, server_member, server_role, server_member_role, server_ban, channel_folder, channel, channel_permission_override, folder_permission_override.
FK: server.owner_id→user; server_member.user_id→user/server_id→server; server_role.server_id→server; server_member_role.member_id→server_member/role_id→server_role/assigned_by→user; server_ban.server_id→server/user_id,banned_by,unbanned_by→user; channel_folder.server_id→server; channel.channel_folder_id→channel_folder; channel_permission_override.channel_id→channel/role_id→server_role/user_id→user; folder_permission_override.folder_id→channel_folder/role_id→server_role/user_id→user; message.channel_id→channel.
Общие message_attachment.message_id→message и message_read_status.message_id→message сохраняются. reply_to_message_id является scalar reference. Room messages используют message.room_id. Конвертации данных не будет.
Constraints/indexes: PK id; server.invited_code UNIQUE NOT NULL; owner_id и mandatory FK NOT NULL; override XOR проверяется Java, не явным SQL constraint. Legacy @Index нет. Имена deployed FK, дополнительные indexes/constraints и ON DELETE требуют отдельной read-only catalog-инвентаризации; подключения к существующей БД в этом этапе нет.
JPA cascade: server→members/folders/roles REMOVE/PERSIST + orphanRemoval; folder→channels/overrides REMOVE/PERSIST/MERGE; role→memberRoles/folderOverrides/channelOverrides REMOVE/PERSIST/MERGE; member→memberRoles REMOVE/PERSIST/MERGE. User.serverMemberships без cascade. Message→attachments ALL/orphanRemoval сохраняется. Legacy rows должны быть закрыты для write paths.
S3 references: server.bannerUrl, user/room avatarUrl, message_attachment.storage_key (отдельного thumbnail key в текущей модели нет). Объекты сохраняются. Исходные entity declarations сохранены в stage9-baseline/legacy-sources.md.

## Изменения backend

Удалено 79 production Java files: 6 REST controllers, 9 JPA entities, legacy enum/permission policies, services, repositories, DTO, exceptions и OpenAPI descriptions. Полный список ниже. Также удалены legacy fields/error constants/log identifiers. Общие InsufficientPermissionsException, application security, transport JwtChannelInterceptor и MessageChannel сохранены.

Message теперь имеет только Room association и room target validation. `@SQLRestriction("room_id is not null")` закрывает исторические channel rows для findById, batch reply lookup и коллекций User. Новых обязательных столбцов нет: room_id сохраняет nullable DDL, чтобы не сделать архивные строки невалидными. Channel column не маппится и не удаляется из БД. Edit/delete требуют room ID и membership/room lock перед любой записью; бывший автор channel message не получает bypass. MessageAccessService авторизует активную комнату. Read receipts публикуются только участникам комнаты. Channel payload field удалён из ShortMessageWrapped/MessageReadStatusContent; поля DM/GROUP не менялись.

Сохранены Stage 6 after-commit helpers, upload idempotency/hash, upload transaction outcome cleanup, room lock, preview, message ID ordering, reply/edit/delete/clear/pagination и read-state boundaries. MessageRepository потерял только channel queries; Stage 8 aggregate-unread и batch attachments сохраняются. S3ObjectAccessService/MessageAttachmentController продолжают использовать MessageAccessService; generic storage access не открывался.

## Изменения frontend

Удалено 38 файлов, включая 4 legacy-only PNG assets. Удалены ServerLayout/MyServers, ChannelChat/Folder/ItemsList, server cards/buttons/icons, API clients/interfaces/entities/slices и их стили. AppLayout и обновление профиля больше не вызывают fetchMyServers. NavigateBar сохраняет DM/friends indicator, notifications, settings, conference launcher и профиль; вместо списка серверов остаётся layout spacer, без пустой кнопки. Полного редизайна нет.

Redux server/channelMessage reducers и action types удалены; logout reset и normalized users/messages/rooms остаются прежними. WebSocket publish handler больше не подписывается на channel topic и не отправляет channel command. Reconnect по-прежнему восстанавливает 8 персональных subscriptions, DM/GROUP history и call state. Application BroadcastChannel, audio channels и LiveKit serverUrl сохранены. Ни одна библиотека не использовалась только Server/Channel: package.json/pom.xml не менялись этим этапом.

## Удалённые REST endpoints

Контекст /api указан отдельно. Старые handlers отсутствуют, это не compatibility stubs:

| Base path | Удалённые методы/суффиксы |
|---|---|
| `/servers` | POST /create; GET /my; GET /{serverId}; GET /join/{inviteCode}; PUT /{serverId}; GET /{serverId}/regenerate-invite; GET /{serverId}/leave; GET /{serverId}/members; DELETE /{serverId}/members/{targetUserId}; PATCH /{serverId}/members/{targetUserId}/nickname; DELETE /{serverId} |
| `/servers/{serverId}/channel-folders/{folderId}/channels` | GET collection; GET /{channelId}; POST /create; PUT /{channelId}; DELETE /{channelId}; GET /{channelId}/messages |
| `/server/{serverId}/channel-folders` | GET/POST collection; PUT/DELETE /{folderId} |
| `/server/{serverId}/roles` | GET /all; POST /create; PUT/DELETE /{roleId} |
| `/server/{serverId}/members/{memberId}/roles` | POST/DELETE /{roleId} |
| `/server/{serverId}/bans` | GET/POST collection; DELETE /{targetUserId} |

Отдельный активный Discover handler при инвентаризации не найден; /servers/discover также проверяется отрицательным HTTP-тестом. Frontend routes /server/:serverId, дочерний channel-folders/:channelFolderId/channels/:channelId и /my-servers удалены; /invite/:token остаётся GROUP invitation.

## STOMP и security

Удалены SEND `/app/chat/channel/{channelId}`, SUBSCRIBE/publish `/topic/channel.{channelId}`, channel read/edit/delete broadcasts. WebSocketAccessService разрешает только известные room/chat, call и friend commands и персональные queues; unknown destinations, wildcard, broker SEND и прежние Server/Channel paths отклоняются. JwtChannelInterceptor, WebSocketDeliveryInterceptor и WebSocketAuthorizationSessions продолжают JWT/subject/logout checks, reauthorization при delivery, unsubscribe/disconnect и subscription-ID integrity.

Проверки: отрицательные former channel commands/topic для существующего ID; настоящий broker не доставляет попытку server publish. Production controller scan проверяет отсутствие старых HTTP mappings и наличие auth/room/message/call/conference paths; classpath-тест ловит также забытые stale .class, поэтому выполнен mvn clean test. HTTP-тест проверяет 14 прежних адресов методами GET/POST/PUT/DELETE с JWT: 404. Shared security assertions для IDOR, read receipts, attachments, JWT, logout, LiveKit, room membership не ослаблены. Archived message без room не читается/не отмечается/не изменяется/не удаляется.

## Сохранённые общие компоненты

AuthController/AuthService/JwtTokenProvider/JwtAuthenticationFilter/RefreshTokenService; User/UserService/profiles/settings; FriendService/FriendShipAccessService; Room/RoomService/RoomAccessService/RoomInviteService и InviteController; Message/MessageService/MessageAccessService/MessageReadStatusService/RoomReadStateService и attachments; STOMP session security/interceptors/reconnect; CallSession/CallParticipant/CallSessionService/CallRestoreService/lifecycle cleanup/reconciler/LiveKitCallTokenService; ConferenceService; S3Service/S3ObjectAccessService. Frontend DM/GROUP, friends, profile/settings, call/activeCall, screen capture, devices, RNNoise и Electron остались в существующей архитектуре.

## PostgreSQL и будущая миграция

Существующая БД не подключалась, физические таблицы/строки/FK/indexes не менялись. Локальный application.properties использует `${SPRING_JPA_HIBERNATE_DDL_AUTO:update}`, пример — update; environment override требует отдельной проверки при выпуске. Удаление entities при update не удаляет unmapped таблицы/колонки. Create/drop на пользовательском datasource в Stage 9 не запускался.

LegacyDataPreservationIT проверяет два Hibernate boots в собственном PostgreSQL 16-alpine: актуальная schema + вручную добавленные 9 representative legacy tables, channel_id FK/index, legacy message/attachment/read-status; затем update. Все sentinel rows, FK/index, channel_id и attachment key остаются. Room message доступен ORM; legacy findById/batch lookup/room lookup и S3 authorization закрыты. Это доказательство поведения текущих mappings при update на fixture, а не проверка всех constraints конкретной пользовательской БД.

План: `ops/STAGE9-LEGACY-ARCHIVE-PLAN.md`. Сначала read-only catalog inventory и dependency graph, согласованный backup/retention/S3 manifest, restore drill с counts/checksums, затем отдельная reviewable миграция по фактическим FK без слепого CASCADE. Не конвертировать channel messages в комнаты. Общие user/room/message tables и Stage 6 SQL не удалять/не переписывать. Реальная физическая очистка не выполнена.

## Тесты и сравнение Stage 8

| Проверка | Stage 8 | Stage 9 |
|---|---|---|
| Default backend | 247, 3 legacy failures | **244/244 SUCCESS**, 0 failures/errors/skipped; clean test |
| PostgreSQL integration | 40/40 | **44/44 SUCCESS**, 0 skipped |
| Extended backend включая IT | 284/284 с исключением ChannelControllerTest | **288/288 SUCCESS**, 0 failures/errors/skipped, без исключений |
| Frontend | 89/89 | **91/91 SUCCESS**, 13 файлов |
| TypeScript | 12 legacy diagnostics | **0 errors, exit 0** |
| Web | SUCCESS | **SUCCESS** |
| Electron compile | SUCCESS | **SUCCESS** |
| Полный npm run build | tsc блокировал | **SUCCESS** |
| git diff --check | SUCCESS | **SUCCESS, exit 0** в обоих репозиториях |
| Listing 1/20/200 rooms | 2/2/2 SQL | **2/2/2 SQL** |
| History 100/1000/10000, page 50 | 5/5/5 SQL | **5/5/5 SQL** |

Все 89 исходных frontend сценариев сохранены; добавлены architecture guard и runtime retired-action/reconnect test. Backend удалены 3 legacy-only test classes: ChannelControllerTest (3 cases), ChannelServiceTest (3), ServerServiceTest (8; 7 annotated methods, один parameterized). Из смешанных suites удалены 10 legacy-only методов (4 MessageAccess, 4 WebSocket ACL, 2 HTTP), один authorized channel subscription value; один legacy broker сценарий заменён отрицательным. Всего 26 прежних behavioral invocations удалены/заменены, добавлены 23 новых default invocations; 247−26+23=244. Assertions действующего ядра не ослаблены. Fixture MessageServiceTest теперь задаёт room lookup для обязательного locking пути.

PostgreSQL: прежние 40 scenarios сохранены, добавлены 3 реальных persistence scenarios DM creation/reuse, GROUP create/add/leave authorization и GROUP invite/join/dedup; плюс 1 schema/data preservation. Query-count assertions Stage 8 неизменны.

Первый новый archive fixture ошибся naming strategy (plain Hibernate вместо Spring snake_case); исправлено соответствием реальному naming, а не assertions. Первоначальный broker rejection корректно бросал MessageDeliveryException: отрицательный тест теперь ожидает этот отказ. До финального прохода обнаружен существующий shutdown timeout CorePerformanceIT: Spring context закрывался после остановки PostgreSQL. Такой же timeout присутствует в Stage 8 log. Добавлен AFTER_CLASS DirtiesContext; финальный PostgreSQL run закрывает HikariPool до остановки контейнера и завершается без forced JVM kill. Production lifecycle ради теста не менялся.

Команды и логи (Java 21 C:/jbr, Maven 3.9.9, Node/npm из окружения):

```powershell
# backend; только testcontainers DB, без запуска приложения
mvn -o clean test
mvn -o '-Dtest=CorePerformanceIT,MessageConsistencyPersistenceIT,CallLifecyclePersistenceIT,LegacyDataPreservationIT' '-Dstage8.optimized=true' test
mvn -o '-Dtest=*Test,*IT' '-Dstage8.optimized=true' test
# frontend
npm test
npx tsc -b --pretty false
npm run build:web
npm run build:electron
npm run build
# оба репозитория
git diff --check
```

Логи: backend stage9-backend-full.log, stage9-postgres.log, stage9-backend-extended.log; frontend stage9-frontend-tests.log, stage9-typescript.log, stage9-build-web.log, stage9-build-electron.log, stage9-build-full.log. Maven потребовал доступ к локальному dependency cache вне sandbox; network dependencies/production подключения не потребовались. Конфигурация тестов/strict TypeScript не отключалась.

## Архитектурная проверка и ограничения

Production source search не находит Server/Channel entities/services/repositories/API clients/routes/subscriptions; исключения по словам — технические Spring transport channels, HTTP server, LiveKit SDK/serverUrl, audio channels и auth BroadcastChannel. Classpath architecture test проверяет отсутствие legacy классов и associations User/Message. Новых cycles нет; удалённые слои больше не входят в DI/Redux. Никаких новых package dependencies, controllers для Hub, schema migrations и изменений call/media lifecycle в Stage 9 нет.

Performance regression assertions проходят; новый N+1 не появился на fixture. Timing не является production SLA. Визуальная проверка двух запущенных клиентов, настоящий LiveKit/TURN, аппаратные устройства/RNNoise/screen share и packaged Electron в этом этапе не выполнялись; автоматические suites подтверждают программные contracts/lifecycle, а не слышимость/качество медиа. Web build сохраняет известное предупреждение bundle >500 kB. Прежние outbox/multi-instance broker/S3 operational риски Stage 5–8 не решались общим рефакторингом.

Отдельное наблюдение сохранённого ядра: RoomInviteService.publishJoined уже публикует напрямую внутри своей транзакции; Stage 9 этот путь не менял. Message/RoomService Stage 6 after-commit гарантии сохранены. Расширение after-commit на другие существующие publishers требует отдельной задачи.

Оценка: программное ядро отделено от Server/Channel и может стать основой независимой будущей Hub-модели без наследования старых roles/overrides/folders. PostgreSQL archival и ручная media acceptance остаются отдельными операциями. Hub не создан; Stage 10 не начат.


## Полный список удалённых backend production files (79)

- `src/main/java/com/zvonok/controller/ChannelController.java`
- `src/main/java/com/zvonok/controller/ChannelFolderController.java`
- `src/main/java/com/zvonok/controller/ServerBanController.java`
- `src/main/java/com/zvonok/controller/ServerController.java`
- `src/main/java/com/zvonok/controller/ServerMemberRoleController.java`
- `src/main/java/com/zvonok/controller/ServerRoleController.java`
- `src/main/java/com/zvonok/documentation/ChannelApiDescriptions.java`
- `src/main/java/com/zvonok/documentation/ChannelFolderApiDescriptions.java`
- `src/main/java/com/zvonok/documentation/ServerApiDescriptions.java`
- `src/main/java/com/zvonok/documentation/ServerBanApiDescriptions.java`
- `src/main/java/com/zvonok/documentation/ServerMemberRoleApiDescriptions.java`
- `src/main/java/com/zvonok/documentation/ServerRoleApiDescriptions.java`
- `src/main/java/com/zvonok/exception/CannotBanServerOwnerException.java`
- `src/main/java/com/zvonok/exception/CannotKickServerOwnerException.java`
- `src/main/java/com/zvonok/exception/ChannelFolderNotFoundException.java`
- `src/main/java/com/zvonok/exception/ChannelNotFoundException.java`
- `src/main/java/com/zvonok/exception/InvalidServerMaxMemberException.java`
- `src/main/java/com/zvonok/exception/InvalidServerNameException.java`
- `src/main/java/com/zvonok/exception/OwnerCanNotLeaveServerException.java`
- `src/main/java/com/zvonok/exception/PermissionOverrideTargetMissingException.java`
- `src/main/java/com/zvonok/exception/ServerBanNotFoundException.java`
- `src/main/java/com/zvonok/exception/ServerMemberAlreadyWasKicked.java`
- `src/main/java/com/zvonok/exception/ServerMemberLimitReachedException.java`
- `src/main/java/com/zvonok/exception/ServerMemberNotFoundException.java`
- `src/main/java/com/zvonok/exception/ServerMemberRoleNotFoundException.java`
- `src/main/java/com/zvonok/exception/ServerNotFoundException.java`
- `src/main/java/com/zvonok/exception/ServerRoleNotFoundException.java`
- `src/main/java/com/zvonok/exception/YouAlreadyMemberThisServerException.java`
- `src/main/java/com/zvonok/exception/YouNotMemberThisServerException.java`
- `src/main/java/com/zvonok/model/Channel.java`
- `src/main/java/com/zvonok/model/ChannelFolder.java`
- `src/main/java/com/zvonok/model/ChannelPermissionOverride.java`
- `src/main/java/com/zvonok/model/FolderPermissionOverride.java`
- `src/main/java/com/zvonok/model/Server.java`
- `src/main/java/com/zvonok/model/ServerBan.java`
- `src/main/java/com/zvonok/model/ServerMember.java`
- `src/main/java/com/zvonok/model/ServerMemberRole.java`
- `src/main/java/com/zvonok/model/ServerRole.java`
- `src/main/java/com/zvonok/repository/ChannelFolderRepository.java`
- `src/main/java/com/zvonok/repository/ChannelPermissionOverrideRepository.java`
- `src/main/java/com/zvonok/repository/ChannelRepository.java`
- `src/main/java/com/zvonok/repository/ServerBanRepository.java`
- `src/main/java/com/zvonok/repository/ServerMemberRepository.java`
- `src/main/java/com/zvonok/repository/ServerMemberRoleRepository.java`
- `src/main/java/com/zvonok/repository/ServerRepository.java`
- `src/main/java/com/zvonok/repository/ServerRoleRepository.java`
- `src/main/java/com/zvonok/service/ChannelFolderService.java`
- `src/main/java/com/zvonok/service/ChannelService.java`
- `src/main/java/com/zvonok/service/InviteCodeService.java`
- `src/main/java/com/zvonok/service/PermissionService.java`
- `src/main/java/com/zvonok/service/ServerBanService.java`
- `src/main/java/com/zvonok/service/ServerMemberRoleService.java`
- `src/main/java/com/zvonok/service/ServerMemberService.java`
- `src/main/java/com/zvonok/service/ServerRoleService.java`
- `src/main/java/com/zvonok/service/ServerService.java`
- `src/main/java/com/zvonok/service/dto/CreateChannelDto.java`
- `src/main/java/com/zvonok/service/dto/CreateChannelFolderDto.java`
- `src/main/java/com/zvonok/service/dto/CreateServerMemberDto.java`
- `src/main/java/com/zvonok/service/dto/CreateServerRoleDto.java`
- `src/main/java/com/zvonok/service/dto/Permission.java`
- `src/main/java/com/zvonok/service/dto/UpdateChannelDto.java`
- `src/main/java/com/zvonok/service/dto/UpdateChannelFolderDto.java`
- `src/main/java/com/zvonok/service/dto/UpdateServerRoleDto.java`
- `src/main/java/com/zvonok/service/dto/request/CreateServerBanRequest.java`
- `src/main/java/com/zvonok/service/dto/request/CreateServerRequest.java`
- `src/main/java/com/zvonok/service/dto/request/CreateServerRoleRequest.java`
- `src/main/java/com/zvonok/service/dto/request/UpdateServerMemberNicknameRequest.java`
- `src/main/java/com/zvonok/service/dto/request/UpdateServerRequest.java`
- `src/main/java/com/zvonok/service/dto/request/UpdateServerRoleRequest.java`
- `src/main/java/com/zvonok/service/dto/response/ServerBanResponse.java`
- `src/main/java/com/zvonok/service/dto/response/ServerMemberResponse.java`
- `src/main/java/com/zvonok/service/dto/response/ServerResponse.java`
- `src/main/java/com/zvonok/model/enumeration/ChannelType.java`
- `src/main/java/com/zvonok/controller/dto/ChannelMessageResponse.java`
- `src/main/java/com/zvonok/exception/CannotBanYourselfException.java`
- `src/main/java/com/zvonok/exception/CannotDeleteEveryoneRoleException.java`
- `src/main/java/com/zvonok/exception/CannotDisableEveryoneRoleException.java`
- `src/main/java/com/zvonok/exception/RedefinitionException.java`
- `src/main/java/com/zvonok/repository/FolderPermissionOverrideRepository.java`

## Полный список удалённых frontend files (38)

- `src/pages/ServerLayout/ServerLayout.module.css`
- `src/pages/ServerLayout/ServerLayout.tsx`
- `src/pages/MyServers/MyServers.module.css`
- `src/pages/MyServers/MyServers.tsx`
- `src/components/ChannelChat/ChannelChat.module.css`
- `src/components/ChannelChat/ChannelChat.tsx`
- `src/components/ChannelFolder/ChannelFolder.module.css`
- `src/components/ChannelFolder/ChannelFolder.props.ts`
- `src/components/ChannelFolder/ChannelFolder.tsx`
- `src/components/ChannelItemsList/ChannelItemList.module.css`
- `src/components/ChannelItemsList/ChannelItemList.props.ts`
- `src/components/ChannelItemsList/ChannelItemList.tsx`
- `src/components/MyServersCard/MyServersCard.module.css`
- `src/components/MyServersCard/MyServersCard.props.ts`
- `src/components/MyServersCard/MyServersCard.tsx`
- `src/components/ServerButton/ServerButton.module.css`
- `src/components/ServerButton/ServerButton.props.ts`
- `src/components/ServerButton/ServerButton.tsx`
- `src/components/ServerIcon/ServerIcon.module.css`
- `src/components/ServerIcon/ServerIcon.props.ts`
- `src/components/ServerIcon/ServerIcon.tsx`
- `src/api/serverApi.ts`
- `src/api/channelApi.ts`
- `src/api/interfaces/ServerApiInterface.ts`
- `src/api/interfaces/ChannelApiInterface.ts`
- `src/api/interfaces/GetChannelParams.ts`
- `src/api/interfaces/GetChannelMessageParams.ts`
- `src/entities/server.ts`
- `src/entities/channel.ts`
- `src/entities/channelFolder.ts`
- `src/entities/channelMessage.ts`
- `src/store/slices/server.slice.ts`
- `src/store/slices/channelMessage.slice.ts`
- `src/components/NavigateBar/NavigateBar.props.ts`
- `src/assets/base-server-icon.png`
- `public/server-button-img.png`
- `public/server-button-img2.png`
- `public/server-all-list-icon.png`
