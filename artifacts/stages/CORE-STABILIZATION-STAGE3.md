# Zvonok Core Stabilization — этап 3: Auth, WebSocket, Messaging

Дата: 2026-10-17 (Asia/Yekaterinburg).
Репозитории: `C:\project\zvonok-backend`, `C:\project\zvonok-frontend`.

## Результат

F01/F02/F03 исправлены в приложении и покрыты автоматизированными тестами.
Рабочие изменения этапов 1–2, включая S01/S02/S03/S04/S05, сохранены.
Production, пользовательские данные, схема БД, B01 и Hub/Server/Channel архитектура не изменялись.
Коммитов нет. Существующий dirty `.factorypath` не редактировался.

Это подтверждение выполненных программных проверок, а не приёмка реального браузерного/медиа стенда.
PostgreSQL, два реальных браузера, сетевой транспорт STOMP и запущенный Electron не использовались
в интеграционных тестах этого этапа. Их проверки перечислены ниже отдельно.

## 1. Что подтвердилось в актуальном коде

* F01: Axios request interceptor независимо вызывал refresh для каждого запроса около expiry.
  Ошибки refresh подавлялись, сетевые ошибки приводили к logout; обращение к `error.response.data.message`
  без проверки самого response могло заменить исходную ошибку на TypeError. Refresh также вызывался
  отдельно при bootstrap и обновлении профиля. На backend validate и rotate выполнялись отдельно,
  а rotate безусловно сохранял revoked и создавал потомка: два прочитавших действующий токен запроса
  могли успешно ротировать его. Не было общей транзакции для validate/rotate/выдачи access JWT.
* F02: brokerURL содержал JWT первого подключения. Повторный connect создавал другой Client до
  завершения deactivate предыдущего. Не было очистки соединения при logout; транспортный reconnect
  восстанавливал персональные подписки, но не управлял актуальностью callback/динамических подписок
  и не синхронизировал пропущенную историю. Refresh открытого простаивающего сокета отсутствовал.
* F03: fulfilled истории без проверки roomId/requestId заменял общий массив. Pagination добавляла
  данные конкатенацией без дедупликации, а начальная HTTP history могла удалить live messages,
  поступившие во время запроса. DM и групповые комнаты используют этот общий message slice.

Перед реализацией прочитаны SECURITY-P0.md, CORE-STABILIZATION-STAGE2.md, AGENTS.md backend,
текущие diffs и существующие security/AuthService тесты. Упомянутые AGENTS.md файлы
`agent/code-style/*.md` отсутствуют; это не стало причиной остановки или изменения архитектуры.

## 2. JWT refresh и logout

### Backend

`RefreshTokenRepository.consume(id, now)` выполняет условный UPDATE только для ещё не отозванной
и не истёкшей строки. Успех — строго одна обновлённая строка. Конкурирующий UPDATE после ожидания
блокировки строки не может снова выполнить условие revoked=false. Проигравший получает прежний
RefreshTokenRevokedException/401 и не создаёт потомка. Это не JVM mutex и действует для нескольких
процессов приложения, использующих одну БД.

Потребление родителя и вставка потомка выполняются в транзакции RefreshTokenService.rotate.
AuthService.refresh теперь также транзакционный: validate, работа с lazy user, rotate и создание
access JWT проходят вместе. Ошибка до commit откатывает потребление и потомка.
Новых таблиц/колонок/миграций, Redis и других инфраструктурных компонентов нет.

Успешный logout дополнительно удаляет refresh cookie через Max-Age=0 с прежними
HttpOnly, profile-specific Secure, SameSite и Path. Остальные DTO и endpoints сохранены.
Сохранена предыдущая S02-инвалидация пользовательских WebSocket subscriptions.

### Frontend

* SessionCoordinator хранит один Promise refresh на renderer. Все ожидающие HTTP-запросы получают
  один результат; после завершения читают актуальный access JWT из привязанного Redux store.
* authTransport отделён от обычного Axios transport и не имеет refresh interceptors.
  Bootstrap использует тот же coordinator. Из обновления профиля удалена ненужная ротация JWT:
  профиль не меняет subject/id JWT. Старый цикл импортов api -> store -> api устранён.
* На 401 разрешён один повтор HTTP-запроса. Поздний 401 для уже заменённого JWT использует новый
  access JWT без дополнительной ротации. Повторный 401 завершает сессию. Сетевые ошибки и 5xx
  не вызывают logout, исходный AxiosError/response/message сохраняется, включая ошибки auth transport.
* Logout инвалидирует сессию сразу по intent (`logoutUser.pending`), независимо от успеха HTTP revoke.
  Очищаются пользовательские Redux slices, WebSocket и subscription intents. Старый refresh/HTTP response
  не применяется; middleware также отбрасывает результаты thunks предыдущего поколения сессии.
  Поздний результат logout не очищает новый login. Auth запросы login/register защищены поколением сессии.
* Все cookie-changing операции проходят локальную последовательную очередь, а в среде с Web Locks —
  общий origin-wide lock `zvonok.auth.cookie`. Logout ожидает завершения cookie rotation и использует
  актуальный cookie браузера. Старый JWT захватывается до локальной очистки для авторизации logout.
  Изменившаяся сессия отменяет отложенный logout до отправки, чтобы не отозвать cookie другого аккаунта.
* Между renderer-контекстами передаются только уведомления о границе сессии. В localStorage лежат
  флаг окончательного logout и случайная версия сессии; JWT/refresh token туда не записываются.
  BroadcastChannel и storage event закрывают старые контексты; проверка версии на HTTP/refresh boundary
  защищает и от задержавшегося уведомления. Старая вкладка не усваивает токен нового аккаунта.
  После смены аккаунта в другой вкладке требуется явный login в старой вкладке.
* При недоступном storage остаётся локальный запрет refresh после logout. На следующей загрузке
  persistence этого запрета без storage невозможна; успешный серверный revoke/удаление cookie остаются нужны.

Single-flight объединяет запросы одного renderer. Между вкладками refresh сериализуется, поэтому
каждая вкладка может выполнить свою последовательную ротацию уже актуального cookie, без reuse родителя.
Электронный main/preload transport не переписывался; renderer использует тот же coordinator.

## 3. WebSocket lifecycle, reconnect и подписки

Архитектура STOMP и существующие destinations сохранены. На сессию существует один Client.
Повторный connect во время работы идемпотентен. При обновлении JWT существующий Client сначала
принудительно деактивируется с ожиданием Promise и затем активируется снова; второй параллельный
Client не создаётся. При смене сессии новый Client создаётся после завершения предыдущего shutdown.

`beforeConnect` перед каждой первоначальной/повторной попыткой читает текущий токен, при необходимости
ожидает общий refresh и заново устанавливает brokerURL и CONNECT Authorization. Прежний JWT в query
для handshake сохранён. STOMP debug отключён, чтобы не печатать CONNECT credentials.

Для уже открытого сокета установлен таймер refresh до expiry. Он использует прежний порог 60 секунд,
уменьшая его до половины TTL для короткоживущих токенов. При отказе сети нет refresh retry loop:
ошибка видна в connection state; последующий online/обычный запрос/явное подключение может повторить
операцию. Существующий STOMP transport reconnectDelay=5000 сохранён.

Каждый физический connect имеет поколение. Старые callbacks и продолжения async обработчиков
не dispatch-ят события после disconnect, reconnect, logout или account switch. Logout очищает
таймер, фактические подписки и желаемые canvas/code subscriptions. Закрытие Client удаляет broker
subscriptions; S02 по-прежнему проверяет JWT и текущие права при SUBSCRIBE/SEND/доставке.

На connect регистрируется ровно один набор восьми персональных очередей:
messages, call, call-recording, friend-requests, errors, message-read, room-events, users.
DM и групповые сообщения backend доставляет через `/user/queue/messages`, поэтому отдельная
новая room-topic подписка для них не добавлена.

Canvas lifecycle/draw и code lifecycle/session subscription intents сохраняются отдельно от
фактических subscription handles, в том числе при временном offline. Unsubscribe удаляет intent.
Перед восстановлением запрашиваются текущие rooms и restoreCallSession; допускается только
восстановленный активный звонок с ACCEPTED/JOINED и текущей комнатой в списке доступных комнат.
После этого REST заново проверяет ресурс, загружает boards/snapshot/code state, и подписка создаётся
через существующий publishActions handler с дедупликацией по destination. 403/404 удаляет intent;
временная API-ошибка сохраняет его до следующего reconnect и показывает ошибку синхронизации.

После соединения API обновляет список комнат, друзей, входящие/исходящие friend requests,
restoreCallSession и историю активной комнаты. Потеря членства очищает выбранную историю.
Если API временно недоступен, прежняя история сохраняется, бесконечной загрузки не запускается.

Signaling disconnect/error не вызывает leave/end LiveKit и не очищает call/activeCall state.
Существующее поведение restoreCallSession сохраняет уже идущую медиасессию/participantToken.
Тест доказывает сохранение Redux call state, а не физическую непрерывность RTP/звука/видео.
Окончание звонка, пропущенное во время offline, и согласованность состояния с реальным LiveKit
требуют ручной приёмки; новый replay/signaling protocol не добавлен.

## 4. История сообщений, live events, pagination

Результат применяется только при совпадении `meta.arg.roomId` с activeRoomId и сохранённого
requestId конкретной страницы. Переключение комнаты/clear инвалидирует запросы. Проверяется
и сценарий A -> B -> A: старый requestId первой A не действует после возврата в A.
Потерявший актуальность многостраничный resync прекращается перед дальнейшей догрузкой.

History и события объединяются через Map по стабильному message.id. Сортировка — sentAt и ID
при равных timestamps; cursor pagination использует минимальный ID. Live журнал текущей комнаты
сохраняет сообщения, изменения и tombstones удаления: HTTP, выполнявшийся параллельно с событием,
не может удалить live message, вернуть удалённое или отменить live edit. Повторное событие не
добавляет второй элемент. hasMore учитывает фактический запрошенный limit, по умолчанию 15.

После reconnect resync загружает latest и последующие страницы существующего beforeMessageId API
до ранее загруженного oldestMessageId либо конца истории. Это включает пропуски длиннее одной
страницы. Проверка строгого уменьшения cursor защищает от зацикливания. Полученный диапазон
заменяет старую историю и объединяется с событиями, пришедшими уже во время resync: пропущенные
редактирования/удаления также могут быть отражены HTTP-историей. Обычная pagination объединяет
страницы и устраняет пересечения по ID. Ошибка HTTP оставляет текущие messages в состоянии.

Общий legacy channelMessage slice не развивался. Дизайн/разметка чата и LiveKit integration не менялись.

## 5. Автоматизированные проверки

Окружение: Node 24.19.0; Java Semeru 21.0.9; Maven 3.9.9; Vitest 5.0.5.
Добавлены только frontend dev dependency Vitest, test script, config и тесты.
Установка/первый Maven запуск упирались в sandbox-доступ к npm/.m2; разрешённые повторные запуски
выполнили реальные проверки. Эти ошибки окружения не выданы за результаты тестов.

| Проверка | Результат | Сравнение с baseline |
|---|---|---|
| Frontend `npm test` | **29/29**, 4 test files | Новый набор |
| Backend RefreshRotationConcurrencyTest | **4/4** | Новый набор |
| S01/S02/S03/S04/S05 + AuthService/MessageService/Conference + concurrency, `package` | **169/169**, JAR собран | Прежние 165/165 + 4 |
| Полный backend `mvn -o package` | **204/218**, 4 failures + 10 errors, 0 skipped; BUILD FAILURE | Прежние 200/214 + 4 успешных, те же 14 падений |
| Frontend `build:web` | **SUCCESS**, 2090 modules | Был успешен, остаётся успешен |
| Полный `tsc -b --pretty false` | **82** прежние диагностики, exit 2 | Было 88; **0 новых**, 6 исчезли |
| `build:electron` | **SUCCESS** | Main/preload компилируются |
| `git diff --check`, оба репозитория | **SUCCESS** | Whitespace-регрессий нет |

Сигнатуры всех 14 падающих backend методов сравнивались с stage2-all-tests.log: различий нет.
Это ChannelControllerTest и CallSessionServiceTest. Новые 4 concurrency cases включены и в полный run.
TypeScript сравнивался по file + TS code + message без номеров строк: новых диагностик нет.
Шесть исчезнувших TS2769 находились в subscriptions.ts и были вызваны неполным дублирующим RootState.
Теперь WebSocket middleware/types используют реальные типы store; RootState выводится из appReducer,
чтобы не создавать циклическое выведение типов store/middleware. Прочие 82 диагностики не исправлялись.

### Что именно тестируется

* `auth.test.ts` — 6 сценариев через реальные Axios interceptors с управляемым adapter:
  10 параллельных HTTP запросов/один refresh/новые Authorization; logout во время refresh и 10 waiters;
  исходный Network Error с реальным по форме auth config; invalid-refresh 401;
  единственный retry и окончательный 401; поздний HTTP response другой сессии.
* `sessionContexts.test.ts` — 5 сценариев: два независимых coordinator и общий cookie lock;
  уведомление logout второй вкладке; ожидание cookie rotation перед revoke;
  отсутствие storage; задержанное межвкладочное уведомление и проверка persisted boundary.
  Web Locks/BroadcastChannel моделируются adapter-ом; это не запуск двух настоящих browser tabs.
* `messages.test.ts` — 9 случаев: PRIVATE/GROUP A -> B, A -> B -> A, live/history merge,
  повторное событие, live edits/deletes, pagination overlaps, сохранение при network failure,
  реальный thunk multi-page reconnect resync, пропущенное удаление и защиту pagination cursor от edit ещё не загруженного сообщения.
* `websocket.test.ts` — 9 сценариев с настоящим Redux store/middleware и управляемым STOMP Client:
  connect/idempotency/personal subscriptions; refresh/new JWT; logout при отказе HTTP и старые callbacks;
  account switch; reconnect/history/live delivery; сохранение LiveKit state; expiry idle socket;
  canvas/code restoration; отказ восстановления ресурса по 403.
* `RefreshRotationConcurrencyTest` — реальные entity mappings, production Spring Data repository,
  production RefreshTokenService и независимые SQL-транзакции H2. Barrier заставляет все 2/10 потоков
  сначала validate одного родителя; затем ровно один commit успешен. Проверяются число строк/активных
  потомков, отказ reuse и rollback потребления вместе с потомком. Репозиторий/БД в этих тестах не mock.
  Это **не** проверка PostgreSQL isolation/locking в реальном стенде.
* Прежние WebSocketAclSecurityTest/WebSocketBrokerSecurityTest используют реальные JWT, Spring broker
  и S02 interceptors; валидный CONNECT и запрет просроченного/отозванного JWT повторно проверены этим run.

Физический WebSocket/SockJS transport, настоящий browser cookie jar и реальная LiveKit медиасессия
не замещаются утверждением об успешности frontend mocks.

### Команды и логи

```powershell
# C:\project\zvonok-backend
$env:JAVA_HOME='C:\Users\Пользователь\.jdks\semeru-21.0.9'
mvn.cmd -o '-Dtest=RefreshRotationConcurrencyTest,AuthServiceTest' test
mvn.cmd -o '-Dtest=RefreshRotationConcurrencyTest,LiveKitSecurityTest,ConferenceServiceTest,StorageHttpSecurityTest,StorageUploadSecurityTest,CallTokenQuerySecurityTest,MessageAccessSecurityTest,MessageHttpSecurityTest,WebSocketAclSecurityTest,WebSocketBrokerSecurityTest,AuthServiceTest,MessageServiceTest' package
mvn.cmd -o package
# C:\project\zvonok-frontend
npm.cmd test
npm.cmd run build:web
npx.cmd tsc -b --pretty false
npm.cmd run build:electron
# В обоих репозиториях
git diff --check
```

Backend: stage3-refresh-tests.log, stage3-security-package.log, stage3-backend-full.log.
Frontend: stage3-frontend-tests.log, stage3-build-web.log, stage3-typescript.log, stage3-build-electron.log.
Исполняемый JAR: `target/zvonok-0.0.1-SNAPSHOT.jar` создан целевым успешным package.
Полный package честно остаётся неуспешным из-за baseline-тестов, тесты не отключались.
Generated electron-dist/main.js и preload.js после проверки возвращены к исходному содержимому;
в diff они не входят. Установка/подпись Electron installer не проверялась.

## 6. Все файлы именно этапа 3

Backend:

* src/main/java/com/zvonok/controller/AuthController.java
* src/main/java/com/zvonok/service/AuthService.java — только @Transactional на refresh поверх S02 изменений.
* src/main/java/com/zvonok/service/RefreshTokenService.java
* src/main/java/com/zvonok/repository/RefreshTokenRepository.java
* src/test/java/com/zvonok/unittests/service/RefreshRotationConcurrencyTest.java — новый.
* CORE-STABILIZATION-STAGE3.md — этот отчёт.
* CORE-STABILIZATION-STAGE3-SECURITY-FOLLOWUP.md — отдельный обнаруженный вне scope риск.

Frontend:

* package.json, package-lock.json
* vitest.config.ts — новый.
* src/api/api.ts, src/api/authApi.ts
* src/api/sessionCoordinator.ts — новый.
* src/helpers/AppInitializer.tsx
* src/services/websocket.service.ts
* src/store/store.ts
* src/store/middlewares/session.middleware.ts — новый.
* src/store/middlewares/websocket.middleware.ts
* src/store/middlewares/websocket/publishActions.ts — friend publishes проверяют connected, а не active.
* src/store/middlewares/websocket/types.ts
* src/store/slices/user.slice.ts
* src/store/slices/websocket.slice.ts
* src/store/slices/message.slice.ts
* tests/setup.ts, tests/auth.test.ts, tests/sessionContexts.test.ts,
  tests/messages.test.ts, tests/websocket.test.ts — новые.

Остальной исходный dirty/untracked diff сохранён. В частности, три frontend Canvas background файла
относятся к этапу 2; другие backend security files и AuthServiceTest не редактировались этапом 3.
`.factorypath` остаётся с прежней пользовательской модификацией, SHA256
`3F78429089C1C0CF3DB229AEA194480ECA7BBC7AAE9E59300C18932F109810AE`.

## 7. Ошибки, ограничения и ручная приёмка

Новых проваленных тестов/TypeScript диагностик не обнаружено. Остаются 14 backend baseline failures,
82 TypeScript diagnostics, прежнее предупреждение о размере frontend chunk и прежние Java warnings.
Это ограничивает утверждение о полной зелёной сборке всего проекта.

1. На тестовом PostgreSQL повторить barrier/concurrent refresh и rollback при ошибке подписи/insert,
   включая конкурирующие application instances. Здесь реально проверена SQL-конкурентность H2.
2. В двух вкладках одного HTTPS origin запустить параллельные пачки запросов около expiry, затем
   logout во время refresh, закрыть/обновить вкладку, сменить аккаунт и проверить отсутствие старой истории,
   callback и скрытого refresh. Проверить Web Locks, storage events и задержку BroadcastChannel.
3. При отсутствии Web Locks локальная очередь сохраняет single-flight одного renderer,
   но origin-wide сериализация нескольких вкладок недоступна. Backend CAS всё равно отвергает reuse;
   проигравшая вкладка может завершить сессию. Для multi-tab режима требуется современный secure context
   с Web Locks. Небезопасный HTTP/нестандартный embedded runtime не объявлены проверенными.
4. Electron main использует defaultSession и single-instance lock; cookies/storage Electron и системного
   браузера независимы. Проверить packaged file origin, Web Locks/storage, reload/tray, offline/logout,
   новый login и secure cookie с фактическими environment API URLs. Здесь выполнена компиляция Electron,
   приложение/installer не запускались. Недоступный persistent storage ограничивает logout marker после reload.
5. Два пользователя: DM и GROUP, быстрое A/B/A, pagination с live/edited/deleted messages,
   outage с пропуском более 15 сообщений и восстановление. Проверить lost membership/удалённую комнату.
6. В активном звонке дождаться expiry access JWT без HTTP-активности; проверить новый handshake,
   отсутствие дублей сообщений/звонков/friend events, canvas/code subscriptions и текущие ACL.
   Отдельно отозвать членство/завершить звонок/закрыть board во время offline.
7. Отключить только signaling при работающем LiveKit, затем восстановить: медиасессия должна продолжаться.
   Проверить пропущенный CALL_ENDED и фактическое состояние комнаты на LiveKit. REST restore здесь
   не вводит новый replay протокол и не обрывает существующий media token только из-за reconnect/NONE.
8. Canvas/code snapshot и STOMP subscribe не составляют атомарный серверный snapshot/replay протокол.
   Их восстановление и membership gate реализованы, но точное согласование edits во время получения
   snapshot требует приёмки; новые серверные event sequence/ACK не вводились.
9. После сетевой ошибки refresh нет слепого retry loop. При появлении сети повтор возможен по online,
   API запросу или connect; при полностью зависшем transport/background throttling таймеры браузера
   не дают гарантии точного времени. S02 продолжает блокировать delivery после JWT expiry.
10. Потеря HTTP-ответа после commit rotation — отдельная неопределённость браузерного cookie transport:
    без нового идемпотентного server protocol нельзя гарантировать восстановление, если новый cookie
    не дошёл. Повторное успешное использование потреблённого родителя намеренно запрещено.
11. Stateless access JWT не получает распределённого revoke от logout; старые токены могут действовать
    до TTL вне управляемого клиента. S02-инвалидация текущих подписок действует на текущем app instance,
    как и до этапа 3. Отдельный серверный session/family registry не добавлялся.
12. Дополнительный риск REST-доступа к canvas/code после отзыва membership/окончания звонка записан
    отдельно в CORE-STABILIZATION-STAGE3-SECURITY-FOLLOWUP.md. Вне scope endpoints не переписывались.

## Итоговые статусы

* **F01 — исправлено**: single-flight, session/cross-tab guards, атомарная ротация и запрет reuse.
* **F02 — исправлено**: один STOMP lifecycle, актуальный JWT, logout teardown, guarded callbacks,
  восстановление subscriptions и API resync; физическая media/browser приёмка остаётся обязательной.
* **F03 — исправлено**: room/request identity, стабильный merge/deduplication, pagination и reconnect history.
