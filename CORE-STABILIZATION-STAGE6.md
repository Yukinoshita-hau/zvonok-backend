# Zvonok Core Stabilization — Stage 6: Message Consistency & Transaction Safety

Дата: 2026-10-18, Asia/Yekaterinburg. Рабочие репозитории: `zvonok-backend`, `zvonok-frontend`.

## Результат и границы

B03/B04 исправлены в действующем message/room потоке: сохранение сообщения, attachments metadata, room preview и изменения истории коммитятся вместе; связанные WebSocket события публикуются после commit. Для загрузок добавлены безопасная компенсация и необязательный ключ повторной обработки. Работающий history resync Stage 3 сохранён и проверен.

До изменений проверены незакоммиченные файлы и актуальные реализации, прочитаны SECURITY-P0, Stage 2, Stage 3, security follow-up, Stage 4, Stage 5 и backend AGENTS.md. Указанные в AGENTS `agent/code-style/*.md` отсутствуют. Его общее правило «Don't create migrations» переопределено прямым заданием пользователя подготовить и протестировать SQL; автоматически подключать migration framework или применять SQL к пользовательской БД не потребовалось.

Предыдущие изменения не сбрасывались. Коммиты, production операции, массовое удаление S3, изменения `.factorypath`, новая инфраструктура и Stage 7 не выполнялись. Canvas/Code Sessions не возвращались; старые Server/Channel компоненты не развивались. В общих MessageService/receipt путях оставлена совместимость channel destinations, включая перенос существующих публикаций после commit. Runtime LiveKit не менялся; в существующем PostgreSQL lifecycle test время ограничено микросекундной точностью PostgreSQL, чтобы устранить ложный отказ ровно на границе 90 секунд.

## 1. Подтверждённые ошибки

| Область | До Stage 6 | После |
|---|---|---|
| B04 / сообщения | `convertAndSend*` вызывались до commit; поздняя ошибка PostgreSQL оставляла клиентам несуществующее сообщение | DTO и адресат фиксируются внутри транзакции; отдельный callback на адресата запускается после commit |
| B04 / связанные события | Room create/members/leave/clear и read receipts могли публиковаться до commit | Используют тот же `TransactionUtils.runAfterCommit` |
| B04 / callback | Повторный вызов одного callback повторял побочный эффект | `AtomicBoolean.compareAndSet` обеспечивает одну попытку, в том числе после ошибки публикации |
| B04 / storage | INSERT сообщения → S3 uploads под транзакцией; при втором upload или DB failure уже загруженные объекты оставались без компенсации | Валидация → S3 вне транзакции → короткая DB transaction; компенсация только новых UUID keys |
| B04 / повторная обработка | У upload запроса не было устойчивого идентификатора | Необязательный UUID `clientRequestId`, SHA-256 содержимого запроса, unique `(sender_id, upload_request_key)` |
| B03 / edit | Любое edit делало старое сообщение последним | Preview меняется только для текущего `lastMessageId` |
| B03 / delete | Пересчёт каждого delete, потеря attachment placeholder, последний delete вызывал неверный overload и не очищал preview | Один bounded latest query только при удалении последнего; корректные null и attachment preview |
| B03 / concurrency | Сохранение и preview разных отправок могли перезаписаться | Единая pessimistic Room lock до записи, edit/delete/clear и room mutations |
| B03 / order | Первая страница по sentAt, следующая по id | Room/DM history и frontend используют ID order, совпадающий с cursor/unread моделью |
| B03 / длинный текст | Preview `varchar(255)` получало полный текст и могло откатить сообщение | Preview ограничено 255 символами; полный текст истории сохраняется |
| Frontend / previews | Ответ старого `fetchMyRooms` заменял более новый | Проверяется requestId |
| Frontend / clear | Клиент ожидал roomId верхнего уровня, сервер посылает payload.roomId; поздний clear стирал новые сообщения | Поддерживаются обе формы, запускаются rooms/history resync; REST clear также не обнуляет новые сообщения |
| Frontend / replay | Повторный create мог заменить live overlay после edit/delete; клиент сортировал по wall clock | Terminal/delete и edit guards, защита от старых editedAt, дедупликация и ID order |

## 2. Архитектура и границы транзакций

Обычное сообщение: REST/STOMP → существующий MessageService → авторизация + `RoomRepository.findByIdForUpdate` → Message INSERT → room preview UPDATE → commit → персональные `/queue/messages`. Membership/active-room проверки не ослаблялись. Блокировка общая для DM/GROUP и действует между процессами через PostgreSQL.

Edit/delete сначала получают scalar roomId сообщения, блокируют комнату с проверкой доступа, затем загружают Message. Это избегает загрузки stale Message до ожидания lock. Channel compatibility path сохраняется, новая channel подсистема не создавалась. При edit проверяется sender; при delete сохранены прежние sender/admin проверки.

Clear GROUP: lock → soft delete видимых сообщений → reset RoomReadState → очистка preview → commit → прежний `ROOM_MESSAGES_CLEARED` с payload. Clear DM по действующей политике запрещён. Новая отправка после clear создаёт корректный preview и unread. Сообщения/вложения физически не удаляются.

Загрузка использует `NOT_SUPPORTED` orchestration: короткая проверка доступа/reply → проверка MIME/size/duration всех файлов и fingerprint вне DB transaction → короткая проверка прежнего запроса → S3 uploads → новая короткая TransactionTemplate transaction. В ней повторно проверяются доступ, reply и duplicate request под Room lock, создаются Message + attachments через прежний JPA cascade, обновляется preview и регистрируются callbacks. Сеть S3 не держит SQL transaction/Room lock. Helper не вызывается из общей внешней transaction; upload endpoint владеет самостоятельным commit, что соответствует его HTTP workflow.

Публикации получают готовые DTO/строковые destinations; lazy entities и SQL запросы не откладываются в callbacks. Отказ одного адресата после commit логируется и не блокирует callback следующего адресата. Сохранённая запись не откатывается из-за WebSocket.

## 3. Порядок событий и восстановление

Внутри транзакции callbacks выполняются в порядке регистрации. Destination names, payload shapes и REST/STOMP пути Stage 3 сохранены. У публикации нет durable outbox, ACK/retry или гарантии доставки. После DB commit две разные транзакции могут публиковать callbacks вперемежку; Room lock гарантирует порядок записей и preview, но не глобальный порядок доставки событий.

Frontend дедуплицирует по message ID, сохраняет tombstone удаления до resync, не позволяет create отменить edit/delete и не применяет старый editedAt поверх нового. Edit, пришедший раньше create, сохраняется и используется при последующем create. Потерянные изменения восстанавливает существующий reconnect resync: страницы читаются до ранее загруженной границы, пропущенные deletions исчезают, edits берутся из PostgreSQL. Повторно этот механизм не переписывался.

HTTP upload response теперь также применяется через существующий message reducer: отправитель видит сохранённое вложение, даже если его WebSocket event не дошёл. При поздней доставке второго экземпляра дубля нет. Для остальных клиентов потерянное событие компенсируется resync; непрерывное подключение само по себе не гарантирует немедленного обнаружения потери.

## 4. Storage: последовательности и компенсация

Найдены два действующих domain upload callers: MessageService и UserService.uploadAvatar. Все `putObject` проходят S3Service со Stage 2 validation и private ACL. Message IMAGE/VIDEO/AUDIO/VIDEO_NOTE используют один pipeline. Отдельного разрешённого произвольного FILE типа сейчас нет: AttachmentType содержит только эти четыре значения; generic S3 mutations закрыты Stage 2. Новые разрешения MIME не добавлялись.

| Отказ/исход | PostgreSQL | S3 |
|---|---|---|
| Validation/access до upload | Message отсутствует | PUT не вызывается |
| Первый или второй upload | Message отсутствует | Delete только UUID keys, созданных/попытанных этим вызовом; учитывается PUT с потерянным ответом |
| Ошибка после upload до создания transaction callback | Message не создан | Компенсация из catch |
| Ошибка INSERT/flush/commit с подтверждённым rollback | Message, attachments, preview откатываются | Компенсация из afterCompletion(ROLLED_BACK) |
| Commit успешен, WebSocket упал | Message/attachments/preview остаются | Файлы остаются |
| Неизвестен результат commit | Не объявляется rollback | Файлы сохраняются: commit мог произойти |
| Ошибка delete при компенсации | Исходная ошибка не заменяется | Логируется точный orphan key; остальные новые keys всё равно обрабатываются |
| Concurrent retry того же upload | Сохраняется одно сообщение | Лишние staged keys проигравшего удаляются после завершения transaction |

Уникальные UUID storage keys и access resolution по MessageAttachment сохранены. Компенсация не принимает key из пользовательского запроса, старого Message или avatar URL. Существующие объекты, в том числе soft-deleted messages и старые avatar objects, не удалялись.

Avatar upload остаётся отдельным ранее существующим S3 → user avatar URL flow. Его транзакционность/публикация профиля не перепроектировались в message scope. Ошибка DB после avatar PUT и crash процесса могут оставить orphan; старый avatar не удаляется. Это не исправление всех storage workflows приложения.

Crash после PUT до регистрации/завершения callback, недоступный S3 delete, потерянный PUT response и неизвестный commit не позволяют гарантировать отсутствие orphan. Предлагаемый дальнейший механизм: изолированный dry-run inventory/manifest новых keys, достаточный grace, повторная проверка отсутствия любых DB references (включая soft-deleted messages и avatar/room references), отсутствие активных uploads и только затем одобренное адресное удаление. Для UNKNOWN commit обязательна reconciliation с PostgreSQL; автоматическое удаление по одному возрасту небезопасно. Такой cleaner не запускался и не добавлялся.

## 5. Повторная обработка upload

`clientRequestId` — необязательный multipart UUID; ответ и URL прежние. Текущий frontend создаёт ключ до Axios POST; transparent replay той же FormData сохраняет ключ. Явный retry через API/thunk может передать прежний clientRequestId. Новое пользовательское действие без прежнего ключа считается новой отправкой. Старый клиент без ключа сохраняет совместимость, но независимые POST без идентификатора невозможно безопасно отличить от намеренной повторной отправки.

Fingerprint учитывает room/content/reply/type/duration/waveform, порядок файлов, имена, MIME, размеры и байты. Повтор с тем же sender/key/hash возвращает прежнее сообщение и attachment IDs без новых INSERT/events/PUT. Повтор с другим содержимым или tombstoned message получает 400. Проверяется актуальное членство, даже если исходная отправка разрешалась. Конкурентные запросы в одной комнате сериализуются lock; unique DB constraint дополнительно запрещает одинаковый sender/key между комнатами/процессами. Ключи сохраняются вместе с soft-deleted Message и не воскрешают историю.

## 6. Room preview и unread

Принятый порядок сообщений — растущий server ID; sentAt/editedAt остаются отображаемыми серверными timestamp. Send назначает preview своей записи под lock. Edit старой записи не меняет preview/activity; edit последней обновляет только её текст с прежним sentAt. Delete старой записи не выполняет лишнего latest query; delete последней выбирает максимум ID среди неудалённых, включая attachment placeholder. При отсутствии сообщений id/content становятся null, activity возвращается к room.createdAt. Clear делает то же; следующий send формирует новый preview.

Unread по-прежнему считается существующими запросами по sender/ID/deletedAt и RoomReadState. Edit не меняет количество, delete уменьшает его по видимой истории, clear сбрасывает read states, новое сообщение после clear снова считается unread для другого участника. Проверены DM и GROUP; запрет clear DM сохранён.

## 7. SQL и контролируемая схема

Фактические поля Stage 5: `call_session.media_empty_since`, `call_session.media_observed_at`, nullable `timestamp(6) without time zone`. Spring Boot/Hibernate snake_case соответствие проверено на реальной PostgreSQL схеме; теперь имена явно закреплены `@Column`.

Подготовлены самостоятельные ручные SQL:

- `ops/sql/stage6-call-media-observations.sql` — два nullable timestamp поля, без backfill/удаления данных.
- `ops/sql/stage6-message-upload-idempotency.sql` — nullable `message.upload_request_key varchar(36)` и `upload_request_hash varchar(64)`, unique `(sender_id, upload_request_key)`; прежние строки остаются NULL и не конфликтуют.

Скрипты транзакционные, повторяемые, с `lock_timeout=5s`, `statement_timeout=30s`; неожиданная существующая форма колонок вызывает отказ вместо преобразования/потери данных. При отсутствующей baseline table выводится NOTICE и выполняется no-op: эти additive scripts не создают полную схему приложения. Это поведение проверено в пустой временной схеме; также проверены существующая минимальная схема со строками, повторное применение, сохранение ранее заполненных timestamp/keys, unique constraint и действующая Hibernate-generated схема.

Пользовательская/production БД не подключалась. Миграции выполнялись только в disposable PostgreSQL 16 Testcontainers.

Порядок применения на согласованном стенде:

1. Проверить database/schema/search_path и backup/restore на копии, снять schema-only baseline и сравнить его с действующими JPA entities. Для пустой БД сначала нужен проверенный полный baseline.
2. Остановить/ограничить записи на время DDL, оценить размер message и длительность создания unique constraint. При timeout транзакция откатывается; не обходить защиту без оценки блокировок.
3. Выполнить `psql --dbname=<approved-staging-dsn> --set=ON_ERROR_STOP=1 --file=ops/sql/stage6-call-media-observations.sql`, затем аналогично `stage6-message-upload-idempotency.sql`, с явно выбранным search_path.
4. Повторить scripts и проверить типы/nullable/constraint, сохранность строк, запуск приложения и smoke tests.
5. После сверки всей схемы установить `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` для deployment. Текущий application.properties поддерживает эту переменную и по умолчанию использует update. Конфигурация автоматически не переключалась.
6. Хранить версии scripts/checksums и журнал применения; выдавать runtime роли права DML, DDL выполнять отдельной ролью. Flyway/Liquibase можно подключить позже отдельным изменением с baseline существующей БД, без автоматического create/drop и без удаления legacy tables.

## 8. Изменённые файлы именно Stage 6

Backend runtime:

- `service/MessageService.java`: transaction boundaries, snapshots/publication, preview, storage staging/compensation/idempotency, ID history.
- `service/RoomService.java`: after-commit room events, transaction createGroup и общая lock для room mutations/preview.
- `service/MessageReadStatusService.java`: after-commit read receipt snapshots.
- `utils/TransactionUtils.java`: одна попытка callback и stack trace ошибки.
- `repository/MessageRepository.java`: scalar roomId, latest visible Message, ID order и lookup upload request.
- `model/Message.java`: nullable upload request key/hash и unique constraint.
- `model/CallSession.java`: явные имена двух существующих полей.
- `controller/RoomController.java`: optional clientRequestId в прежнем endpoint.

Backend tests/SQL:

- Новые `integrationtest/MessageConsistencyPersistenceIT.java`, `unittests/service/TransactionPublicationTest.java`, `AttachmentTransactionOutcomeTest.java`.
- `integrationtest/CallLifecyclePersistenceIT.java`: microsecond time fixture на границе PostgreSQL timestamp, runtime lifecycle без изменений.
- `unittests/security/MessageHttpSecurityTest.java`, `WebSocketBrokerSecurityTest.java`, `StorageHttpSecurityTest.java`: constructor dependency fixtures; broker fixture использует locked room method. Assertions безопасности не отключались.
- Два SQL scripts выше и этот отчёт.

Frontend:

- `src/api/interfaces/MessageAttachmentDtos.ts`, `src/api/messageAttachmentsApi.ts`: optional key и формирование multipart.
- `src/store/slices/message.slice.ts`: replay/order guards, HTTP upload response, безопасный clear resync и typed thunk dispatch.
- `src/store/slices/room.slice.ts`: requestId guard списка комнат.
- `src/store/middlewares/websocket/subscriptions.ts`: nested clear event и authoritative resync.
- `tests/messageConsistency.test.ts` и `tests/websocket.test.ts`: новые инварианты и clear-event regression.

Остальные отображаемые git status изменения принадлежат предыдущим этапам; они не являются новым scope Stage 6. Build outputs не являются ручными изменениями дизайна/функций.

## 9. Автоматизированные результаты

| Проверка | Stage 5 baseline | Stage 6 |
|---|---|---|
| Backend targeted | 188/188 | 206/206, SUCCESS; расширенный набор |
| Backend full | 244, 3 legacy failures | 247, 3 прежних ChannelControllerTest failures; Errors 0, Skipped 0; НЕ SUCCESS |
| PostgreSQL integration отдельно | 5/5 | 33/33, SUCCESS: 28 message consistency + 5 lifecycle |
| Frontend | 48/48, 6 файлов | 58/58, 7 файлов, SUCCESS |
| TypeScript `tsc -b --pretty false` | 66 diagnostics | 66 diagnostics, НЕ SUCCESS; новых diagnostic signatures нет |
| Web build | SUCCESS | SUCCESS, прежнее предупреждение большого bundle |
| Electron build | SUCCESS | SUCCESS |
| `git diff --check` | Проверен | Успешно в обоих репозиториях; только Git LF/CRLF warnings |

Полный backend suite не объявляется успешным: прежние падения `getChannelShouldReturnChannelWhenUserCanViewIt`, `getChannelShouldReturnForbiddenWhenChannelAccessDenied`, `getChannelShouldReturnNotFoundWhenChannelDoesNotBelongToFolder` сохранены без изменения legacy tests. Проверены MessageAccessSecurityTest, MessageHttpSecurityTest, storage security, JWT/refresh rotation, WebSocket ACL/broker, LiveKit token/presence и lifecycle suites. Targeted набор расширен MessageService и новыми transaction tests. Frontend добавляет 10 сценариев к baseline; существующий reconnect/page resync тест продолжает проходить. TypeScript сравнивался с stage5-typescript.log после удаления только координат строк: diagnostic signatures совпадают.

Новые PostgreSQL сценарии: commit/rollback publication; broker failure plain/attachment; edit/delete/preview/unread DM/GROUP; clear и send после clear; rollback edit/delete/clear; concurrent sends; clear/send и delete/send; длинный Unicode text без усечения истории и разрыва emoji; upload каждого AttachmentType; отказ первого/второго PUT; invalid second file до PUT; реальная CHECK constraint ошибка commit после PUT; неуспешная компенсация без удаления старого файла; отзыв membership во время upload; последовательный/конкурентный retry с теми же attachment IDs, sender scope, запрет retry после удаления/отзыва доступа; repeatable migration/data/unique constraint проверки.

TransactionPublicationTest проверяет повтор callback после успешной и неуспешной попытки. AttachmentTransactionOutcomeTest проверяет UNKNOWN и отсутствие небезопасного delete. Mock storage не выдаётся за полноценную проверку S3.

Логи: backend `stage6-targeted.log`, `stage6-backend-full.log`, `stage6-persistence.log`; frontend `stage6-tests.log`, `stage6-typescript.log`, `stage6-build-web.log`, `stage6-build-electron.log`. Команды запускались с Java 21 `JAVA_HOME=C:\jbr`, Maven offline с доступом к существующему cache; PostgreSQL использовал Docker/Testcontainers и только создаваемые тестами контейнеры.

PostgreSQL suite отдельно от стандартного Maven naming pattern: `mvn -o -Dtest=MessageConsistencyPersistenceIT,CallLifecyclePersistenceIT test`. Real JPA/locks/commit/rollback проверены; S3 и SimpMessagingTemplate контролируемые mocks. Это не тест настоящего S3 или реальной доставки STOMP до физического клиента. Controlled transaction outcome unit test отдельно моделирует UNKNOWN, не объявляет такой сценарий проверкой реального обрыва соединения PostgreSQL.

## 10. Ручная приёмка двумя клиентами

1. В DM и GROUP отправить обычные/длинные сообщения, изображения, видео, голосовые и видеозаметки; проверить историю, preview, сортировку и unread обоих пользователей.
2. Редактировать старое и последнее сообщение; удалить старое, последнее, единственное и последнее текстовое после attachment-only. Сверить PostgreSQL, REST history и обе комнаты UI.
3. GROUP clear при открытой истории обоих клиентов; немедленный send третьим запросом; повторить clear/send и delete/send параллельно. Новое после clear не должно исчезать от позднего события. Clear DM должен по-прежнему отклоняться.
4. Отключить STOMP одного клиента, отправить/редактировать/удалить больше одной страницы сообщений, выполнить clear и новые sends; reconnect должен восстановить authoritative history/previews/unread без дублей.
5. На согласованном тестовом storage вызвать отказ первого/второго PUT, DB INSERT/commit после PUT и отказ cleanup DELETE. Проверить только новые UUID keys, сохранность старых файлов и orphan logs.
6. Повторить один multipart с тем же clientRequestId последовательно и конкурентно; проверить один Message, те же attachment IDs, отсутствие повторных событий, cleanup лишних staged keys. Другой payload с тем же ключом должен отклоняться.
7. Отозвать членство во время upload и перед retry; проверить отсутствие сообщения/публикации и отсутствие доступа к чужим файлам. Generic storage upload/delete остаются закрытыми.
8. Отключить broker после commit; сообщение и файл должны оставаться доступными через историю; проверить отправителя по HTTP response и другого клиента после resync.
9. Повторить гонки на двух backend instances, учитывая прежние ограничения simple broker. Проверить настоящий browser/Electron renderer и реальные S3 permissions/content/downloads.

## 11. Оставшиеся ограничения

Нет распределённой ACID между S3 и PostgreSQL; durable delivery/outbox и гарантированный orphan cleaner не добавлялись. Разрыв процесса после commit может потерять событие; при сохранённом соединении до resync клиент может временно отставать. UNKNOWN commit сохраняет файлы до reconciliation. Прямое внешнее удаление S3 объекта или ручная запись в БД вне domain services не покрываются transaction guarantees. Массовый backfill ранее испорченных preview в пользовательской БД не выполнялся: такие комнаты следует выявлять dry-run сравнением с последним неудалённым Message и исправлять отдельно по согласованному списку; новый send уже обновляет preview корректно.

Idempotency требует повторного использования clientRequestId; POST без ключа и новое действие UI без прежнего ключа считаются отдельными отправками. Простое сравнение содержимого для дедупликации не вводилось. Avatar orphan/публикация профиля — прежний отдельный риск, описанный выше. Ограничения многопроцессного STOMP simple broker Stage 3 сохранены. Три legacy Channel tests и прежние TypeScript diagnostics не исправлялись.

Реальный S3, два физических клиента и application deployment с production schema не проверялись. SQL подготовлен для review и проверен только на временных тестовых схемах; разрешение на production применение не запрашивалось и не предполагалось.
