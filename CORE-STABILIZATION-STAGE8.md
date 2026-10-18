# Zvonok Core Stabilization — Stage 8: Performance & Scalability

Дата: 2026-10-18, Asia/Yekaterinburg. Репозитории: `C:\project\zvonok-backend`, `C:\project\zvonok-frontend`.

## Результат и границы

Устранено линейное увеличение SQL-запросов списка комнат и N+1 загрузки attachments страницы истории. Пересекающиеся WebSocket room refresh объединены без таймеров; полный profile event обновляет normalized users без HTTP. После browser-профилирования добавлена виртуализация длинного DM/GROUP списка с измеряемыми высотами строк.

Существующие незакоммиченные изменения Stage 1–7 сохранены; перед правками просмотрены status/diff обоих репозиториев и материалы SECURITY-P0, Stage 2–7/security follow-up/cleanup. Новые коммиты, изменения `.factorypath`, production/пользовательской БД, схемы, transport/codec/QoE, Canvas/Code Sessions, Server/Channel runtime и Stage 9 не выполнялись. Сторонние зависимости не добавлены.

Все цифры ниже получены на синтетических fixtures. Это НЕ production-метрики, не p95 и не SLA. Уменьшение числа запросов и DOM — детерминированный результат; время одного запуска зависит от JIT, Docker/host load, planner statistics и dev browser instrumentation.

## Методика и воспроизводимость

Backend: Maven 3.9.9, Java 23 (compile release 21), disposable PostgreSQL 16-alpine / PostgreSQL 16.15 Testcontainers. Приложение/production datasource не запускается. `CorePerformanceIT` использует Hibernate-generated create-drop schema, реальные repositories/JPA и read transaction, `EntityManager.clear()` перед измерением, Hibernate Statistics (`prepareStatementCount`, `entityLoadCount`, maximum HQL execution time). UserService и внешние зависимости — mocks. Поэтому таблица НЕ включает реальный поиск пользователя: обычный username lookup добавляет отдельный SQL; поведение реального UserService не ускорялось.

Listing: 1/20/200 комнат с двумя общими участниками, по 100 чужих видимых сообщений; отсутствующие read states — отдельная важная ветка. History: 100/1 000/10 000 сообщений, загружается ровно 50. Перед/после — одна и та же fixture и граница измерения. Hibernate first-level cache холодный, второй cache не включался. Все fixture-транзакции откатываются, контейнеры удаляются Testcontainers. Проверки корректности отдельно покрывают PRIVATE/GROUP, read boundary, self/deleted, membership и inactive rooms.

Frontend: Vitest с mock STOMP и настоящими Redux reducers/thunks; отдельный Node reproduction observer lookup; отдельный Redux benchmark 1 000/5 000/10 000. Browser benchmark использует настоящий `DmItemsList`, React Profiler, Redux и MemoryRouter в IAB Chromium, dev Vite, viewport списка 600 px. Без StrictMode; тексты одного дня, без media для сопоставимого timing run. Дополнительный acceptance run включает reply, variable text height, синтетическое изображение через настоящий Blob loader, prepend, teardown. Его render counts НЕ смешиваются с timing run.

Команды (из соответствующего репозитория):

```powershell
# Backend, отдельно от обычного CI
mvn -o '-Dtest=CorePerformanceIT' '-Dstage8.optimized=true' test
mvn -o '-Dtest=CorePerformanceIT,MessageConsistencyPersistenceIT,CallLifecyclePersistenceIT' '-Dstage8.optimized=true' test
mvn -o test
# Расширенная проверка всего кроме известного ChannelControllerTest; включает также IT
mvn -o '-Dtest=!ChannelControllerTest' test

# Frontend
npm test
node benchmarks/history.mjs
npx vitest run --config vitest.performance.config.ts
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 3188
# Browser: http://127.0.0.1:3188/benchmarks/history.html
# Scroll/media acceptance: тот же URL + ?acceptance=1
# Переход 480 -> 510: ?acceptance=1&threshold=1
npx tsc -b
npm run build:web
npm run build:electron
git diff --check
```

Timing benchmarks не используют миллисекундных pass/fail thresholds. Query invariants при `stage8.optimized=true`: listing <=3 statements, history page <=8. Обычный `mvn test` не включает `*IT`; frontend benchmarks исключены обычным Vitest include. Timing assertions отсутствуют. Scroll acceptance использует геометрическую погрешность <2 px, строгие checks reply/edit/bottom и resource counters, а не время выполнения.

## 1. Bottlenecks и SQL baseline

| Сценарий | До SQL | После SQL | Entities до / после | elapsed до / после, ms |
|---|---:|---:|---:|---:|
| Listing 1 room | 5 | 2 | 3 / 3 | 252.282 / 252.902 |
| Listing 20 rooms | 62 | 2 | 22 / 22 | 240.271 / 26.600 |
| Listing 200 rooms | 602 | 2 | 202 / 202 | 1493.665 / 90.650 |
| History 100, page 50 | 54 | 5 | 53 / 53 | 89.230 / 139.028 |
| History 1 000, page 50 | 54 | 5 | 53 / 53 | 45.504 / 37.495 |
| History 10 000, page 50 | 54 | 5 | 53 / 53 | 57.267 / 69.115 |

Источники: backend `stage8-baseline-performance.log`, `stage8-final-performance.log`. Listing Hibernate max HQL time: 29/5/18 ms до и 38/6/32 ms после для 1/20/200. Это maximum отдельного HQL запроса, НЕ сумма JDBC time. Отдельные aggregate-unread/DTO-serialization/JDBC-total CPU timings не инструментировались; end-to-end service mapping включён в elapsed. Serialization speedup не заявляется. History elapsed на двух масштабах не улучшился в этом запуске: гарантируется уменьшение round trips, а не обещание заданной latency.

### Listing и unread

До: batch read-state entities + отдельный count каждой комнаты; при unread — повторный lookup read state и lookup первого сообщения; lazy members — ещё запрос каждой комнаты. Fixture без read state выполняла `3N+2` SQL. При существующем state добавляется запрос первого unread Message и его eager associations.

После: отдельный listing-specific fetch join участников, без pagination коллекции, и один scalar aggregate `count/min` по разрешённым room IDs. Нулевые комнаты получают count=0/null. Не загружаются unread Message/RoomReadState entities. Исходный общий repository method оставлен для остальных callers; новый query используется только listing. DTO остаётся прежним, entities не сериализуются наружу. Полные User/Room для участников всё ещё загружаются, потому entities в текущей fixture не уменьшились; member DTO projections — возможная отдельная работа после profiling памяти.

`LEFT JOIN RoomReadState` ограничен `(userId,roomId)` с существующим unique constraint. Условия `sender<>self`, `deletedAt is null`, `id>lastRead`, отсутствующий/null state сохранены. `min(id)` даёт первый unread также при отсутствующем state: прежний listing в этой ветке показывал count>0, но firstUnread=null. Membership/active фильтруются самим listing query до передачи IDs aggregate. Публичных endpoints/прав доступа не добавлено.

Transaction boundaries: write/clear/edit/delete/afterCommit Stage 6 не менялись. Listing получил явный `@Transactional(readOnly=true)`, чтобы fetch, aggregate и DTO mapping выполнялись в сервисной транзакции, без зависимости mapping от OSIV. PostgreSQL isolation не повышался: это не обещание serializable snapshot при конкурентных сообщениях. History по-прежнему работает в существующей транзакции MessageService.

Отрицательные последствия: один aggregate может читать много подходящих сообщений; fetch join возвращает несколько строк на room/member, хотя Hibernate дедуплицирует roots. Нельзя механически переносить этот collection fetch на pageable listing. При огромном числе комнат потребуется отдельная оценка IN-параметров и pagination, без нового cache layer.

### History N+1

До: 50 отдельных lazy selects attachments, даже если коллекции пусты. После: `@BatchSize(50)` на lazy attachments; pageable room history fetch graph загружает только to-one sender/room. Collection fetch join в paginated SQL НЕ используется, лимит остаётся в PostgreSQL. Страница 50 укладывается в 5 statements, число загруженных Message остаётся 50. Reply preview batching Stage 6 и ID ordering не менялись.

Отрицательные последствия: batch может инициализировать до 50 managed collections, включая ещё не отображаемые; это bounded read amplification. Страницы больше 50 требуют нескольких batches. Нет глобального EAGER attachments и нет in-memory pagination. `Page` всё ещё может выполнять count query — переход на Slice не реализован без отдельного доказательства.

## 2. PostgreSQL EXPLAIN ANALYZE

Сырые `EXPLAIN (ANALYZE,BUFFERS)` сохранены в `stage8-postgres-regression.log`, строки `STAGE8,plan-analyzed`. Отдельно выполнен ANALYZE тестовых таблиц. Первые before/after plans без ANALYZE имели устаревшую оценку rows=1, Seq Scan + Sort; использовать их как доказательство необходимости индекса нельзя.

| Набор | History после ANALYZE | Buffers hit | Execution ms | Unread aggregate plan | Execution ms |
|---|---|---:|---:|---|---:|
| 100 | Index Scan Backward message_pkey + Limit 50 | 4 | 0.090 | GroupAggregate / Nested Loop Left Join / Seq Scan | 1.052 |
| 1 000 | Index Scan Backward message_pkey + Limit 50 | 3 | 0.062 | тот же | 1.064 |
| 10 000 | Index Scan Backward message_pkey + Limit 50 | 4 | 0.084 | тот же | 5.692 |

Для unread фактические входы — 100/1 000/10 000 messages, один room group; read-state scan материализован, а не запрашивается из Java на каждую комнату. У fixture высокое покрытие запрошенной комнаты и отсутствующий state. Таблицы содержат следы rollback предыдущих fixtures; физическая плотность и shared hits не моделируют production. Это проверка правильности SQL/planner, не capacity model.

Новых индексов, SQL/schema scripts нет. Существующий PK уже поддержал LIMIT для выбранного распределения. Кандидат partial `(room_id,id) WHERE deleted_at IS NULL` требует representative interleaved rooms, unread selectivity/read-boundary distribution и insert/soft-delete benchmark; он добавит index maintenance на записи. Без такого обоснования индекс не установлен даже на пользовательский стенд.

## 3. WebSocket-triggered HTTP

| Сценарий | До room-list HTTP | После |
|---|---:|---:|
| Синхронный burst 100 сообщений неактивной комнаты | 100 | 1 |
| События, поступившие после начала snapshot | по событию | 1 in-flight + trailing refresh для накопленных событий |
| Полный USER_PROFILE_UPDATED | 1 | 0 |
| Одиночное неполное message event | 1 | 1 authoritative refresh |

`stage8-ws-baseline.log` и `stage8-final-tests.log`; actual Axios adapter requests counted through mock STOMP. Profile baseline=1 установлен по прежнему безусловному вызову; after=0 проверен adapter assertion, отдельного browser timing baseline для profile не снималось. Комнатные/friend fallback events внутри подписок используют ту же очередь. В message path удалён повторный upsert одного sender.

Очередь запускается в microtask, без debounce timer и произвольной задержки. В один момент выполняется один queued refresh. Любое событие во время HTTP делает очередь dirty: после ответа обязательно запускается trailing snapshot. Во время непрерывного потока могут следовать несколько последовательных snapshots, а не фиксированный лимит два навсегда. Redux asyncThunk rejection освобождает очередь. Caller использует существующий session/connection-guarded dispatch; после teardown queued callbacks не запускают HTTP. Unit tests проверяют failure/recovery и teardown.

Почему одиночный message event ещё делает HTTP: payload не содержит authoritative unread state, preview после delete предыдущего latest и состояния других devices. Локально обновляется загруженная history; unread/preview не угадываются. Profile event содержит необходимые scalar поля и применяется к normalized users; room DTO пересоздавать не нужно.

RequestId/session epoch guards не удалены. Reconnect сохраняет полный room/friend/call/history resync. После await message handler проверяет актуальную activeRoom, чтобы delayed refresh не переключил комнату, выбранную пользователем. Inactive-room message не вызывает history HTTP. Clear сохраняет authoritative history resync, чтобы не потерять сообщения после clear. Между message refresh queue и независимыми HTTP mutations/reconnect возможны отдельные перекрывающиеся requests; глобальное переустройство thunk concurrency не делалось. Промежуточный snapshot во время burst может кратко отставать, завершающий snapshot восстанавливает актуальность. Нового room cache нет.

## 4. Большие истории: алгоритмы, React и DOM

Observer registration раньше искал каждую ссылку через `messages.find`; Map заменяет квадратичный проход. В Node reproduction:

| Messages | До сравнений | После visits (Map build + lookups) | До / после ms |
|---|---:|---:|---:|
| 1 000 | 500 500 | 2 000 | 4.271 / 0.438 |
| 5 000 | 12 502 500 | 10 000 | 103.372 / 1.359 |
| 10 000 | 50 005 000 | 20 000 | 366.777 / 1.948 |

Это isolated algorithm reproduction, НЕ React/DOM timing. Map хранит ссылки на существующие messages, не копии payload; цена — O(N) дополнительного индекса. Меню также использует этот Map. После удаления/clear processed receipt IDs очищаются от отсутствующих сообщений.

Browser profiling после Map всё ещё показал серьёзную DOM нагрузку; поэтому внедрена windowing, а не заранее выбранная технология. Сопоставимый run (600 px, одни и те же texts/author update):

| Messages | DOM до / после | mount ms до / после | author update ms до / после | React mount commits до / после | update commits до / после |
|---|---:|---:|---:|---:|---:|
| 1 000 | 8 003 / 119 | 335.650 / 51.305 | 487.015 / 61.515 | 1 / 2 | 1 / 5 |
| 5 000 | 40 003 / 146 | 2947.265 / 77.510 | 1474.475 / 64.035 | 2 / 4 | 1 / 5 |
| 10 000 | 80 003 / 146 | 5412.350 / 117.410 | 5334.625 / 62.610 | 2 / 4 | 1 / 5 |

Profiler render duration для 10k: mount 2729.195 -> 83.540 ms, update 2700.280 -> 47.575 ms. Commit count вырос из-за geometry measurement; утверждение «renders стало меньше» НЕ делается. Снижен объём работы каждого render и число DOM nodes. JSON: `benchmarks/react-baseline.json`, `react-final.json`. Baseline snapshot `DmItemsList.stage8-baseline.txt` снят после Map, до windowing; он не является полной копией git HEAD.

`useVirtualRows` применяется от 500 chat rows. Smaller lists сохраняют обычную разметку. Window использует pixel overscan, binary search offsets, ResizeObserver и стабильные message keys. Высоты текста/изображений измеряются, при смене ширины оценки инвалидируются. Editing row pinned даже вне viewport. Reply к загруженному, но невидимому message прокручивает window к его key. Divider/attachments/reply/edit UI сохранён. Prepend и изменение высоты выше viewport используют key+pixel anchor; native scroll anchoring отключён в virtual branch, чтобы не получить двойную компенсацию. При bottom mode сохраняется follow-tail. Сохранена смена activeRoom/requestId guards; legacy prepend handler больше не корректирует scroll второй раз для virtual list.

Read IntersectionObserver по-прежнему disconnect/re-register при изменении данных/окна; теперь он обходит только mounted refs с O(1) lookup и видит новые rows при scroll. Отдельный observer без пересоздания не внедрялся без дополнительного bottleneck. ResizeObserver/listeners/rAF очищаются, reply timers отменяются при смене комнаты/unmount. Blob ownership в MessageAttachments не переписывался: его существующий teardown проверен настоящим компонентом и synthetic Blob download.

Отрицательные последствия windowing: offscreen DOM отсутствует для browser find/accessibility traversal; изображения при повторном входе в window могут загружаться повторно после revoke Blob URL. Estimates могут уточняться на ранее невиданных строках. Layout/Redux по-прежнему O(N), data retention не ограничивался: усечение могло бы сломать reconnect/delete/reply semantics. Это не замена полноценной product acceptance на реальных Electron/media clients.

### State retention и память

Отдельный настоящий reducer benchmark (`stage8-history-redux.log`):

| Messages | Load reducer ms | Edit ms | Retained до switch | После switch |
|---|---:|---:|---:|---:|
| 1 000 | 8.953 | 3.243 | 1 000 | 0 |
| 5 000 | 24.156 | 8.223 | 5 000 | 0 |
| 10 000 | 41.581 | 15.917 | 10 000 | 0 |

Strict checks: unique IDs, порядок, edit, clear message/live state на room switch. Browser после unmount: DOM=0, active observer instances=0, outstanding созданных Blob URLs=0; после switch messages=0. Это resource/retention checks, НЕ доказательство полного heap leak absence. Retained data во время чтения остаётся N; heap bytes, CPU%, process RAM, GC и p95 не измерялись достоверно и не заявляются.

## 5. Корректность и регрессия

| Проверка | Результат |
|---|---|
| Frontend до Stage 8 | 79/79 |
| Frontend после | 89/89, 12 files |
| Отдельный Redux benchmark | 3/3 |
| PostgreSQL performance + consistency + lifecycle | 40/40 (7 новых, 33 предыдущих) |
| Backend full default | 247 tests: 244 pass, 3 прежних legacy Channel failures, 0 errors |
| Backend расширенный, явно без ChannelControllerTest | 284/284, включая все IT |
| TypeScript | ровно 12 прежних Server/Channel diagnostics; новых core errors нет |
| Web | SUCCESS, Vite; остаётся предупреждение chunk >500 kB |
| Electron | SUCCESS |
| git diff --check обоих репозиториев | PASS |

Известные full-suite failures не исправлялись: ChannelControllerTest `getChannelShouldReturnChannelWhenUserCanViewIt`, `getChannelShouldReturnForbiddenWhenChannelAccessDenied`, `getChannelShouldReturnNotFoundWhenChannelDoesNotBelongToFolder`.

Новые strict tests: query upper bounds, PRIVATE/GROUP absent/read-boundary/self/deleted summaries, inactive/nonmember filtering, empty room IDs, retained/unique/order/history edit/switch, coalescing + trailing snapshot + failure + teardown, complete profile event no HTTP, variable-height binary lookup/anchor. Прежние suites покрывают stale HTTP, duplicate message/edit/delete, pagination, reconnect missed events, room switching, clear-after-send, S3/WS/JWT/room authorization, transactions/afterCommit.

Browser acceptance отдельно от обычного CI: 1k/5k/10k, prepend, expansion выше viewport, Blob image height, reply к unloaded DOM, follow-tail, pinned editor и cleanup; дополнительно переход threshold 480 -> 510. Геометрия проверяется в fixture, raw результаты отображаются в `#results`. Живые calls/hardware/Electron UI во время этого этапа не запускались.

## 6. LiveKit/audio audit

Изучены ActiveCallOverlay/CallLifecycle, memoized RoomOptions, CallAudioLayer placement и Stage 7 audio ownership. Обычное message state не входит в selector ActiveCallOverlay; RoomOptions стабилен, CallAudioLayer не переносился в условную ветку expanded UI. Новый LiveKit Room/AudioContext/RNNoise runtime в этом этапе не добавлен. CallQualityController не активирован. Предыдущие lifecycle/resource tests прошли. Реальные audio CPU/memory и hardware/network приёмка остаются за границами измеренного результата; media performance improvement не заявляется.

## 7. Все файлы именно Stage 8

Backend (пути от `C:\project\zvonok-backend`):

- `src/main/java/com/zvonok/repository/RoomReadStateRepository.java`
- `src/main/java/com/zvonok/repository/RoomUnreadSummary.java` (новый scalar projection)
- `src/main/java/com/zvonok/repository/RoomRepository.java`
- `src/main/java/com/zvonok/service/RoomReadStateService.java`
- `src/main/java/com/zvonok/service/RoomService.java`
- `src/main/java/com/zvonok/repository/MessageRepository.java`
- `src/main/java/com/zvonok/model/Message.java`
- `src/test/java/com/zvonok/integrationtest/CorePerformanceIT.java`
- `CORE-STABILIZATION-STAGE8.md` (копия отчёта)

Frontend (пути от `C:\project\zvonok-frontend`):

- `src/components/DmItemsList/DmItemsList.tsx`
- `src/components/DmItemsList/useVirtualRows.ts`
- `src/components/DmItemsList/virtualLayout.ts`
- `src/store/middlewares/websocket/subscriptions.ts`
- `src/store/middlewares/websocket/refreshQueue.ts`
- `tests/websocket.test.ts` (добавлены 2 проверки)
- `tests/performance.test.ts`, `tests/virtualHistory.test.ts`
- `vitest.performance.config.ts`
- `benchmarks/history.mjs`, `benchmarks/history.test.ts`
- `benchmarks/history.html`, `benchmarks/react-history.tsx`
- `benchmarks/DmItemsList.stage8-baseline.txt`
- `benchmarks/react-baseline.json`, `benchmarks/react-final.json`, `benchmarks/react-acceptance.json`, `benchmarks/README.md`
- `CORE-STABILIZATION-STAGE8.md`

Существующие Stage 1–7 изменения остальных файлов, package/lock и generated electron files не приписываются Stage 8. Запущенные сборки обновляют ignored `dist` и могут воспроизвести прежний electron-dist output.

Локальные ignored evidence logs: backend `stage8-baseline-performance.log`, `stage8-final-performance.log`, `stage8-postgres-regression.log`, `stage8-full-tests.log`, `stage8-targeted-tests.log`, `stage8-diff-check.log`; frontend `stage8-baseline-tests.log`, `stage8-ws-baseline.log`, `stage8-history-baseline.log`, `stage8-history-redux.log`, `stage8-final-tests.log`, `stage8-typescript.log`, `stage8-build-web.log`, `stage8-build-electron.log`, `stage8-diff-check.log`, `stage8-format.log`, `stage8-vite.log`, `stage8-vite-benchmark.log`. Maven XML под `target/surefire-reports` перезаписываются следующими запусками, итог каждого запуска закреплён отдельным log.

## 8. Оставшиеся проблемы и отложенные оптимизации

- Нужен production-like interleaved/unequal room dataset и repeated warm measurements; текущий single-sample timing не даёт p95/capacity/throughput.
- Проверить compound/partial message indexes с репрезентативной selectivity и write overhead; не устанавливать на основании stale-statistics Seq Scan.
- Добавить отдельный стресс-набор с множеством reply parents от уникальных отправителей: parent lookup остаётся batch по IDs, но его eager associations пока не профилировались отдельно от обычной страницы.
- Отдельно профилировать member scalar DTO projections и serialized payload bytes; сейчас устранены round trips, не entity footprint.
- Расширить browser/Electron acceptance: реальные большие video/audio attachments, быстрый resize/scroll, keyboard/accessibility и изображения по реальной сети. Resource counters не заменяют heap snapshots.
- Подтвердить необходимость bounded history cache, более эффективного reconnect accumulation и линейных Redux insert/merge вместо sorting; текущие 10k reducer timings сами по себе не оправдывают смену архитектуры/усечение истории.
- При развитии server event contract можно доставлять authoritative per-user unread/preview и убрать single-message list refresh; сейчас это было бы угадыванием состояния.
- Глобальное объединение запросов mutations/reconnect, Slice вместо Page и bundle splitting требуют отдельных метрик/контрактов.
- Прежние 12 legacy TS diagnostics и три Channel failures остаются. LiveKit hardware/media acceptance и эксплуатационные риски предыдущих этапов не закрываются этим этапом.

Stage 9 не начат.
