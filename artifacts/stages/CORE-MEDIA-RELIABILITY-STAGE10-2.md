# Zvonok Stage 10.2 — Intermittent One-Way Audio Investigation

Дата: 2026-10-18, Asia/Yekaterinburg. Frontend `C:\project\zvonok-frontend`, backend `C:\project\zvonok-backend`.

## Вывод

**FIXED IN TESTED SCENARIOS** для воспроизведённой гонки первоначального включения микрофона. Пользователь мог включить микрофон до `SignalConnected`; затем `LiveKitRoom` применял default `audio=false`, ожидая уже начатую публикацию, и заглушал подтверждённый track. Один клиент получал remote audio, другой не получал incoming audio RTP при connected PC и существующей subscription.

Минимальное исправление: UI и hotkey не позволяют **включить выключенный** микрофон до `ConnectionState.Connected`. Уже включённый микрофон можно заглушить во время reconnect. Нет forced Room recreation, смены SDK/server/codec/transport, отключения RNNoise, retries или новых продуктовых таймеров. Deadline ожидания аудио остался30s.

Три точных повтора FAIL-сценария после patch — **3/3 PASS**. Финальная серия после patch — **30/30 PASS**, включая6 подтверждённых pre-signal guarded попыток. Ни конечная серия, ни этот вывод не гарантируют абсолютную надёжность интернета, устройств или всех остальных причин одностороннего аудио. Исторический call3 Stage10.1 не имел client pipeline trace; нельзя доказать задним числом, что именно он имел ту же причину. Сейчас аналогичный симптом воспроизведён и его причина установлена на call59.

## Безопасность, baseline и стенд

Stage1–10.1 сохранены. До изменений в обоих репозиториях записаны `stage10-2-evidence/baseline.patch`, `baseline-status.txt`, `source-baseline.zip`. Изучены отчёты Stage10/10.1, исходный media harness, `media-results.json`, три DM repeat JSON, исходный SFU timeout summary/log и актуальный Git diff. `.factorypath`, production и пользовательские PostgreSQL/S3 не затрагивались; коммитов нет. Backend Java source и DB schema в Stage10.2 не менялись. Canvas, Code Sessions, Server/Channel и Hub не восстанавливались; Stage11 не выполнялся.

Использован прежний изолированный стенд: PostgreSQL16.15 `zvonok-stage10-pg`, label stage10, loopback15432, отдельная DB `zvonok_stage10_1`; Java21/Spring Boot3.5.6 loopback18080/api в `ddl-auto=validate`; настоящий LiveKit1.13.10 `zvonok-stage10-1-livekit`, label stage10-1, loopback17880/ICE7881TCP/7882UDP; livekit-client2.17.2; Vite loopback13000; bundled Playwright1.62.1/Chromium151; независимые browser contexts. JWT TTL15s только в тестовом backend. Стандартные invite60/initial120/media grace90/reconcile30 timers не менялись.

Микрофоны синтетические; реальные WebRTC PeerConnection, RTP, SFU и STOMP. S3 не требовался и не запускался. Пользователь сообщил, что его провайдер S3 не оплачен; ни обращения к нему, ни удаления его объектов нет.

## Инструментарий

В существующий `ActiveCallOverlay` добавлен `MediaDiagnostics`, включаемый **только** при DEV и `VITE_MEDIA_DIAGNOSTICS=true`. В production и по умолчанию SDK методы не оборачиваются. Тесты подтверждают gate, сохранение SDK arguments/results/rejections, анонимизацию и detach listeners.

Каждый context сохраняет `sequence`, `performance.now()` monotonic timestamp, Call ID, `mediaGeneration`, Room connection state, анонимные identity p1/p2, local/remote publications с SID/source/muted/subscribed, MediaStreamTrack enabled/muted/readyState. Наблюдаются createTracks, publishTrack request/resolve, LocalTrackPublished (SDK publication ACK), TrackPublished/Subscribed/Muted и обратные события, participant join/leave, reconnect/quality/playback events, React observer/settings/processor mount/unmount и processor setup. Никаких JWT/cookies/API keys, SDP, device labels или candidate IP addresses в diagnostic trace нет.

Playwright init instrumentation наблюдает настоящий getUserMedia, PeerConnections/senders/receivers, AudioContext, AudioTrack playback promises/HTML audio state и worklet destroy requests. Снимки getStats содержат outbound/inbound/remote-inbound audio packets/bytes/loss/jitter/energy, codec, nominated candidate pair type/protocol/RTT; поля записываются только если доступны. Audio output hardware не наблюдаем.

Снимки делаются до connect, на connected/local publication/remote subscription, при mic request, через1/3/5/10s, перед timeout/end и terminal disconnect. Для FAIL полный trace сохраняется **до** hangup и закрытия contexts. Browser wall/monotonic anchors сохранены; межпроцессная корреляция по Call ID/track SID/direction, без предположения идеальной синхронизации браузера и Docker UTC. Момент первого положительного sample — верхняя граница наблюдения, не точная latency первого RTP.

Server collector сохраняет только allowlisted event/RTP fields из SFU logs, не raw SDP/signalling. `analysis.json` сопоставляет обе стороны и upstream/downstream server stats. SFU forwarding наблюдался напрямую по downstream counters на соответствующем SID.

В исследовательской фазе один PASS call18 пересёк HMR диагностического кода и исключён из принятой RTP-выборки. Полная серия30 до fix и все точные/post-fix серии запускались на зафиксированном коде; post-fix SHA256 manifest сохранён. В `src/main.tsx` StrictMode не включён; нельзя приписывать дефект StrictMode без evidence.

## Воспроизведение и доказанная root cause

Первая серия6 и следующая серия30 с обычными последовательностями дали двусторонний RTP. Простая задержка WS в ранних попытках фактически не срабатывала: route устанавливался после navigation/constructor instrumentation; cached WebSocket обходил Playwright shim. Их audio PASS действителен как обычный звонок, но **не доказывает** ранний unmute. Это обнаружено по отсутствию route hits и `microphone.request(true)` уже в connected. Harness исправлен: routing установлен до navigation, hits обязательны, порядок подтверждается trace.

Точный сценарий call59:

1. A вызывает DM, B принимает. Удерживается только успешный signalling handshake A; B подключается к настоящему SFU. Ошибка сервера, fake track или fake RTP не создаются.
2. Пользовательский UI unmute A выполняется пока Room connecting. Настоящий getUserMedia создаёт живой LocalAudioTrack, публикация начинает ждать signal.
3. Handshake A пропускается. Handler `onSignalConnected` установленного `@livekit/components-react/src/hooks/useLiveKitRoom.ts` вызывает `localP.setMicrophoneEnabled(!!audio)`; default `audio=false`.
4. В SDK `LocalParticipant.setTrackEnabled(false)` видит pending microphone publication, ожидает её и затем mute. Публикация получает ACK, но сразу оказывается muted. Такой же вызов false в обычном случае происходит **до** пользовательского true и не создаёт проблему.
5. A получает B; B subscribed на track A, но incoming audio bytes0. Ожидание двустороннего RTP завершается прежним30s timeout.

Локальная последовательность A, `trial-mvdntuqo-01-before-end-A.json`:

| Seq | Monotonic ms | Факт |
|---|---:|---|
|11|1267.845|microphone.request true, connecting — пользовательский UI |
|19|1338.890|publishTrack.request, connecting |
|25|1439.020|microphone.request false, connecting — SDK initial audio=false |
|33|1499.470|publishTrack.resolved, SID подтверждён |
|34|1499.970|livekit.trackMuted |
|42|1518.860|processor setup завершён; это не возвращает microphone enabled |

Самая ранняя доказанная несовместимость с пользовательским намерением — **стадия3, muted state после initial-state callback**, а не отсутствующий capture, server ACK, ICE или autoplay permission. Код источника вызова и отсутствие второго пользовательского клика проверены. Небольшие outbound bytes сами по себе не доказывают передачу нужного аудио: track A уже muted, forwarding к B отсутствует.

## Две стороны FAIL call59

| Стадия | A | B |
|---|---|---|
|Microphone capture|PASS, живой captured audio track|PASS |
|LocalAudioTrack создан|PASS|PASS |
|Microphone enabled после setup|**false**, TrackMuted сразу после ACK|true |
|Publish/server ACK|PASS, SDK LocalTrackPublished + resolved SID|PASS |
|Outbound browser RTP|151 packets /532 payload bytes, нужное аудио не растёт|1482 packets /785553 payload bytes |
|SFU upstream|3644bytes на SID A|842152bytes на SID B |
|SFU forwarding собственного трека|**0bytes к B**|830044bytes к A |
|Inbound browser RTP|1482 packets /785553bytes от B|**0 packets /0bytes от A** |
|Remote subscription|Есть|Есть, несмотря на muted source |
|Attach/play promise|Resolved,1 playing HTML audio element|Promise resolved;0 progressing audio elements |
|AudioContext/output|Программный graph; физическая слышимость UNKNOWN|Физический output UNKNOWN; RTP уже отсутствует раньше playback |

SFU bytes включают иной учёт headers/служебного трафика и не должны равняться browser payload bytes. Полные samples обоих клиентов и SFU rows сохранены. Статистика RTT/loss/codec/candidates не превращалась в p95/throughput или оценку production capacity.

## Исправление и regression

`MicrophoneToggleButton` использует настоящий `useConnectionState`. Disabled сохраняет SDK pending guard и дополнительно запрещает unmute выключенного mic до Connected. Muting уже работающего mic разрешён при reconnect. `CallHotkeys` проверяет тот же смысловой guard по актуальному `room.state`, поэтому keyboard/Electron shortcut не обходит защиту. Публикация после успешного подключения остаётся прежней; Room, RNNoise и backend protocol не менялись.

`microphoneConnectionRace.test.ts`: до patch3 FAIL/3 PASS, после6 PASS. Проверены connecting/disconnected/reconnecting unmute, connected unmute, mute при reconnect и SDK pending disable. `microphoneHotkeyRace.test.ts`:3 PASS — ранний вызов заблокирован, connected разрешён, reconnect mute разрешён. `mediaDiagnostics.test.ts`:4 PASS — dev gate/production off, анонимизация/cleanup, сохранение исходной ошибки. Прежние microphone lifecycle/resource tests сохранены.

Точный before/after: call59 FAIL; calls66/67/68 —3 PASS. Для каждого `preconnectGuardObserved=true`, реальный WS route hit зарегистрирован, начальный false SDK происходит до разрешённого пользовательского true; обе стороны имеют положительные outbound и растущие inbound bytes. Человек видит недоступную кнопку до готовности media; нажатие не обещает включение, которое затем будет молча отменено.

Это исправление подтверждённого пути Zvonok. Нет утверждения о баге любой версии LiveKit или о доказанной причине исторического call3 без его client trace.

## Серии и границы покрытия

Всего **90 DM E2E attempts**, подтверждены также read-only SQL. Raw результаты: **88 PASS /2 FAIL**. Один FAIL — call59 с one-way audio; другой — call23 под критерием немедленного cleanup pending capture. Raw PASS включает отдельный eventual-cleanup repeat call24 и исследовательский call18 с HMR, который исключён из принятой RTP-выборки. Из87 пригодных media attempts (без2 cancellation probes и1 HMR) — **86 PASS /1 FAIL**, все33 media attempts после patch — PASS. Нельзя выдавать1/5 старых попыток за вероятность20%.

| Серия | DM attempts | PASS | FAIL | Интерпретация |
|---|---:|---:|---:|---|
|Первая диагностика|6|6|0|Исходный сценарий и варианты |
|Исследовательская расширенная|10|9|1|Cleanup call23 FAIL; HMR call18 не принят как RTP acceptance |
|Отдельный pending-exit repeat|1|1|0|Eventual SDK cleanup, не двусторонний RTP test |
|Замороженная серия до fix|30|30|0|Обычные варианты; ранние WS labels без hits не засчитаны как pre-signal ordering |
|Проверки неактивного WS routing|3|3|0|Двусторонний RTP действителен, требуемый ранний порядок не достигнут |
|Подтверждённый pre-signal repro|1|0|1|Call59, root cause локализована |
|RNNoise on до fix|6|6|0|Реальный RNNoise processing/RTP |
|Точный сценарий после fix|3|3|0|Calls66–68, early guard proof |
|Финальная серия после fix|30|30|0|Независимый комплект аккаунтов/DM,6 early guarded attempts |

Пример after-fix call66: A outbound254303bytes/492packets, inbound249526bytes/483packets; B outbound249526bytes/483packets, inbound254303bytes/492packets; microphoneEnabled=true у обоих. Выбранные pair/codec/RTT/loss поля обоих клиентов находятся в сырых sanitized traces; значения одиночных samples не превращаются в latency distribution.

| Вариант | Выполнение / проверка |
|---|---|
|Немедленный и delayed accept|Реальные UI calls, задержка accept800ms |
|A раньше B / B раньше A|Успешные token responses одного участника удержаны700ms; фактический порядок SDK events сохранён |
|Одновременные microphones|Два UI clicks параллельно; отдельные publications/SFU media |
|Mic после Connected|Контрольный вариант без initial-state гонки |
|Mic до signal|Call59 воспроизводит one-way; после fix ранний UI unmute блокируется, затем успешен после Connected |
|Повтор в той же DM / быстрый end→new|Та же комната/contexts в каждой серии, следующая попытка после доказанного teardown; по одной CallSession на attempt |
|JWT refresh при подключении|Настоящий refresh HTTP/cookies и TTL15s, естественные token/STOMP renewals; текущий RTP не подменяется моками |
|STOMP reconnect при capture/publish|Настоящее закрытие только backend socket, реальный capture completion удержан500ms; SFU отдельно жив |
|RNNoise on|Отдельная серия6 PASS до fix; GROUP после fix с включённым existing setting, настоящими worklets |
|Exit при pending capture|Отдельные calls23/24: immediate cleanup criterion FAIL; eventual SDK cleanup наблюдался отдельно |

Pending exit: call23 после hangup имел closed PC/AudioContexts, но1 живой поздний captured track; trace сохранён, не стёрт teardown. Повтор call24 показал SDK disconnected-publication timeout15s: через ещё14.097s после первого осмотра track остановлен; итог0live tracks/contexts/peers. Это **не постоянная утечка**, но и **не немедленный capture cleanup**. Изменения ownership/SDK для этого отдельного пути не смешивались с доказанной one-way root cause; требуется отдельная оценка своевременной отмены capture. Audio timeout не увеличивался.

## GROUP, ресурсы и серверная согласованность

До patch GROUP3 PASS (call40). После patch GROUP3 PASS с RNNoise on: у каждого2 incoming audio RTP streams, байты продолжают расти после настоящего STOMP reconnect. Новая backend socket проверена явно; существующий SFU media session сохраняется, CallSession count1.

После завершения GROUP: все captured tracks ended, PeerConnections closed, у каждого4 AudioContexts closed; у каждого1 RNNoise worklet и1 `destroy` request. Это свидетельство запроса destroy и закрытого context, не прямое измерение освобождения WASM heap или аудио качества. Для обычных DM после end open peers/live captured tracks/open contexts0; pending-capture исключение описано выше.

Финальный `server-final-audit.json`:90 новых DM +2 GROUP calls; activeCalls0, activeParticipantsInEndedCalls0, previewMismatches0, настоящий SFU ListRooms пуст. По одному CallSession на попытку проверялось в каждой серии. Не менялись permissions, токены вызова и delivery rules. Backend production data не использовались.

## Финальная регрессия

| Проверка | Результат |
|---|---|
|Backend default|248/248 PASS |
|Backend extended|293/293 PASS |
|Stage8 PostgreSQL query invariants|7/7 PASS |
|Frontend|111/111 PASS; baseline98 +13 новых tests |
|TypeScript|0 errors, tsc-b PASS |
|Web build|SUCCESS; существующее chunk-size warning не скрывалось |
|Electron compile|SUCCESS |
|NSIS/hardware UI|NOT RUN, не заявляется исправленным |
|Scripts parse / Git whitespace|PASS, node --check и git diff --check |

Backend checks повторены после patch в собственных Testcontainers, без пересборки запущенного Windows jar. Security tests не отключались и TypeScript не ослаблялся.

## Изменённые файлы и воспроизведение

Product fix: frontend `src/components/CallUi/MicrophoneToggleButton.tsx`, `src/components/ActiveCallOverlay/CallHotkeys.tsx`.

Test-only observer: новый `src/components/CallUi/MediaDiagnostics.tsx`; подключения/диагностические события в `ActiveCallOverlay.tsx`, `MicrophoneSettingsSync.tsx`, `MicrophoneProcessorSync.tsx`.

Новые tests: `tests/mediaDiagnostics.test.ts`, `microphoneConnectionRace.test.ts`, `microphoneHotkeyRace.test.ts`.

Новые scripts в `scripts/stage10-2/`: `browser-diagnostics.cjs`, `dm-investigation.cjs`, `group-regression.cjs`, `sfu-evidence.cjs`, `server-audit.cjs`, `analyze.cjs`, `README.md`. В README точные команды, env flags, labels, ограничения и безопасная остановка. Отчёт скопирован в оба репозитория. Evidence игнорируются; runtime credentials Stage10.1 не копировались в отчёт или исходники.

Backend product files, application production configuration, package versions, `.factorypath` и schema не менялись. Прежний большой Git diff сохранён и не приписывается Stage10.2.

## Ограничения и дальнейшая аппаратная приёмка

Проверены синтетические устройства и локальный настоящий SFU. Два физических компьютера, слышимость, качество звука, реальные USB/Bluetooth switches, screen capture, TURN relay, Wi-Fi/IP/NAT change, HTTPS/WSS proxy и многочасовой call остаются BLOCKED/NOT RUN. Test source без StrictMode; StrictMode-specific proof не получен. Инструментирование может менять microtask timing; наличие детерминированного FAIL и его исчезновение под тем же observer до/после сильнее обычной успешной серии, но не устраняет все границы.

Аппаратный план: два отдельных тестовых аккаунта на двух физических машинах, реальные microphone/output devices, одновременная речь в обе стороны и запись наблюдений обоих людей; повтор быстрых accept/unmute/end→call, media reconnect при живом STOMP и наоборот; RNNoise settings/device switching;30–60min разговор. Затем staging TLS/proxy/TURN на разных сетях с доказанным relay candidate, отдельными timestamps media loss/first confirmed empty/end и getStats обоих receiver/sender. Не считать browser offline реальной сменой IP. Без production аккаунтов, DB и S3 objects.

## Отдельно: S3 cleanup P2

Статус сохранён **OPEN**: постоянный DELETE503 может оставить orphan, старые аватары сохраняются по существующей политике, согласованного retention period и durable retry нет. Неоплата пользовательского S3 не является проверкой корректности cleanup и не отменяет P2. В Stage10.2 S3 objects не удалялись, новый cleanup subsystem не внедрялся.

Краткий отдельный план: согласовать retention и критерий удаления; определить DB reference/owner guard и поведение UNKNOWN transaction outcome; выбрать durable jobs/outbox либо provider inventory+reconciliation; согласовать требуемую schema/инфраструктуру до реализации; bounded retries/backoff, backlog/dead-letter metrics, dry-run, отдельные disposable buckets; fault E2E с PUT/GET/DELETE503 и recovery. Удалять только доказанно неиспользуемые objects после согласованного периода. Этот план не реализован вместе с media fix.

## Безопасное завершение

Завершение выполнено: synthetic browser processes0; проверены command lines и адресно остановлены только Vite PID2056/backend PID9332; labelled SFU/PostgreSQL — Exited; listeners13000/18080/17880/15432 отсутствуют. DB/evidence сохранены; контейнеры не удалялись, S3 не запускался и его objects не затрагивались. Backend `stage10-2-evidence/shutdown.json` фиксирует фактический результат. Post-fix source SHA256 совпадает с зафиксированным перед финальной серией; в diagnostic JSON/logs JWT-like значения не обнаружены.
