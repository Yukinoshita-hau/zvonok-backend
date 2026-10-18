# Zvonok Stage 10 — End-to-End Reliability & Real-World Acceptance

Дата: 2026-10-18, Asia/Yekaterinburg. Репозитории: `C:\project\zvonok-backend`, `C:\project\zvonok-frontend`.

## Решение

**Not Ready для завершения полной Stage 10 / безопасного выпуска с заявленной real-world acceptance.** Локальные функциональные E2E с PostgreSQL, Spring Boot, React, STOMP и настоящим LiveKit прошли после четырёх минимальных исправлений. Это свидетельство работоспособности проверенных программных путей. Два физических компьютера, слышимость/качество реального звука, аппаратные устройства, screen capture, TURN, TLS/proxy и реальные S3-вложения не приняты. Electron installer в текущем окружении не собирается из-за EPERM. Production capacity не оценивалась.

Stage 1–9 сохранены; Canvas, Code Sessions, Server/Channel не восстановлены. Stage 11/Hub не выполнялись. Коммитов, production операций и обращений к пользовательской БД/S3 не было.

Статусы: PASS — сценарий выполнен с указанным уровнем доказательства; FAIL — выполнен с ошибкой; BLOCKED — отсутствует компонент/доступ; NOT RUN — не запускался. PASS unit/integration не переносится в колонку реального E2E.

## 1. Окружение и baseline

| Компонент | Фактическая доступность и использование |
|---|---|
| Windows / Docker | Docker Desktop 28.4.0; engine доступен вне sandbox. Первоначально running containers отсутствовали. Docker VM RAM 7.445 GiB |
| Host PostgreSQL | Процессы PostgreSQL и listener `5432` существуют; назначение/данные не проверялись, подключения не выполнялись |
| Тестовый PostgreSQL | Реально запущен `postgres:16-alpine`, PostgreSQL 16.15, контейнер `zvonok-stage10-pg`, label `zvonok.acceptance=stage10`, `127.0.0.1:15432`, БД `zvonok_stage10`, новый пароль, без пользовательских volumes |
| Spring Boot | Реальный repackaged jar, Java 21.0.9 Semeru, `127.0.0.1:18080/api`; собственные test JWT/S3/LiveKit overrides, dev cookie profile |
| React | Реальный Vite dev server, `127.0.0.1:13000`, явные `VITE_API_URL`/`VITE_WS_URL` на тестовый backend |
| LiveKit | Реальный `livekit/livekit-server:v1.9.0`, dev mode, `127.0.0.1:17880`, ICE UDP `7882` / TCP `7881` на loopback; image digest `sha256:3602a85840d51981808d0519aefca170a3dbe3dec7bc8c5b87cbd52a503ea89f` |
| STUN/TURN | Выделенного тестового TURN нет; relay route не проверен. Выбранные пары локального прогона — UDP host/prflx |
| S3 | Рабочего тестового endpoint/credentials нет. Загрузка MinIO из Docker Hub отказана `pull access denied`, из quay.io — `unauthorized`. Backend явно направлен на недоступный loopback `19000` с test-only заглушками credentials; fallback к локальным настоящим настройкам исключён |
| Proxy / TLS | Не разворачивались. Проверено прямое HTTP/WS loopback подключение; HTTPS/WSS, forwarded headers, proxy timeouts не приняты |
| Браузеры | Bundled Playwright, Headless Chromium 151; отдельные browser contexts с раздельными cookies/storage, до трёх пользователей. Установленный Edge не использовался для пользовательских сессий |
| Медиа | `--use-fake-device-for-media-stream`, `--use-fake-ui-for-media-stream`; настоящий WebRTC transport и SFU, синтетический источник. Второй компьютер отсутствует |
| Electron | TypeScript main/preload compile доступен; builder 26.15.3 / Electron 43.0.0. NSIS packaging завершился EPERM, GUI/installer acceptance не выполнена |

До правок записаны HEAD, porcelain status и binary tracked patch обоих репозиториев: `stage10-evidence/*baseline.patch`, `*-status.txt`, `*-head.txt`. Отчёты Stage 2–9 и относящиеся к сохранённому ядру изменения/конфигурация изучены. Дополнительно сохранены source-baseline ZIP с прежними untracked source/tests: они реконструированы после правок обратным исключением только добавлений Stage 10; это дополнение к исходным Git evidence, а не утверждение, что untracked ZIP был сделан до начала работы. Secrets-bearing runtime properties в ZIP исключены.

SHA256 source ZIP: backend `39590267FE5F9634B86BC41FF6BC58F2A4716DD7B014D4473F7F1D170C3FF741`, frontend `18B2BA935FE23665752E74563A94567641C1660DDF1EC83E48019E2DE74CAC10`. Frontend reconstruction script сохранён рядом с evidence. Для воспроизведения нужен указанный HEAD + исходный tracked patch + source ZIP, отдельная безопасная конфигурация. Не применять baseline patch поверх текущей работы.

Исходные `application.properties`/`.env` не менялись. Backend не запускался против неизвестной БД с `update`. `create` применялся только к свежему собственному контейнеру/новой БД. Первоначальный порт `55432` оказался в Windows excluded range; пустой не запустившийся Stage10 container удалён и создан на `15432`. Для ICE исправлен только тестовый port mapping, production конфигурация не менялась.

## 2. Схема PostgreSQL / SQL Stage 6

**PASS, настоящий disposable PostgreSQL:** оба `ops/sql/stage6-*.sql` просмотрены и выполнены повторно через `psql -v ON_ERROR_STOP=1` после создания JPA baseline. Таблицы/колонки присутствуют; повторное применение успешно. Скрипты additive, не создают baseline, имеют lock/statement timeouts и проверки типов/nullability; upload script добавляет unique `(sender_id,upload_request_key)`. При отсутствии таблицы они сообщают NOTICE и пропускают работу — поэтому успешный exit сам по себе не подтверждает готовую схему. Готовая тестовая схема дополнительно сохранена через schema-only `pg_dump` в `stage10-evidence/test-schema.sql`.

`ops/stage10-schema.ps1` проверяет label и жёстко использует только созданный контейнер/БД. Скрипты Stage 6 не изменялись, миграции к настоящей БД не применялись. Runtime production `ddl-auto=validate` и фактический catalog deployment требуют отдельного согласованного deployment review.

## 3. Чаты: реальные клиенты и данные

Финальный основной прогон: DM room 19, GROUP room 20; третьему пользователю выдано отдельное GROUP invite. Детальные результаты — frontend `stage10-evidence/browser-results.json`, скриншоты `client-a.png`/`client-b.png`. Создание части fixture выполняется REST, сообщения/звонки проверяются также реальным UI; edit/delete/reply отправлены настоящими STOMP frames, а не кликами context menu.

| Сценарий | Реальный E2E | Что проверено / ограничение |
|---|---|---|
| Регистрация / отдельные сессии | PASS | UI, два независимых cookie/storage contexts, GET profile |
| Friendship / создание DM | PASS | SEND friend request/accept, DM присутствует у обоих; данные сохранены PostgreSQL |
| GROUP создание | PASS | Реальный REST и отображение комнаты у A/B |
| Добавление третьего по invite / duplicate join | PASS | membership в API, третий UI-клиент и звонок; duplicate join не добавляет участника повторно |
| Исключение другого участника | BLOCKED | В текущих RoomController/RoomService нет отдельного kick/remove-member пути. Добровольный leave не выдаётся за исключение; новый протокол не добавлялся |
| DM send A→B / B→A | PASS | Кнопка/Enter UI, текст на обоих клиентах, history API, ровно одна запись каждого текста |
| Edit/delete/reply | PASS | Настоящий STOMP, обновление другого UI и проверка history. Context-menu UX отдельно NOT RUN |
| Read/unread | PASS | Явный REST read для начального boundary, затем новое сообщение вне открытой комнаты даёт unread=1, просмотр с прокруткой до конца даёт 0 |
| Readers badge / индивидуальные receipt UI | NOT RUN | Aggregate unread не считается проверкой отображения списка читателей |
| Preview content | PASS | Новое сообщение совпадает с `lastMessageContent`; SQL preview ID audit без расхождений |
| Сортировка нескольких комнат / preview после offline edit | NOT RUN | Общая программная регрессия отдельно; dedicated UI assertion не запускался |
| Пагинация | PASS | Две страницы по 15 ID без пересечения после 100 реальных сообщений |
| GROUP clear history | PASS | DELETE тестовой истории, другой клиент получает пустую history; SQL preview соответствует |
| Сетевой offline / пропущенное сообщение / reconnect | PASS | Browser-context offline, A сохраняет сообщение, B после online догружает его |
| Отправка текста offline с последующим подтверждением | NOT RUN | Harness вводил draft, не проверял подтверждённую offline send/retry доставку |
| Пропущенные edit/delete offline | NOT RUN | Edit/delete online и missed-message resync проверены отдельно; это не эквивалентно этому сценарию |
| Быстрое A/B/A переключение | NOT RUN | В E2E были переходы между комнатами, без отдельной adversarial race-проверки |
| Attachments / voice / video notes | BLOCKED | Нет рабочего изолированного S3; синтетические байты в persistence suites не считаются E2E upload/playback |
| Повторный multipart upload / большие файлы | BLOCKED | Тот же S3 blocker; integration idempotency/cleanup assertions отдельно проходят |
| JWT refresh во время активности / реальное expiry | NOT RUN | Обычная cookie refresh при reload выполнена; намеренно истекающий JWT и concurrent refresh не проверены E2E |
| Logout с незавершёнными запросами | NOT RUN | Программные session-boundary tests PASS, реальный UI race не выполнен |

Unread-проверка сначала ошибочно предполагала нулевой boundary после burst и автоматическое прочтение всей истории при входе. После явной подготовки boundary и пользовательской прокрутки к концу она прошла. Эти промежуточные timeouts сохранены как observations, не как доказанный исправленный дефект unread.

## 4. Звонки и восстановление

| Сценарий | Реальный E2E | Доказательство / граница |
|---|---|---|
| DM invite → notification → accept → LiveKit | PASS | Два UI, server CallSession, настоящие ICE/PeerConnection connected и inbound RTP |
| Audio transport в обе стороны | PASS | Получены inbound audio bytes на обоих клиентах. Синтетический источник; слышимость и качество человеком BLOCKED |
| Camera on/off | PASS | Синтетическая камера, удалённые `framesDecoded > 0`, обратное выключение UI |
| Screen share / остановка браузером | BLOCKED | Настоящий desktop capture/ручной picker и физический display не проверены |
| DM end / повторный вызов / decline | PASS | PCs закрыты, local captured tracks ended, повторный invite доступен, declined session завершена |
| Missed call / реальный timeout 60s | PASS | Call 21: начат 03:22:32.345, ended CANCELLED 03:23:36.446, UI-наблюдение 64030 ms; scheduler делает истечение дискретным |
| GROUP второй и третий | PASS | Три реальных SFU-клиента, по два входящих audio stream на каждом |
| GROUP leave без end / rejoin | PASS | Остальные остаются, count=2, повторный UI join снова даёт два remote audio stream |
| Поздний join после истечения invite | NOT RUN | Третий join и rejoin выполнены до длительной задержки; >60s late join отдельно не принят |
| Одновременные join/leave | NOT RUN | Последовательные действия проверены, отдельного concurrent lifecycle stress нет |
| Выход мини-режим / последний участник end | PASS | B уходит из mini dock, C уходит, A завершает оставшийся звонок |
| STOMP disconnect при живом медиа | PASS | Закрыт только backend WS, новый STOMP socket, то же количество PC и растущие audio bytes; CallSession сохраняется |
| LiveKit signaling disconnect при STOMP | PASS | Закрыт только SFU signaling socket, STOMP жив, CallSession прежняя. Это не полное отключение media transport |
| Настоящая краткая остановка media SFU | PASS | `docker pause` только Stage10 LiveKit на 6s: RTP bytes 28932→28932, STOMP жив; unpause и рост bytes, тот же call ID |
| Полный internet outage / смена Wi-Fi / IP | BLOCKED | Нет второго компьютера/двух сетей/управляемого network fault стенда. Browser offline не выдаётся за OS/WebRTC internet outage |
| Длительная media loss | PASS | Отдельный реальный звонок: оба browser media-клиента ушли; сервер завершил пустую комнату после policy grace |
| Reload в grace | PASS | C reload, восстановление audio в той же GROUP CallSession |
| Restore внутри grace без media rejoin | PASS | Call 22: через ~10s после ухода обоих клиентов restore вернул тот же callId |
| Restore/token после допустимого периода | PASS | После cleanup call 22: active-call 204, restore NONE, token 403 |
| Пропущенный CALL_ENDED | PASS, server/API уровень | При уходе обоих страниц событие некому получить; последующий restore NONE. Browser UI после именно этого missed event отдельно NOT RUN |
| Expired JWT внутри звонка | NOT RUN | Прошедший cookie refresh/reload не равен реальному JWT expiration |
| Ghost participants / лишние sessions после сценариев | PASS | Финальный SQL: все 22 сессии ENDED, активных participant states в ended calls 0; SFU ListRooms пуст |

**Важная граница grace:** сейчас 90s отсчитываются от подтверждённого `media_empty_since`, после initial-connect 120s и периодического reconciliation 30s. В реальном свежем call 22: started 03:22:39.056523, first empty observation 03:25:06.551291, ended 03:26:36.665137. Пустота подтверждена ~90.114s; от старта до cleanup ~237.609s. Поэтому обещать жёсткое прекращение restore через 90s от потери сети нельзя. Согласовать требуемую product-политику отдельно; таймеры в этом этапе не сокращались.

Первый grace harness имел слишком короткий 180s deadline после начальной проверки и получил timeout из-за этой политики. Он сохранён в `grace-results.json`; продолжение наблюдало **тот же** call 22, не создавало новый: `grace-continuation.json`, SQL timestamps. Исправленный deadline воспроизводимого `stage10-grace.cjs` учитывает initial-connect/sweep. Не скрываем первоначальный timeout.

## 5. Измерения WebRTC / LiveKit

Источник: реальный `RTCPeerConnection.getStats()` каждого браузера, а не sender stats чужого track. Сохранён `webrtc-stats.json`; ранние успешные sample дополнительно в `webrtc-dm-pass.json`.

| Показатель финального DM sample | Наблюдение |
|---|---|
| От UI accept до обнаружения первого inbound audio | A 697 ms, B 700 ms; включает явное включение изначально выключенного микрофона и polling |
| От accept до обнаружения декодированного video | 987 ms; камера включена отдельным действием после audio |
| RTT selected/nominated candidate pairs | 1–2 ms в локальном sample; cumulative route snapshots, не internet RTT/SLA |
| Packet loss sample | audio 0 из 11 received, video 0 из 12 received в приведённом раннем receiver sample |
| Jitter sample | audio 0.001 s, video 0.004 s |
| ICE / PC | connected / connected |
| Route | local host UDP → remote prflx UDP; relay не обнаружен; адреса/порты сохранённого stats ограничены типом/protocol |
| Video sample | bytesReceived=10234, packetsReceived=12, framesDecoded=2 |
| Media pause recovery | после unpause до обнаружения возобновившегося RTP 46 ms; pause=6000 ms |
| Signaling close observation | 62 ms до проверки уже connected PC; медиа могло не прерываться, это **не** полноценная длительность reconnect |

Нет точного UI accept→LiveKit Connected timestamp event log, точного времени слышимого звука, распределения latency/throughput, p95 или статистически значимой оценки потерь. Количество внесённых fault actions известно; полное количество внутренних SDK reconnect attempts не инструментировано. Не выводим его из числа PC или console errors. Небольшие loopback sample не описывают качество реальных сетей.

## 6. Устройства / ресурсы медиа

| Сценарий | Статус | Граница |
|---|---|---|
| Mute/unmute | PASS | Реальный UI с синтетическим microphone track |
| Camera toggle | PASS | Реальное publish/decode синтетической камеры |
| Выбор/переключение аппаратного микрофона и камеры | BLOCKED | Реальные разные устройства не использовались |
| Отказ в доступе | NOT RUN | Permissions в основном E2E были выданы заранее |
| RNNoise on/off / изменение настроек | NOT RUN | Программные processor/resource tests PASS; UI+реальное качество RNNoise не приняты |
| Screen sharing / browser stop / screen↔camera | BLOCKED | Нет ручной capture acceptance |
| Сворачивание / end в mini dock | PASS | GROUP UI |
| Captured MediaStream tracks после DM end | PASS | У A audio/video ended, у B audio ended; PCs closed |
| AudioContext после DM end | PASS | По четыре наблюдавшихся contexts на клиента, все closed |
| WASM processors в реальном звонке | NOT RUN | RNNoise не включался; unit resource cleanup отдельно PASS |

`media-resources.json` содержит фактические состояния. Это не heap/leak test длительностью несколько часов.

## 7. Воспроизведённые дефекты и минимальные исправления

| ID / severity | Воспроизведение и свидетельство до | Root cause / исправление | Проверка после |
|---|---|---|---|
| S10-01 P1, phantom GROUP membership event | PostgreSQL transaction: join по invite, rollback. Membership остался 1, broker получил 2 уведомления. `invite-before.log`: 1 failure | `RoomInviteService.publishJoined` публиковал внутри transaction. Snapshot usernames/payload и существующий `TransactionUtils.runAfterCommit` | Новый persistence regression: rollback без событий, commit по одному событию каждому, duplicate join без событий; extended 289/289. Здесь broker spy, это integration evidence, не настоящий rollback browser E2E |
| S10-02 P1, третий invitee теряет accept | A вызывает GROUP B/C; B принимает раньше C; accept C исчезает. `group-defect-before.json`, три unit cases fail | Broadcast CALL_ACCEPT/CALL_ACCEPTED/CALL_PARTICIPANT_JOINED ошибочно считался собственным accept. В существующей subscription для unanswered GROUP чужой participant event обновляет active-call summary и не переводит личный call state / не запрашивает token | Три regression cases PASS; три browser SFU-клиента принимают последовательно, audio получен каждым |
| S10-03 P1, GROUP rejoin заблокирован | B leave, затем UI «Войти»: token 403, media UI не появляется. `rejoin-defect-before.json`, `rejoin-regression-before.log` | Local closedCallIds не снимался при explicit join; token запрашивался до committed join. Existing prepareJoinedCall вызывается publish middleware; banner ждёт собственного JOINED ACK | Regression PASS, UI rejoin снова получает два audio stream; новый protocol/DB schema не вводились |
| S10-04 P1, живой STOMP помечен offline | Завершение DM во время reconnect/resync; REST restore конфликтует с end, новый call button остаётся disabled при открытом WS. `resync-defect-before.json`, regression fail | Post-connect resync failure использовал transport connectError. Добавлен syncError, сохраняющий transport state и сам текст ошибки | Regression PASS; новый DM после reconnect/end работает; состояние транспорта и sync failure различены |

Изменены только RoomInvite publisher, существующие frontend subscription/publish/banner/WS state и regression tests. Нет массового refactor, новых framework, protocol/schema изменений. Backend count увеличился на 1 integration test; frontend на 5 cases (3+1+1). Уже сохранённые Stage 1–9 tests не отключались.

Оставшиеся особенности: initial-connect/grace policy выше; нет durable outbox для after-commit публикации (crash после commit всё ещё может потерять event), simple broker остаётся single-instance, S3 operational cleanup и ранее выданные media JWT требуют прежних operational мер. В Stage 10 они не объявлены устранёнными.

## 8. Финальная согласованность и логи

Read-only audit `ops/sql/stage10-acceptance-audit.sql` / `stage10-evidence/database-final.txt`:

- 22 CallSession, все ENDED; active=0.
- ACCEPTED/JOINED/RINGING participants у ENDED calls: 0.
- Duplicate burst `(room_id,sender_id,content)`: 0. Это конкретная invariant, не универсальная exactly-once гарантия для любого retry.
- Room preview ID против последнего undeleted message: 0 расхождений.
- 1063 message rows накоплены всеми отдельными прогонами; attachments=0. Подтверждённой потери сохранённых текстовых сообщений в выполненных сценариях не обнаружено.
- Финальный SFU ListRooms: пусто. Browser contexts закрыты. Persistent broker subscription count отдельно не измерен; после браузерных disconnect backend logs показывают remainingSessions=0.
- S3: чтений/записей/destructive tests нет, orphan audit BLOCKED; ноль attachment rows не выдаётся за подтверждение чистоты удалённого S3.
- Завершённые звонки отклоняют выдачу нового token HTTP 403.

Финальный browser run: 0 pageerror, **22 console error entries**: 3 initial refresh без cookie (400), 9 WS `/rtc/v1` handshake 404, 10 resource 404. LiveKit client 2.17.2 успешно fallback-подключается к выбранному server 1.9.0; эти 404 не скрыты и не считаются чистой console acceptance. Требуется отдельная приёмка совместимой server/SDK пары без fallback noise. Ранний backend ERROR относится к неправильному `/friend/accept/undefined` в первой версии harness; исправлено поле requestId, этот запуск не считается дефектом продукта. Воспроизведённые product failures и expected denied token responses сохранены отдельно. JWT/API keys в опубликованные evidence/result summaries не выводились.

## 9. Нагрузка и регрессия

| Проверка | Статус / результат |
|---|---|
| Backend default | PASS: 244/244, 0 skipped, `backend-default-final.log` |
| Backend extended + package | PASS: 289/289, 0 skipped, `backend-extended.log` |
| PostgreSQL optimized query invariants | PASS: 7/7 `CorePerformanceIT`, `stage8.optimized=true`; listing 1/20/200 rooms по 2 queries; history 100/1000/10000 messages по 5 queries |
| Frontend functional regression | PASS: 96/96, 13 files, `frontend-tests-final.log` |
| TypeScript + Web build | PASS: `npm run build` (`tsc -b && vite build`), 0 TypeScript errors; прежнее предупреждение chunk >500 kB сохраняется |
| Electron main/preload compile | PASS: `npm run build:electron` |
| Electron NSIS packaging | FAIL command / BLOCKED environment acceptance: сначала network EACCES, затем вне sandbox и в новом output EPERM rename win-unpacked.tmp→win-unpacked. Текущий installer success не заявляется |
| Десятки/сотни сообщений | PASS: по 100 настоящих STOMP сообщений в повторных browser runs, UI/history/pagination; отдельный persistence dataset до 10000 |
| Одновременные A/B сообщения | PASS: GROUP две UI send операции Promise.all |
| Повторные короткие звонки | PASS: повторный DM, decline и GROUP циклы; не длительный churn benchmark |
| Долгий непрерывный звонок / быстрые reconnect серии | NOT RUN: отдельного soak/netem stress не было |
| Большие attachments в лимитах | BLOCKED: S3 |

Снятый resource sample (не peak): SFU CPU 0.49%, RAM 79.3 MiB, cumulative network 13 MB / 7.83 MB; PostgreSQL CPU 0.76%, RAM 90.81 MiB, network 10.4 MB / 17 MB. Ранний sample: SFU 0.68%/44.17 MiB, PostgreSQL 2.00%/91.5 MiB. Backend финальный sample: working set 368947200 B, private bytes 648245248 B, CPU time 212 s, handles 748. Финальный SQL наблюдал 11 DB connections, включая audit; это не число одновременных пользователей. Временного профиля CPU/memory, peak, throughput, p95 и production capacity нет. SQL performance timings fixture сохранены в логе, не заявляются SLA.

## 10. Воспроизведение и безопасное продолжение

1. Выбрать JDK21, запустить backend `ops/stage10-regression.ps1 -JavaHome <JDK21>`; frontend `scripts/stage10-regression.ps1`. Backend IT сами создают Testcontainers PostgreSQL, не подставлять существующий datasource.
2. Для нового стенда: backend `ops/stage10-start.ps1 -JavaHome <JDK21>`. Он откажется повторно использовать контейнеры с теми же именами, создаёт только новые loopback PostgreSQL/LiveKit. Старые Stage10 ресурсы сначала осмотреть по label; не удалять чужие контейнеры/volumes. Скрипт запускает backend в foreground с новой disposable схемой; не предназначен для restart существующей БД.
3. После boot выполнить `ops/stage10-schema.ps1`. Schema-only dump сохранён; перед использованием сохранённой схемы вместо bootstrap задать `ddl-auto=validate` и отдельные test credentials.
4. Frontend shell: `$env:VITE_API_URL='http://127.0.0.1:18080/api'; $env:VITE_WS_URL='ws://127.0.0.1:18080/api/ws-raw'; npm.cmd run dev -- --host 127.0.0.1 --port 13000 --strictPort`.
5. `node scripts/stage10-acceptance.cjs`. Playwright берётся из bundled runtime; для другого ПК задать STAGE10_PLAYWRIGHT на уже установленный package. Новая npm dependency не добавлена. Только при явном использовании собственного labelled SFU: `STAGE10_SFU_OUTAGE=1` включает pause/unpause ровно `zvonok-stage10-livekit`, unpause находится в finally.
6. Отдельно `node scripts/stage10-grace.cjs` (до ~5 минут по текущей политике). `stage10-grace-observe.cjs <test-username> <callId> <roomId>` продолжает конкретную Stage10 fixture, не создаёт новый звонок. Затем SQL audit и `node scripts/stage10-sfu-audit.cjs`.
7. Не публиковать runtime configs/JWT/browser storage. Evidence локальные; новые аккаунты используют `s10...` / `@test.invalid`, не существующих пользователей.

Безопасный S3 стенд: сначала обеспечить доступ к доверенному MinIO image/зеркалу с фиксированным digest; новый контейнер без host data mounts, только `127.0.0.1:19000:9000`, отдельные случайные MINIO_ROOT_USER/MINIO_ROOT_PASSWORD. Создать новый bucket `zvonok-avatars` (имя сейчас hardcoded в S3Service), backend указать только этот endpoint/test credentials. Затем UI multipart→download обеими разрешёнными сессиями и deny outsider, retry clientRequestId, rollback cleanup и manifest objects до/после. Не использовать настоящий bucket даже при совпадении имени.

TURN/TLS/два ПК: отдельный тестовый host/domain и credentials, ограниченные firewall ports по выбранному deployment, HTTPS/WSS сертификат и auth cookie/CORS origins под этот стенд. Настроить LiveKit/TURN и проверить getStats selected pair `relay` при принудительном relay, затем две независимые сети и Wi-Fi/IP switch. Развёртывание на реальном deployment здесь не выполнялось. Базовый dev запуск сверялся с [официальной LiveKit local guide](https://docs.livekit.io/transport/self-hosting/local/).

## 11. Оставшиеся acceptance gates / release blockers

- **BLOCKED:** два физических ПК, реальная двусторонняя слышимость/эхо/качество, переключение USB/Bluetooth/camera, permissions denied, RNNoise quality, browser/Electron capture/stop, screen audio. Записать наблюдателя, OS/browser versions и receiver stats каждого участника.
- **BLOCKED:** действующий изолированный S3 и полный attachment/voice/video-note/security/orphan matrix.
- **BLOCKED:** TURN relay, TLS/proxy, реальные network change/IP switch и длительные controlled media/network outages.
- **BLOCKED:** успешный NSIS package и запуск установленного Electron с test endpoints. Уточнить причину Windows EPERM/AV/file lock отдельно, не удаляя существующий пользовательский release.
- **NOT RUN:** реальное JWT expiry во время media, logout/in-flight races, missed offline edits/deletes, simultaneous join/leave stress, long-call soak, UI readers badge.
- **NOT RUN / требуется решение:** принять или изменить фактическую initial-connect+media-grace политику; не обещать «90 секунд от потери сети» по текущим данным.
- Console без ошибок пока не принята: проверить согласованную LiveKit SDK/server пару и убрать воспроизводимый `/rtc/v1` fallback noise на тестовом deployment.

Локальное программное ядро прошло перечисленные функциональные проверки после исправлений. Перечисленные аппаратные/инфраструктурные gates остаются обязательными для полного Stage 10 sign-off.


После audit собственные backend/Vite процессы остановлены; оба labelled Stage10 контейнера остановлены (Exited 0), тестовая БД сохранена в контейнере для осмотра. Runtime production/host PostgreSQL не изменён. Дополнительный packaging с electronDist не дал обхода: node_modules/electron/dist/electron.exe отсутствует.

