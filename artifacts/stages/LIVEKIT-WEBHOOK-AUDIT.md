# LiveKit webhook 401 / Egress audit

Дата: 18 октября 2026, Asia/Yekaterinburg. Проверен действующий локальный Docker Compose.

## Вывод и статус

**Причина 401 установлена и воспроизведена настоящим LiveKit POST. Исправление доставки не выполнено; проблема не объявляется закрытой.** В LiveKit настроен URL, для которого в backend вообще нет endpoint/receiver. Основной lifecycle звонков webhook не использует. Поэтому добавлять новый протокол lifecycle или открывать путь через `permitAll` ради устранения 401 не требуется.

Первоначальный аудит зафиксировал состояние до удаления legacy записи и webhook target. Следующий раздел в конце документа описывает выполненный cleanup. Локальные изменения backend `docker-compose.yml` и `application.properties.example`, Stage 1–10.3 и local connection fix сохранены. PostgreSQL volumes и пользовательские S3-объекты не изменялись; credentials не менялись.

На момент первоначального аудита рекомендовано убрать неиспользуемый `webhook` block. Это выполнено в follow-up ниже. Если появится реальный потребитель событий, понадобится отдельный receiver с проверкой подписи.

## Маршрут, security и подпись

Фактический target: `http://host.docker.internal:8080/api/webhooks/livekit`. Docker mount: `C:\project\zvonok-livekit\livekit.yaml` → `/etc/livekit.yaml`. Backend работает на 8080, servlet context — `/api`.

1. В `src/main/java/com/zvonok/controller` нет `webhooks/livekit`. Поиск во всём production source не обнаружил webhook receiver или обработчиков `room_started`, `room_finished`, `participant_joined`, `participant_left`, `egress_*`.
2. Дополнительно прочитан **runtime** `/opt/app/app.jar` из контейнера: среди 285 application classes нет ни одной с упоминанием `webhook`. Это не предположение по исходникам о запущенном образе.
3. `config/SecurityConfig.java`: одна `SecurityFilterChain`, stateless, CSRF disabled; `JwtAuthenticationFilter` перед `UsernamePasswordAuthenticationFilter`; whitelist не содержит `/webhooks/livekit`; остальное — `.anyRequest().authenticated()`. Security matcher должен использовать путь без servlet context `/api`, если когда-либо появится receiver.
4. `security/JwtAuthenticationFilter.java` извлекает только `Authorization: Bearer <application JWT>`. `JwtTokenProvider` проверяет JWT ключом `app.jwt.secret`; пользовательские токены создаются с HS512. Это отдельный механизм от LiveKit webhook.
5. LiveKit отправляет JWT в `Authorization`, содержащий SHA-256 исходного тела, с подписью LiveKit API secret. Application filter такой raw JWT не аутентифицирует. Добавление `Bearer` не решает проблему: другой signing key и назначение токена. Spring отклоняет запрос до MVC. Даже с application JWT отсутствующий endpoint не станет обработчиком событий.
6. Runtime `LIVEKIT_API_KEY` и `LIVEKIT_API_SECRET` backend **совпадают** с signing key/secret в реально смонтированном YAML (проверено сравнением в памяти; значения не публикуются). LiveKit RoomService API отвечает 200. Это не ошибка сети, порта, context path, CORS, CSRF или несовпадение LiveKit key/secret. Серверная проверка webhook-подписи здесь вообще не вызывается.

Нельзя считать 401 на поддельном запросе доказательством проверки подписи. Сейчас все эти запросы отвергаются общей авторизацией.

Если receiver будет нужен позже: принимать исходное тело `application/webhook+json`; проверять JWT, issuer/API key, срок действия и SHA-256 **исходных байтов**, используя LiveKit WebhookReceiver; изолировать от application JWT filter только точный POST endpoint; передавать событие в service лишь после проверки. Не заменять проверку подписи одним `permitAll`. Предусмотреть идемпотентность и запоздалые события, не воскрешать завершённые сессии. Сейчас receiver не добавлялся.

Официальный контракт: [LiveKit webhooks](https://docs.livekit.io/intro/basics/rooms-participants-tracks/webhooks-events/).

## Фактические зависимости lifecycle

| Функция | Текущий путь | Webhook нужен сейчас |
|---|---|---|
| DM/PRIVATE start, accept, decline | `CallSessionService`, DB, `CallEventPublisher` | Нет |
| GROUP start, join/accept, rejoin | Тот же сервис, membership/status checks, application events | Нет |
| Participant lifecycle | `CallParticipant` меняется при application actions; `ACCEPTED` не доказывает наличие медиа | Нет |
| DM leave/end, GROUP last leave/host end | `finishCall`, terminal DB state/events, LiveKit delete после commit | Нет |
| Восстановление | `CallRestoreStateService` ищет DB candidate; `CallSessionService` проверяет membership, leftAt, terminal status, media grace; token touch инвалидирует старый cleanup snapshot | Нет |
| Cleanup после потери клиента | `CallLifecycleReconciler` → LiveKit `listParticipants` → `CallSessionCleanupService.observeMedia` | Нет |
| Защита cleanup | Version/observation checks, grace, повторный REST check перед финализацией; REST error не считается пустой комнатой | Нет |
| Recording/Egress | Отдельные REST start/stop + EgressServiceClient | Worker нужен только для записи; webhook consumer отсутствует |

Defaults policy: invitation 60 s, initial connect 120 s, media grace 90 s, reconciliation delay 30 s; runtime overrides отдельно не утверждаются. Статус `JOINED` поддерживается как enum, но `participant_joined` webhook не переводит в него записи. SFU-присутствие проверяется REST, а не значением enum.

## Egress: используется ли и что занимает

В момент проверки `docker ps` показывает backend, frontend, LiveKit, Redis, PostgreSQL. В `docker ps -a` нет Egress container, включая остановленные. В `zvonok-livekit/docker-compose.yaml` весь `livekit-egress` service закомментирован. Worker не включался.

Read-only `POST /twirp/livekit.Egress/ListEgress`: **200, items=0**. Read-only `SELECT status, count(*) FROM call_recording GROUP BY status`: **0 строк**. В текущей локальной БД нет recording records; это не утверждение об истории других окружений или наличии объектов в S3. S3 не перечислялся.

Текущий вклад **контейнера Egress**: 0 CPU / 0 RAM, поскольку контейнера/worker нет. Это не измерение потребления работающей записи; benchmark записи намеренно не проводился. Сам Redis нужен LiveKit и не является ресурсом исключительно Egress.

Однократный idle snapshot `docker stats --no-stream`:

| Container | CPU | RAM |
|---|---:|---:|
| livekit | 0.21% | 40.27 MiB |
| livekit-redis | 0.47% | 4.352 MiB |
| zvonok-backend | 0.21% | 602.6 MiB |
| zvonok-postgres | 0.01% | 41.5 MiB |
| zvonok-frontend | 0.00% | 28.52 MiB |

Не следует приписывать эти ресурсы Egress или обещать их экономию после удаления recording-кода. Отдельные процессы вне данного Docker Compose не обследовались.

### Оставшийся recording-код и пробелы

- `CallRecordingController`: `/api/calls/{callId}/recording/start` и `/stop` остаются доступны аутентифицированным клиентам с service-level host checks. Отсутствие кнопки в UI не удаляет API.
- `CallRecordingService.startRecording` вызывает `LiveKitEgressAdminService.startRoomCompositeMp4`, затем сохраняет `STARTING`, рассылает `/queue/call-recording`.
- Egress request: room composite, grid, MP4, `H264_1080P_60`, S3 bucket `egrees`, prefix `call-recordings/<callId>/`. Имя bucket здесь приведено как в коде, не исправлялось.
- `stopRecording` выполняет `safeStopEgress` (до 5 попыток) и применяет статус из **синхронного ответа**. При ENDING/прочих статусах сохраняется `STOPPING`; последующего event receiver/poller не найдено. Поэтому финальный файл/ошибка могут не попасть в DB. Это статически установленный риск; Egress для его воспроизведения не запускался.
- Обработчиков `egress_started`, `egress_updated`, `egress_ended` нет. `findByEgressId` в repository есть, production callers не найдены. Ни один действующий webhook handler нельзя классифицировать как «только legacy recording»: обработчиков вообще нет. Не следует выдавать предполагаемую прошлую архитектуру за найденный код.
- `CallSessionService` и cleanup не вызывают `CallRecordingService`: автоматического stop записи на end call на уровне application service нет. Асинхронное завершение worker при закрытии комнаты не синхронизируется этим backend.
- Frontend сохраняет DTO action и `/queue/call-recording` subscription в `store/middlewares/websocket/subscriptions.ts`; она лишь парсит и логирует payload. В проверенных `src/api` и `CallUi` нет вызовов recording start/stop.

### Безопасное последующее удаление

1. Подтвердить отказ от server recording у потребителей API во всех окружениях. Локальные пустые DB/ListEgress не доказывают отсутствие production записей.
2. В отдельном изменении закрыть/удалить recording start/stop API и frontend subscription/DTO, затем удалить только ставшие неиспользуемыми `CallRecordingService`, `LiveKitEgressAdminService` и связанные payload/actions. Согласовать ответ для старых API-клиентов, проверить callers.
3. Не удалять таблицу `call_recording`, исторические метаданные, PostgreSQL volumes, bucket или S3-объекты автоматически. Отдельно согласовать retention/export, если они существуют в другом окружении.
4. Egress service уже отключён локально; удалить его закомментированный Compose block/egress-only config можно отдельно. Не удалять Redis, LiveKit RoomService/token SDK, общие S3 credentials/client или message attachments storage.
5. Удалить неиспользуемый webhook target отдельно от recording-кода; сохранить REST reconciliation и повторить DM/GROUP/restore/end regression после применения конфигурации.

## Проверки и доказательства

### Реальный POST из LiveKit

В 19:49:51 +05:00 скрипт создал уникальную пустую SFU room `webhook-audit-16dadd84ce5047b2873d426594a81bb4` (CreateRoom 200), затем удалил **только её** (DeleteRoom 200). DB CallSession и пользовательские комнаты не создавались и не изменялись.

Реальный notifier LiveKit зарегистрировал:

| Event | Event ID | HTTP |
|---|---|---:|
| room_started | EV_z8jJoijhF8Fn | 401 |
| room_finished | EV_GfktF7pQ3vAX | 401 |

Backend `JwtAuthenticationFilter` зарегистрировал соответствующие `POST /api/webhooks/livekit status=401`. В текущих логах также найдены реальные DM `dm-5-call-33` и GROUP `group-8-call-30/31` participant/track/room events с 401 и успешный `DeleteRoom` 200. Эти предшествовавшие audit события — подтверждение доставки, **не новый browser acceptance run**.

### Невалидные запросы и валидная подпись

На работающий backend отправлено шесть `application/webhook+json` POST: без Authorization, malformed token, JWT с неправильным secret, изменённое тело при прежней подписи, корректный LiveKit JWT с hash исходного тела, тот же JWT с Bearer prefix. Все шесть: **401**. Положительная server-side signature verification **не реализована и не заявляется пройденной**. Реальный notifier POST выше независимо исключает ошибку самодельного тестового токена как объяснение исходного 401.

Этот диагностический POST был выполнен перед удалением webhook target. Он зафиксировал исходный отказ, а не acceptance receiver.

### Regression tests

JDK: `C:\Users\Пользователь\.jdks\semeru-21.0.9`.

- `mvn.cmd -B test`: **248 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Лог: `webhook-audit-regression.log`.
- `mvn.cmd -B -Dtest=CallLifecyclePersistenceIT test`: **5 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Отдельный PostgreSQL Testcontainer, не действующая БД. Лог: `webhook-audit-persistence.log`.
- В default suite прошли `CallSessionServiceTest`, `CallActionsLifecycleTest`, `CallLifecycleTest`, LiveKit security/presence tests: DM start/accept/leave, GROUP membership/join/reaccept/last leave/host end, grace recovery, forbidden restore, REST failure, duplicate actions и terminal-state protections.
- Persistence IT проверяет concurrent leave/end/token restore, committed recovery invalidating cleanup snapshot, rollback без публикации/удаления media room, revoked membership restore и однократное истечение grace без восстановления ended call.
- Первый sandbox Maven запуск остановился на чтении dependency JAR из cache. Повторный запуск с доступом к cache завершился успешно; это не failure regression test.

## Открытые границы

Новая browser-проверка DM/GROUP с двумя/тремя клиентами и browser reload/reconnect не выполнялась; lifecycle проверен существующими unit/integration tests, а доставка — настоящим LiveKit. Полный extended suite всех `*IT` и запись под нагрузкой не запускались. Stage 1–10.3 изменения не переписывались; прохождение всех исторических acceptance gates этим аудитом не заявляется.

**До follow-up ниже 401 оставался открытым.** Добавлять receiver исключительно ради ответа 200 и включать Egress для данного lifecycle оснований нет.

## Follow-up: удаление серверной записи и webhook target

По решению владельца проекта удалены recording API и Egress client/service, а также recording entity, repository, status/action enums и payload DTO. Общая LiveKit server SDK осталась для RoomService и token generation; общий S3 service и credentials не менялись. В frontend удалены неиспользуемые recording DTO, /user/queue/call-recording destination и пустой logger subscription. В backend снята только эта персональная WS destination. Обычный /user/queue/call, JWT security, токены и CallSession lifecycle сохранены.

Из смонтированного zvonok-livekit/livekit.yaml убран только webhook block. Backend и frontend образы пересобраны и запущены; LiveKit server пересоздан для чтения файла. PostgreSQL и Redis оставались запущены; перед пересозданием сервисов в Compose БД было 0 сессий RINGING/ACTIVE. Проверены backend /api/health HTTP 200 и frontend HTTP 200. В текущем Docker Compose Egress worker отсутствует.

Таблица call_recording сохранена. Entity и repository удалены из активной JPA модели; с ddl-auto=update Hibernate не удалил неизвестную таблицу. LegacyDataPreservationIT создаёт в отдельном PostgreSQL Testcontainer историческую строку, два внешних ключа и два индекса, запускает Hibernate update и затем полноценный Spring Boot application context с тем же Testcontainer. Проверка подтверждает сохранность строки, обоих FK и обоих индексов. В действующей локальной Compose БД после обновления таблица также существует: 0 строк, 2 FK и прежние индексы. Никакие SQL изменения к пользовательским данным не применялись.

Проверки после cleanup:

- Backend mvn.cmd -B clean test: 250 tests, 0 failures, 0 errors, 0 skipped.
- mvn.cmd -B -Dtest=LegacyDataPreservationIT: 1 test passed; Spring Boot стартовал на PostgreSQL Testcontainer с ddl-auto=update.
- Frontend npm test: 120/120; npm run build: TypeScript и production build прошли.
- Backend regression проверяет отсутствие recording controller/mappings/classes; authenticated /calls/{id}/recording/start и /stop дают 404. WS ACL отвергает /user/queue/call-recording. Frontend test проверяет отсутствие этой subscription.
- Runtime backend и frontend health: HTTP 200. Загруженный LiveKit YAML не содержит webhook target.

Исторические S3-объекты и bucket egrees не перечислялись, не перемещались и не удалялись. Отсутствие локальных строк не говорит об истории других окружений. Поэтому удаление физической таблицы, данных и bucket оставлено для отдельного retention решения. Закомментированный Egress service block удалён из локального Compose; Egress worker и browser MediaRecorder не запускались/не добавлялись.
