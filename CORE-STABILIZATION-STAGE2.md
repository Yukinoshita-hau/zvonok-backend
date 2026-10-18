# Zvonok Core Stabilization — этап 2: LiveKit и S3

Дата: 2026-10-17 (Asia/Yekaterinburg).
Исходный backend HEAD: `6f8d9b54de01e7a494f6a2804382356b74f615ac`.

## Результат и границы проверки

S03: обход авторизации при выдаче LiveKit-токенов устранён в приложении.
S04: устранены общие операции перезаписи/удаления по ключу и обход доменной авторизации при чтении через API.
Полное эксплуатационное закрытие S04 требует проверки прямого доступа к bucket: приложение не может
отменить публичный доступ, разрешённый внешней bucket policy/CDN или ACL старого объекта.
Новые загрузки явно получают private ACL. Production и политики хранилища не изменялись.

Схема БД, существующие S3-ключи, пользовательские данные, legacy Channel/Server и B01 не изменялись.
Коммиты не создавались. `zvonok-code-runner` не изменялся. Изменения S01/S02/S05 сохранены;
существующая модификация `.factorypath` не редактировалась.

## 1. Подтверждённые проблемы

* `GET /livekit/token?room=...` подписывал произвольное имя комнаты без участия в звонке.
* Основной call-token путь проверял запись участника и статусы, но не текущее членство в комнате,
  активность комнаты и `leftAt`; заключительный SQL/HQL update также не проверял эти условия.
* Установленный `livekit-server 0.7.0` принимает TTL в миллисекундах. Передача `minutes * 60`
  давала примерно 0,6 секунды вместо заявленных 10 минут. Подтверждено исходниками установленного SDK
  и проверкой `exp` в подписанном JWT; исправлено.
* Общий `/s3/upload` использовал исходное имя как ключ, позволяя конфликт/перезапись; общий `/s3/delete/{key}`
  не проверял владение или доменный объект.
* Публичный `/s3/download/**` не разрешал объект через доменную модель. Само знание ключа не является правом.
* Аватары использовали имя пользователя и timestamp, вложения добавляли непроверенное расширение имени.
* Проверка файлов в основном полагалась на заявленный MIME. Аватары не имели отдельной проверки типа/размера.
* Download вложений проверял комнату, но не общую политику сообщения, включая удаление сообщения.
* `PUT /users/my` позволял присвоить произвольный `avatarUrl`: после введения публичности через профиль
  это могло бы использоваться для публикации чужого storage key. Этот путь также ограничен.

## 2. S03: реализованное решение

Legacy endpoint сохранён как закрытый маршрут: с JWT возвращает 403, без JWT — 401.
Не выполняет подпись и не принимает произвольное имя комнаты как авторизацию.
Неиспользуемый метод `LiveKitTokenService.generateToken` удалён.

Основной путь остаётся `POST /calls/{callId}/token`. Комната берётся из сохранённого CallSession.
Нужны текущий участник активной комнаты, запись CallParticipant без `leftAt` и статус ACCEPTED/JOINED.
Для DM допустим ACTIVE/RINGING, чтобы инициатор мог подключиться до ответа адресата;
адресат со статусом RINGING токен не получает до принятия. Групповой звонок должен быть ACTIVE.
Заключительное условное обновление после обращения к LiveKit повторно проверяет статусы,
`leftAt`, активность комнаты и текущее членство. При отзыве доступа подпись не выполняется.

`CallRestoreService` продолжает использовать тот же защищённый issuer. Проверено восстановление
допустимого участника и отказ после отзыва членства. Входящий RINGING по-прежнему возвращает приглашение,
а не даёт медиатокен без принятия. Создание, accept/join/leave/end не перепроектировались.

Конференции сохраняют существующую модель: JWT пользователя + действительный код ACTIVE-конференции
разрешают join; неизвестный код и ENDED не разрешают выдачу; завершать может хозяин.
Это приглашение по ссылке, отдельного списка предварительно одобренных участников в модели нет.

Grants: roomJoin, точное roomName, publish, subscribe, publishData. Административные права,
создание/перечисление комнат, запись и управление участниками не выдаются.
publishData нужен существующему `CallChatPanel` с LiveKit `useChat`.
В JWT проверяются identity, room binding, отсутствие admin/create grants и фактический TTL.

## 3. S04: реализованное решение

### Политика чтения и совместимость

Пользователь явно выбрал: закрыть непривязанные объекты, сохранить публичные аватары,
проверять вложения по членству, фоны досок — по участию в звонке. Файлы и ключи сохраняются.

`S3ObjectAccessService` разрешает объект **до** обращения к S3:

1. Все ссылки MessageAttachment с данным storageKey проходят существующий MessageAccessService.
   Учитываются текущая комната/членство, удалённое сообщение и существующая channel-политика без её расширения.
2. Фон CanvasBoard требует активной доски, ACTIVE-звонка, ACCEPTED/JOINED участника без `leftAt`
   и текущего доступа к комнате. Все найденные ссылки должны пройти проверку.
3. Публичное чтение разрешено только для текущей ссылки аватара пользователя или активной комнаты.
4. Неизвестные ключи и непривязанные приватные namespaces закрыты. Ошибки проверки не обходят ACL.

Приватные ссылки проверяются раньше публичных: присвоение того же объекта публичному профилю
не снимает защиту вложения/фона. Ключи с обходом пути, обратным слешем и управляющими символами отвергаются.
Общий download понимает вложенные ключи; ID-based download вложений сохраняет прежний URL и DTO.
Ответы получают `private, no-store`, `nosniff` и безопасный Content-Disposition.
Для старых объектов отсутствующий/опасный MIME заменяется на octet-stream; содержимое не удаляется.

Существующие ключи не переименовываются. Проверены старый аватар, аватар комнаты,
старое вложение с расширением и аватар без MIME. Старые непривязанные файлы остаются в bucket,
но перестают отдаваться через API согласно выбранной политике. Аватар после замены также
остаётся физически сохранён; если ссылок больше нет, общий API его не публикует.

### Запись, замена и удаление

Общие `/s3/upload` и `/s3/delete/{key}` возвращают 403 авторизованному пользователю.
Произвольные объекты не имеют достоверной ownership-записи, поэтому универсальное удаление
не разрешается даже по префиксу имени. Рабочие загрузки идут через существующие доменные endpoints.

Аватар загружается для identity из JWT. Новый ключ — `avatar_<UUID>`, без имени/расширения клиента.
Повторные одинаковые имена не конфликтуют. Ссылка профиля меняется только после успешного put;
старый объект не удаляется. Ошибка хранилища оставляет прежнюю ссылку.
`PUT /users/my` принимает прежнее значение avatarUrl или отсутствие изменения;
для нового аватара требуется `/users/upload-avatar`. UI уже использует этот путь.

Новые вложения используют `message-attachments/<messageId>/<UUID>` без расширения клиента.
Исходное имя остаётся метаданными/названием скачивания. Фоны уже использовали UUID и доменные проверки записи.

В общем storage-адаптере добавлены MIME allowlist, проверка сигнатуры контейнера и размеров:
изображения до 10 MiB, audio до 25 MiB, video до 100 MiB; пустые/неположительные размеры запрещены.
Аватар допускает PNG/JPEG/GIF/WebP. Сохраняются прежние более узкие доменные ограничения,
число вложений, duration, правила фонов и серверные multipart limits.
Проверка сигнатуры не является полной проверкой декодером, антивирусом или защитой от всех media exploits.

## 4. Frontend и API-контракты

Проверены `callApi`, `conferenceApi`, `livekitApi`, `userApi`, `messageAttachmentsApi`,
AccountSetting, CallChatPanel и использование backgroundImageUrl.

Сохранены:

* `/calls/{id}/token`, `/calls/restore`, call join/accept/leave/end и форматы call DTO.
* `/conferences`, `/{code}/join`, `/{code}/end`, поля token/serverUrl и ссылка приглашения.
* `/users/upload-avatar` (multipart `file`, JWT, пустой успешный ответ), профиль и отображение аватара.
* `/rooms/{id}/messages/attachments`, поля DTO вложений и `/message-attachments/{id}/download`.
* JWT transport и refresh flow; WebSocket/STOMP форматы S01/S02/S05.

Изменены намеренно:

* Legacy `/livekit/token` больше не выдаёт токены. Frontend содержит неиспользуемый API wrapper,
  но его вызовов из работающего приложения не найдено; реальные звонки используют callId.
* Общая запись/удаление S3 запрещены; вызовов этих операций в frontend не найдено.
* Общий download теперь доменно авторизован; непривязанные объекты закрыты.
* Произвольное изменение avatarUrl через общий update профиля отвергается (400).
* Новые storage keys стали непрозрачными; существующие URL и ключи не менялись.
* Фоны досок требуют JWT. Два существующих image-представления загружают их через существующий
  Axios binary transport с JWT и показывают blob URL. URL освобождается при смене/размонтировании;
  UI/UX и DTO досок не менялись.

В legacy MyServersCard найден статический URL `aga1.png`, не привязанный к доменному объекту.
Такой неизвестный ключ подпадает под выбранное закрытие непривязанных файлов. Старые server banners,
если они существуют только в legacy полях, не объявлены публичными новой политикой;
требуется отдельная инвентаризация/согласование. Server/Channel-функции не развивались.

## 5. Тестирование и сборки

Java: Semeru 21.0.9; Maven 3.9.9. Maven запускался offline с доступом к локальному кэшу зависимостей.
Первая sandbox-компиляция не смогла прочитать/закрыть JAR из `.m2`; запуск с доступом к кэшу прошёл.
Зависимости, pom и lockfile не менялись.

| Проверка | Всего | Успешно | Failures | Errors | Skipped | Итог |
|---|---:|---:|---:|---:|---:|---|
| S03 перед переходом к S04 | 21 | 21 | 0 | 0 | 0 | SUCCESS |
| Финальная целевая регрессия + package | 165 | 165 | 0 | 0 | 0 | SUCCESS, executable JAR |
| Полный backend suite | 214 | 200 | 4 | 10 | 0 | FAILURE: те же 14 baseline-падений |
| Изолированный исходный HEAD: две падающие тестовые группы | 14 | 0 | 4 | 10 | 0 | те же падения |
| Frontend build:web | — | — | — | — | — | SUCCESS, 2088 modules |
| Полный TypeScript `tsc -b --pretty false` | — | — | — | — | — | 88 прежних диагностик, новых нет |
| git diff --check, оба репозитория | — | — | — | — | — | SUCCESS |

Финальная целевая группа содержит 47 новых случаев: LiveKitSecurityTest 17,
StorageHttpSecurityTest 18, StorageUploadSecurityTest 11, CallTokenQuerySecurityTest 1;
также 6 существующих ConferenceServiceTest и все 112 случаев предыдущей целевой регрессии.
S01/S02/S05: MessageAccessSecurityTest 17, MessageHttpSecurityTest 9,
WebSocketAclSecurityTest 65, WebSocketBrokerSecurityTest 6, AuthServiceTest 12, MessageServiceTest 3.

Новые HTTP-тесты используют реальные SecurityConfig, JWT filter, контроллеры, доменные сервисы
и S3Service с mock AmazonS3. Проверяются 401/403/404/200, недоступность чужих/удалённых/непривязанных
объектов, вложенные ключи, публичные старые аватары, замена собственного аватара и отсутствие S3-операций
при отказе. Сервисные тесты проверяют UUID, private ACL, неподменённый поток, MIME/size/signature,
ошибку put без удаления старого файла и запрет avatarUrl alias.

Hibernate проверяет production HQL по реальным entity mappings без соединения с БД.
Это проверка разбора/семантики запроса, а не SQL execution/транзакционных гонок в PostgreSQL.
LiveKit/S3 network adapters и репозитории в новых security-сценариях замоканы.

Полный suite сравнивался с предыдущими результатами 153/167: добавлены 47 успешных случаев,
новых падений нет. Падают ChannelControllerTest (3 failures) и CallSessionServiceTest
(1 failure, 10 errors). В изолированном baseline хеши CallSessionService и обеих тестовых групп
совпадают с исходным HEAD. Набор падающих методов сравнен отдельно.
Исторические XML-отчёты в target могут содержать давно несуществующие тесты; числа взяты
из итогов актуальных Maven-запусков, без суммирования устаревших XML.

Все 88 строк TypeScript-диагностик совпадают с `security-frontend-build.log` предыдущего этапа.
Есть прежнее предупреждение о размере frontend chunk и прежние Lombok/unchecked предупреждения backend.
Они не объявляются исправленными.

Команды и логи:

```powershell
$env:JAVA_HOME='C:\Users\Пользователь\.jdks\semeru-21.0.9'
mvn.cmd -o '-Dtest=LiveKitSecurityTest,ConferenceServiceTest,StorageHttpSecurityTest,StorageUploadSecurityTest,CallTokenQuerySecurityTest,MessageAccessSecurityTest,MessageHttpSecurityTest,WebSocketAclSecurityTest,WebSocketBrokerSecurityTest,AuthServiceTest,MessageServiceTest' package
mvn.cmd -o test
mvn.cmd -o -f target/security-baseline/pom.xml '-Dtest=ChannelControllerTest,CallSessionServiceTest' test
# В zvonok-frontend:
npm.cmd run build:web
npx.cmd tsc -b --pretty false
```

Backend: `stage2-s03.log`, `stage2-security.log`, `stage2-package.log`,
`stage2-all-tests.log`, `stage2-baseline-tests.log`, `stage2-compile.log`.
Frontend: `stage2-frontend-build.log`, `stage2-typescript.log`.
JAR: `target/zvonok-0.0.1-SNAPSHOT.jar`.

## 6. Все файлы этапа 2

Backend, изменённые:

* `src/main/java/com/zvonok/controller/LiveKitController.java`
* `src/main/java/com/zvonok/controller/S3Controller.java`
* `src/main/java/com/zvonok/controller/MessageAttachmentController.java`
* `src/main/java/com/zvonok/repository/CallParticipantRepository.java`
* `src/main/java/com/zvonok/repository/CanvasBoardRepository.java`
* `src/main/java/com/zvonok/repository/MessageAttachmentRepository.java`
* `src/main/java/com/zvonok/repository/UserRepository.java`
* `src/main/java/com/zvonok/repository/RoomRepository.java`
* `src/main/java/com/zvonok/service/CallSessionService.java`
* `src/main/java/com/zvonok/service/LiveKitTokenService.java`
* `src/main/java/com/zvonok/service/S3Service.java`
* `src/main/java/com/zvonok/service/UserService.java`
* `src/main/java/com/zvonok/service/MessageService.java` — добавления поверх сохранённых изменений этапа 1.

Backend, новые:

* `src/main/java/com/zvonok/service/S3ObjectAccessService.java`
* `src/main/java/com/zvonok/service/StorageUploadValidation.java`
* `src/test/java/com/zvonok/unittests/service/LiveKitSecurityTest.java`
* `src/test/java/com/zvonok/unittests/service/StorageUploadSecurityTest.java`
* `src/test/java/com/zvonok/unittests/security/StorageHttpSecurityTest.java`
* `src/test/java/com/zvonok/unittests/security/CallTokenQuerySecurityTest.java`
* `CORE-STABILIZATION-STAGE2.md`

Frontend:

* `src/components/CallCanvas/CanvasTemplateLayer/CanvasTemplateLayer.tsx`
* `src/components/CallCanvas/WhiteboardTile/WhiteboardTile.tsx`
* `src/components/CallCanvas/useCanvasBackgroundImage.ts` — новый.

Остальные dirty/untracked файлы были в рабочем дереве до этапа 2: SECURITY-P0.md,
WebSocketConfig, MessageController, ShortMessageWrapped, JwtChannelInterceptor,
MessageReadStatusRepository, AuthService, MessageReadStatusService, PermissionService,
MessageReadStatusContent, AuthServiceTest, InvalidMessageBatchException,
WebSocketDeliveryInterceptor, WebSocketAuthorizationSessions, MessageAccessService,
WebSocketAccessService и тесты MessageAccessSecurityTest/MessageHttpSecurityTest/
WebSocketAclSecurityTest/WebSocketBrokerSecurityTest. Они сохранены.
`.factorypath` также был dirty до начала работ и не редактировался.

## 7. Миграции и безопасный переход

Миграции схемы/данных для исправлений приложения не нужны и не выполнялись.
Bucket не очищался; старые файлы не удалялись, не переименовывались и не заменялись.

Если потребуются доступ к непривязанным старым файлам или блокировка прямого публичного bucket:

1. Выполнить read-only инвентаризацию storage keys, доменных ссылок и bucket/CDN policy на согласованном стенде.
2. Согласовать принадлежность/публичность неопознанных объектов; привязать их к существующим доменам
   только отдельной проверяемой операцией. По имени файла право не угадывать.
3. Проверить сохранение ссылок через защищённый backend proxy; для старых прямых storage/CDN URL
   сначала согласовать совместимый gateway/переход, затем отдельное изменение политики.
4. Проверить отсутствие анонимного доступа к приватным объектам напрямую и корректную поддержку private ACL
   используемым S3-провайдером. Никакие массовые операции с объектами в этой задаче не запускались.

## 8. Оставшиеся ограничения и ручная приёмка

* Проверить на стенде DM: caller до accept, receiver после accept, reconnect после сбоя, leave/end;
  групповой join/reconnect и отзыв членства; приглашение/завершение конференции с двумя браузерами.
* Уже выданный LiveKit JWT не отзывается проверкой HTTP endpoint: его room grant может оставаться
  применимым до TTL. Нужна отдельная проверка поведения self-hosted LiveKit при end/kick/rejoin,
  включая возможное повторное создание комнаты старым JWT. Новая выдача для ENDED запрещена.
* Проверить реальные S3 put/get с private ACL, отображение старых аватаров, фото/видео/voice/video-note,
  фоны досок с JWT, старые ключи и запрет прямых публичных ссылок на private media.
* PostgreSQL rollback, конкурентный отзыв членства и storage failure после успешного put требуют
  стенда. Сбой БД после put может оставить новый приватный orphan; старый объект не удаляется.
  Автоматическая очистка orphan намеренно не добавлялась.
* Файлы проверяются по размеру/MIME/сигнатуре, без полного декодирования и malware scanning.
  Не менялся прежний разрыв между service video limit 100 MiB и default multipart limit 50 MB.
* На каждый generic download выполняется поиск доменных ссылок. Не добавлялись schema indexes;
  требуется нагрузочная проверка и отдельное согласование индексов при необходимости.
* Код конференции имеет прежние 8 hex-символов; rate limiting/длинные коды и распределённый отзыв
  stateless JWT остаются отдельными задачами. Модель приглашений не перепроектировалась.
* В локальном ignored application.properties есть credential defaults; действительность не проверялась,
  значения в отчёт не включены. Управление/ротация секретов вне scope.
* 14 baseline-падений backend, 88 TypeScript-диагностик, B01 и legacy Server/Channel/Hub работы остаются.

S03 можно считать закрытым по серверной авторизации **новой выдачи** токенов; реальная media-приёмка
остаётся обязательной перед production. S04 исправлен на уровне API и новых загрузок, но полное
эксплуатационное закрытие не подтверждено без проверки bucket/CDN/старых ACL и ручной приёмки.
