# Zvonok Stage 10.1 — Complete Automated E2E Acceptance

Дата: 2026-10-18, Asia/Yekaterinburg. Репозитории `C:\project\zvonok-backend`, `C:\project\zvonok-frontend`.

## Решение

**Not Ready для безопасного выпуска с полной real-world acceptance.** Автоматизируемые пробелы существенно закрыты настоящими PostgreSQL/S3/STOMP/LiveKit и отдельными browser cookie jars. Исправлены три подтверждённых дефекта: rollback аватара, unread короткой истории, scroll anchor UI-пагинации. Остались воспроизведённый intermittent DM one-way RTP timeout и отсутствие гарантированного cleanup при длительном S3 DELETE outage. Аппаратная приёмка, TURN и TLS/proxy отсутствуют. Успешные повторы не отменяют сохранённый FAIL.

PASS означает выполненный сценарий только на указанном уровне. FAIL — выполнен с ошибкой; BLOCKED — отсутствует необходимая среда; NOT RUN — не выполнен. Unit PASS отдельно от реального E2E. Медиа синтетические: SFU/WebRTC настоящие, слышимость и качество оборудования не приняты.

Stage 1–10 сохранены. Коммитов, production операций, изменений `.factorypath`, production TTL, протокола, схемы продукта, codecs или транспорта нет. Stage 11/Hub не начаты. Два Stage 6 SQL изучены и дважды выполнены только на `zvonok_stage10_1`; повторное выполнение успешно. Исторический Stage 10 DB прочитан для grace timestamps, не изменялся.

## Baseline и evidence

В обоих репозиториях до Stage 10.1 правок сохранены `stage10-1-evidence/baseline.patch`, `baseline-status.txt`, `source-baseline.zip`. Изучены Stage 9/10 отчёты, текущие исходники, Git diff и Stage 10 scripts/evidence. Backend runtime secrets и пользовательские конфигурации в source ZIP не включались. Каталоги evidence игнорируются локальным `.gitignore`; `runtime.env` не публиковать.

Evidence frontend: `auth-results.json`, `s3-results.json`, `s3-delete-results.json`, `chat-results.json`, `history-results.json`, `history-ui-results.json`, `unread-before-fix.json`, `unread-after-fix.json`, `media-initial-pass.json`, `media-results.json`, `media-dm-repeat-{1,2,3}.json`. Исходные harness failures сохранены отдельно: не являются продуктовыми дефектами (неоднозначные selectors, soft-delete SQL oracle, незавершённый login redirect).

Evidence backend: Maven logs, `schema-review.log`, `database-final-audit.txt`, `call-timeline-and-invariants.txt`, `dm-timeout-rtp-summary.json`, `dm-timeout-server-observation.log`, resource samples, MinIO build log. Не выводились JWT, refresh cookies или API/S3 credentials.

## Реально использованная инфраструктура

| Компонент | Фактическая конфигурация |
|---|---|
| PostgreSQL | 16.15, контейнер `zvonok-stage10-pg`, label `zvonok.acceptance=stage10`, loopback 15432; отдельная новая DB `zvonok_stage10_1`, user stage10. Старый `zvonok_stage10` сохранён |
| Backend | Java 21.0.9, Spring Boot 3.5.6, loopback `18080/api`; первоначальный create только свежей DB, все последующие старты validate. TTL 15000 ms для Auth/media, 300000 ms для storage/chat; env overrides только тестового процесса |
| Frontend | React/Vite loopback 13000, API/WS явно на тестовый backend. Реальный STOMP `/api/ws-raw` |
| LiveKit | `livekit/livekit-server:v1.13.10`, digest `sha256:89ca9ce0791e18d6e03bf6e2c49ceb27e1c754eae8bd50c989386c1a5963791a`; label stage10-1; loopback 17880, ICE TCP7881/UDP7882. Старый v1.9.0 контейнер не удалялся |
| S3 | Настоящий MinIO, собранный из официального pinned upstream `RELEASE.2025-04-22T22-12-26Z` через Go module checksum verification; commit `0d7408fc9969`, image `sha256:7ca316baeb2c0b98b995b361d23ae170597d604630cbc635d3f39e2ef1b49006`. Это не mock |
| S3 isolation | `zvonok-stage10-1-s3`, label stage10-1, loopback19001, tmpfs `/data` 512MB, memory1GB/2CPU, uid65534, без host data volume; новый bucket `zvonok-avatars`, случайные test-only credentials |
| S3 failure injection | Loopback19000→реальный MinIO19001; control19002. Proxy не хранит объекты, инжектирует HTTP503 для PUT/GET/DELETE. Admin audit использует настоящий SigV4 напрямую к19001 |
| Browsers | Уже доступный bundled Playwright/Headless Chromium151, независимые contexts и настоящие HttpOnly cookie jars, две вкладки одного context для shared-cookie refresh |
| Медиа | Synthetic microphone/camera, реальные RTP/ICE/SFU; до трёх пользователей в GROUP |
| Отсутствует | Второй физический компьютер, аппаратные устройства, выделенный TURN, TLS/reverse proxy acceptance. Electron runtime/installer UI не запускались |

Docker Hub MinIO image был недоступен, quay unauthorized, официальный binary archive вернул410. Без непроверенного образа использована воспроизводимая сборка официального [MinIO source](https://github.com/minio/minio), `ops/stage10-1/minio.Dockerfile`. Production S3 конфигурация/ACL-проверки не менялись. После длительного перерыва остановленные известные контейнеры были проверены и возобновлены; ошибки PostgreSQL connection refused в старом backend log относятся к остановке стенда.

## Настоящий S3 E2E

| Сценарий | Статус | Доказательство / граница |
|---|---|---|
| Аватар upload и повторный upload | PASS | Реальный HTTP200, exact bytes в MinIO, новый URL; старый объект сохраняется по существующей retention-политике |
| Изображение DM и GROUP | PASS | Реальные file controls + send; получатель декодирует картинку; attachments подтверждены SQL |
| Private ACL / anonymous denial | PASS | GetObjectAcl: owner FULL_CONTROL, без AllUsers/AuthenticatedUsers; anonymous GET403; финальный anonymous HEAD403 для всех17 объектов |
| Download участником / посторонним | PASS | 200 vs404; exact bytes, MIME image/png, size68, inline disposition, nosniff |
| Потеря GROUP membership | PASS | Download404, upload403, новых объектов нет |
| Voice, VIDEO_NOTE, VIDEO | PASS | Синтетические валидные MediaRecorder WebM, настоящий multipart/storage/download; audio decode/video metadata успешны. Это HTTP-path, не проверка кнопок product recorder |
| Один clientRequestId, повтор и5 конкурентных retries | PASS | Один Message ID/SQL row и один новый объект, loser uploads удалены |
| S3 PUT503 / GET503 | PASS | Backend error при outage, нет нового message/object при PUT; после reset GET200 с прежними bytes |
| PostgreSQL rollback после attachment PUT | PASS | Scoped test trigger, HTTP500, SQL0rows, object set неизменен, trigger удалён |
| PostgreSQL rollback после avatar PUT | PASS после fix | До fix новый orphan; после fix новых объектов0, профиль не подтверждён |
| Постоянный S3 DELETE503 при rollback | FAIL | После SDK retries остался1 новый orphan; evidence сохранено, после reset удалён только этот SQL-unreferenced тестовый key. Автоматической durable retry гарантии нет |
| MIME/magic/oversize | PASS | Три invalid cases отвергнуты400/413/422 до изменения storage; image10MB+1 отвергнут |
| Верхняя допустимая граница большого video / HTTP upload connection reset | NOT RUN | Не доказаны большой допустимый файл, multipart body truncation и disconnect; five concurrent complete uploads их не заменяют |
| Product UI voice/video recording buttons | NOT RUN | Валидные upload fixtures не равны взаимодействию с recorder controls |

Финальный manifest:17 объектов,14 уникальных DB references,3 unreferenced objects от прежней avatar retention/доисправительного rollback. Это **не PASS глобального отсутствия orphan**. Новые rollback/retry проверки после fix не добавляют orphan при доступном DELETE; объект persistent DELETE fault удалён контролируемой диагностической очисткой. Старые аватары не удалялись: `StorageUploadSecurityTest.ownerUploadsAndReplacesAvatarWithoutDeletingOldObject` явно закрепляет их сохранение. Retention/GC нужно согласовать отдельно.

## Auth / Session: реальные браузеры

Все7 сценариев `auth-results.json` прошли повторно на финальном коде:

| Сценарий | Статус | Наблюдение |
|---|---|---|
| Реальное истечение access JWT | PASS | Raw browser token с server TTL15s после exp получает401; cookie refresh200, новый JWT авторизует200 |
|10 concurrent refresh callers | PASS | Один network refresh, одна действующая refresh row PostgreSQL |
|10 protected HTTP requests через refresh | PASS | Все авторизованы; реальное ожидание времени, expiry отдельно доказан raw token |
|2 вкладки, общий cookie jar | PASS | Browser locks сериализуют refresh; оба200, одна active refresh row |
| Logout при pending refresh | PASS | Удержан ответ настоящего backend; logout fulfilled, late refresh cancelled, Redux cleared, reload→login |
| Смена аккаунта | PASS | UI login нового аккаунта, старой private room/messages нет, подключённая STOMP identity только новая |
| Invalid/revoked refresh | PASS | Настоящие browser cookies в отдельных jars,401→login |
| JWT expiry при живом звонке / новый STOMP token | PASS в успешных DM повторах | Старый JWT401, token changed, WebSocket identity совпадает с аккаунтом, PC не пересоздан, RTP растёт. DM intermittent failure ниже учитывается отдельно |

Secure-cookie поведение через настоящий HTTPS proxy не проверялось: здесь localhost dev-cookie profile.

## Adversarial chat: UI + PostgreSQL

`chat-results.json` повторно PASS на окончательном коде. Фикстуры комнат/инвайтов создавались REST; это не UI acceptance их создания.

| Сценарий | Статус | Проверка |
|---|---|---|
| UI A→B→A, задержанный HTTP B | PASS | Реальные sidebar clicks, backend snapshot удержан; ни одного B message в A; оба клиента совпадают с SQL |
| Новый WS event во время history snapshot | PASS | Новый send реальным UI, затем старый HTTP response; подтверждённый message сохранён на обоих клиентах |
| Offline edit/delete | PASS | Один context offline; контекстное меню Edit/Delete другого; после reconnect старый текст/удалённый message отсутствуют; SQL soft-delete учтён |
| Конкурентные send/edit/delete + resync | PASS | Два UI клиента, настоящая HTTP thunk resync, итог соответствует SQL, duplicate IDs0 |
| UI clear + немедленный peer send | PASS | Danger zone→confirmation→DELETE200→send; новый подтверждённый message сохранён, оба клиента и SQL сходятся |
| Reconnect и повторное открытие разных комнат | PASS |4 sidebar transitions, messages scoped, preview соответствует последнему live SQL message |
| Unread + read receipt | PASS после fix |1→0, `✓→✓✓`, ровно1 reader row; без дополнительного scroll при целиком видимой короткой истории |
|620 сообщений / virtualization | PASS |621 total SQL rows с первым fixture, уникальность content/ID;500 loaded,20 rendered. Bulk send — настоящий STOMP dispatch, не620 UI действий |
| Прямой HTTP-thunk prepend | PASS | Видимый anchor shift0px |
| Настоящий wheel UI prepend | PASS после fix |500→515 сообщений, native mouse wheel вызывает автоматическую HTTP pagination; shift0px. До fix730px |

Во всех сверках authoritative источник — отдельная PostgreSQL DB; tombstones не сравнивались с видимыми сообщениями как активные rows. После финального сценария preview audit mismatches0.

## LiveKit compatibility и звонки

Причина прежних404 установлена по установленному SDK и официальным исходникам: livekit-client **2.17.2** сначала использует `/rtc/v1`; server **1.9.0** этого endpoint не регистрирует и SDK делает legacy fallback. В [v1.9.0 rtcservice](https://github.com/livekit/livekit/blob/v1.9.0/pkg/service/rtcservice.go) отсутствует v1 route; [v1.13.10 rtcservice](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/rtcservice.go) регистрирует `/rtc/v1` и `/rtc/v1/validate`. Установлен именно этот официальный server image; SDK и serverUrl path не менялись, serverUrl `ws://127.0.0.1:17880`.

На проверенной паре фактические RTC WS paths только `/rtc/v1`, fallback404/RTC console errors0. Это доказанная совместимость данного стенда, не обещание совместимости любых версий. Production upgrade не выполнялся.

| Сценарий | Статус | Evidence |
|---|---|---|
| DM UI call/accept, RTP в обе стороны, camera on/off | FAIL в одном прогоне; отдельные повторы PASS | Всего5 DM попыток: первый PASS, повторный полный suite FAIL по audio30s,3 отдельных повтора PASS. Не считать общий сценарий стабильно принятым |
| Authorization / call token outsider | PASS в успешных DM |403/404, без выдачи чужого token |
| Missed CALL_ENDED, browser жив, STOMP recovery | PASS | Event действительно отсутствует в frames, SFU/reconciliation снимает call; PC0, tracks ended, AudioContexts closed |
| GROUP third invitation expiry | PASS | Реально ожидалось66.193s в финальном suite; уже работающий звонок продолжается |
| GROUP late join после expiry | PASS |3 участников, incoming audio от двух, тот же CallSession |
| Reload активного GROUP | PASS | Восстановление без ручного join (`explicitJoin=false`), входящий RTP, CallSession count1 |
| Конкурентные GROUP leave/join | PASS | Participants3→1→3; одна CallSession |
|2 STOMP сбоя +2 SFU pause/unpause | PASS | Backend и media faults разделены; восстановление RTP, без новой CallSession |
| Короткая synthetic soak | PASS | Финальный GROUP age99.377s, предыдущий104.383s. Это не длительный многочасовой звонок |
| Cleanup / ghost state | PASS | Финально7 ENDED calls, active0, active participant states в ended calls0; peers/tracks/context cleanup в выполненных сценариях |
| Длительный звонок часами | NOT RUN | Накопления многочасовой статистики нет |
| Реальный Wi-Fi/IP/NAT change, TURN relay | BLOCKED | Нет второго сетевого/физического стенда и TURN |
| Повтор grace-expiry на новой server version | NOT RUN | Политика не менялась; ниже отдельно историческое Stage10 evidence |

**Незакрытый P1: intermittent DM one-way audio startup.** `media-results.json` сохраняет call3 timeout на ожидании входящего audio bytes>0 (30s). Серверный filtered evidence показывает downstream1501 packets/815850bytes одному участнику, у другого downstream counters отсутствуют. Причина не локализована; browser receiver snapshot именно этого первого timeout не был собран, добавлен сбор для следующих отказов. Три последующих независимых DM повтора успешны и не воспроизвели отказ. Это не повод менять SDK/codecs наугад или закрывать дефект. Следующий шаг: повторная серия со снимками local publication/mute/publish events + outbound/inbound stats обоих участников, разделить capture/publish/subscribe/transport; затем минимальный patch и тот же regression.

## Измерения медиа и grace

Последний успешный DM (`media-dm-repeat-3.json`, call7): incoming audio379198bytes, packetsLost0, jitter0.001s; video1604bytes,4 decoded frames, packetsLost0; selected candidate pair RTT0.001s, UDP host/prflx; ICE/PC connected. Ранний успешный sample имел RTT0.049s и jitter0.01s. Это отдельные getStats samples синтетического loopback прогона, **не p95**, не throughput benchmark и не слышимость. Полные candidate/stat fields сохранены там, где браузер их предоставил.

Время accept→connected, первого входящего аудио/видео и каждого reconnect не было инструментировано достаточно точно для нового latency распределения. Не вычислялись вымышленные p95, средняя потеря пакетов или production capacity. Ожидание invitation и duration synthetic soak измерены скриптом;30s timeout — deadline, не измеренная latency успешного вызова.

Таймеры неизменны: invite60s; initial connect120s; media grace90s от подтверждённой пустоты; reconcile30s.

| Сценарий / источник | Начало | Реальная потеря media | Первая подтверждённая пустота | Окончательное завершение |
|---|---|---|---|---|
| Stage10.1 DM call7, host end |14:30:54.389501 | Отдельная точная метка не собиралась | Не требовалась |14:31:10.000585, HOST_ENDED;15.611s от начала |
| Stage10 invitation expiry, call21 |03:22:32.345046 | Не было принятого media | Нет |03:23:36.445774, CANCELLED;64.101s от начала |
| Stage10 empty-room grace, call22 |03:22:39.056523 | Точная departure timestamp не зафиксирована |03:25:06.551291 |03:26:36.665137;237.609s от начала,90.114s от первой подтверждённой пустоты |
| Stage10.1 GROUP invitation третьего пользователя | Звонок продолжался | Media остальных живы | Нет | Invitation исчезло через66.193s наблюдения; это не время завершения CallSession |
| Новый controlled120s initial-connect timeout / новый90s boundary | NOT RUN | NOT RUN | NOT RUN | Старые измерения не объявляются новым тестом server1.13.10 |

Timestamps — локальные значения PostgreSQL; server SFU logs UTC. Отсутствующая точная media-loss метка не заменяется `media_observed_at`. Новая политика не предлагалась и не реализовывалась.

## Дефекты, минимальные исправления и регрессия

| Дефект | Приоритет | До / root cause | Исправление / повтор |
|---|---|---|---|
| Новый avatar orphan и phantom profile при DB rollback | P1 | Реальный MinIO PUT прошёл, PG trigger откатил update; uploadAvatar не имел rollback cleanup, publisher вызывался до commit.4 unit tests до patch:3 FAIL/1 PASS | Только новый key удаляется при STATUS_ROLLED_BACK; UNKNOWN не удаляет потенциально committed object; notification через существующий TransactionUtils.afterCommit.4 tests PASS, реальный rollback S3 E2E PASS; прежние аватары сохраняются |
| Unread остаётся1 при видимой короткой истории | P2 | geometry scrollHeight=clientHeight=854, top0, reader row1, room cursor0; skeleton scroll оставлял isAtBottom=false | useLayoutEffect пересчитывает реальное bottom после succeeded; реальный unread regression без scroll PASS, общий history/chat повтор PASS |
| UI prepend двигает видимое сообщение | P2 |730px drift; date-divider key заменялся при prepend, anchor терялся; последующие измерения partial row и loading flow добавляли drift | Anchor на surviving row/следующей видимой границе; skeleton только для пустой initial history.2 unit regressions, direct+native wheel E2E shift0px |
| One-way DM RTP timeout | P1, открыт | Один из5 прогонов FAIL, root cause не установлен | Evidence + richer timeout capture; предположительный product patch не внесён |
| S3 DELETE outage orphan | P2, открыт | Все DELETE retries503; best-effort callback не может гарантировать уборку | Только контролируемая очистка тестового orphan; требуется отдельный durable retry/GC план, не новая архитектура в этом этапе |

Отдельный план для S3 cleanup: согласовать retention old avatars; определить возраст/owner/reference guard; выбрать durable cleanup jobs/outbox либо провайдерный inventory+reconciler; предусмотреть retry/backoff, метрики backlog, dead-letter и dry-run; обсудить schema/infrastructure до реализации. Не удалять живой объект по transient SQL error или UNKNOWN outcome.

## Финальная регрессия и серверная согласованность

| Уровень | Статус | Результат |
|---|---|---|
| Backend default | PASS |248/248; Stage10 baseline244 +4 новых avatar tests |
| Backend extended | PASS |293/293; baseline289 +4 |
| Stage8 PostgreSQL optimized query invariants | PASS |7/7, собственный Testcontainers PostgreSQL |
| Frontend | PASS |98/98; baseline96 +2 virtual anchor tests |
| TypeScript | PASS |0 errors, `tsc -b` в `npm run build` |
| Web production build | PASS |Vite SUCCESS; существующее предупреждение о крупном chunk не скрывалось |
| Electron main/preload compile | PASS |`npm run build:electron` SUCCESS |
| Electron NSIS package/runtime UI | NOT RUN |Необязателен для Stage10.1, предыдущий EPERM не объявляется исправленным |
| Git diff whitespace | PASS |`git diff --check`; старые LF/CRLF warnings не являются ошибками |

Финальный read-only SQL audit: active calls0; active participant states в ended calls0; preview ID mismatches0; upload request duplicate groups0; Stage10.1 fault triggers0. Настоящий SFU admin ListRooms вернул пустой список (`sfu-final-audit.json`). Сохраняются законные message tombstones. STOMP/RTC contexts закрыты после scripts; media cleanup доказан в выполненных сценариях, не глобальная гарантия отсутствия любого resource leak.

Resource snapshot во время media: PostgreSQL74.6MiB/CPU0.02%; LiveKit65.91MiB/CPU6.63%; MinIO88.2MiB/CPU0%; Java working set355065856bytes, накопленный CPU94.47s; DB connections11. Это единственный snapshot, не peak/p95 и не benchmark. Сетевые Docker counters накопительные; throughput/SQL CPU attribution не выводились.

## Файлы Stage 10.1

Product changes backend: `src/main/java/com/zvonok/service/UserService.java`; новый `src/test/java/com/zvonok/unittests/service/AvatarTransactionOutcomeTest.java`.

Product changes frontend: `src/components/DmItemsList/DmItemsList.tsx`, `src/components/DmItemsList/virtualLayout.ts`, `tests/virtualHistory.test.ts`.

Новые backend tools: `ops/stage10-1/start.ps1`, `infrastructure.ps1`, `minio.Dockerfile`, `regression.ps1`.

Новые frontend tools в `scripts/stage10-1/`: `common.cjs`, `auth.cjs`, `s3.cjs`, `s3-client.cjs`, `s3-proxy.cjs`, `s3-delete.cjs`, `storage-audit.cjs`, `sfu-audit.cjs`, `chat.cjs`, `unread.cjs`, `history.cjs`, `history-ui.cjs`, `media.cjs`, `run.ps1`, `regression.ps1`, `README.md`.

Отчёт `CORE-ACCEPTANCE-STAGE10-1.md` в обоих репозиториях. Игнорируемые evidence/runtime artifacts перечислены выше; Web/Electron outputs пересобраны. Остальной большой Git diff относится к сохранённым Stage1–10 и пользовательской работе, не приписывается этому этапу.

## Воспроизведение, завершение и оставшаяся приёмка

Порядок запуска, label checks, loopback ports, build, suites и cleanup описаны в `C:\project\zvonok-frontend\scripts\stage10-1\README.md`. Используется существующий Playwright, новый framework не добавлен. `s3-delete.cjs` намеренно выдаёт FAIL на подтверждённом незакрытом условии; runner не маскирует это успешным expected-failure.

Безопасное завершение фиксируется в `stage10-1-evidence/shutdown.json` backend: проверка PID/command line тестового backend18080, Vite13000, proxy19000/19002; прекращение только этих процессов; stop только известных labelled Stage10.1 MinIO/SFU и Stage10 PostgreSQL; неизвестные контейнеры не удаляются. MinIO tmpfs после stop теряет тестовые объекты; DB/evidence и остановленные контейнеры сохранены. Manifest сохранён до stop. Это уничтожение disposable тестового storage, не решение retention/orphan проблемы продукта.

Завершение проверено: все три контейнера Exited, перечисленные loopback listeners отсутствуют. Stop-Process для двух Node PID вернул NullReference; после повторной проверки command lines применён адресный taskkill, порты проверены повторно. Неизвестные процессы не завершались, контейнеры не удалялись.

План аппаратной приёмки (BLOCKED сейчас):

1. Два физических клиента, отдельные тестовые аккаунты, третий GROUP клиент при возможности. Один desktop/Electron и один Chrome/Edge; реальные микрофоны, камеры, два выходных устройства. Проверить слышимость обоих направлений, mute, mic/camera switch, отказ permission, RNNoise on/off, настройки, screen capture и browser stop sharing, свернутый call/end, release tracks/AudioContexts/WASM.
2. Отдельное тестовое HTTPS/WSS окружение с reverse proxy, доверенным TLS и тестовым TURN; доказать relay candidate pair на ограниченной сети, сохранить getStats обоих участников и SFU logs. Не использовать production endpoints/credentials.
3. Разные реальные сети, Wi-Fi→другая сеть/внешний IP, короткий/длительный disconnect; независимо рвать STOMP/SFU, проверять90s confirmed-empty grace с отдельными timestamps loss/first-empty/end; не приравнивать browser offline к NAT change.
4. Повторить DM start/accept/decline/missed/repeat, GROUP late/concurrent join/leave/reload, потерю CALL_ENDED; локализовать intermittent DM audio до закрытия P1. Длительный звонок30–60min и серия коротких вызовов с ресурсами/слышимым аудио, без обещаний production capacity.
5. Повторить S3 authorization/ACL на реально выбранном staging provider, TLS cookies/session rotation, большой допустимый video, interrupted multipart, production-подобные proxy limits. Согласовать retention и durable cleanup до заявления об отсутствии orphan.

NSIS EPERM diagnostic plan: на отдельном output path сохранить точную операцию/stack, проверить owner/ACL и handles Process Explorer/Handle, antivirus/Controlled Folder Access и блокировки cache/output; воспроизвести из чистого пользовательского staging build directory; повторить packaging после устранения конкретной причины. Не удалять пользовательские releases/cache целиком, не отключать защиту и не менять signing/release configuration без согласования.

Блокеры безопасного выпуска: незакрытый DM P1; согласованная гарантия S3 cleanup/retention; аппаратные media/TURN/TLS/network checks. Локальные PASS не закрывают эти условия.
