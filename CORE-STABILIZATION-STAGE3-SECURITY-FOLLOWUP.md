# Security follow-up, обнаруженный во время этапа 3

Дата: 2026-10-17. Только статическое исследование локального кода. Production не использовался.

## Canvas/code REST-проверки не эквивалентны актуальным S02 STOMP ACL

CanvasBoardService.getActiveBoards/getBoard/getSnapshot вызывают validateCallParticipant.
Этот helper загружает CallSession и проверяет статус записи CallParticipant, но не текущее
членство в комнате и не ENDED для read endpoints. Проверки ENDED существуют отдельно для
создания/изменения. CodeSessionService.getActiveSession и validateSessionParticipant имеют
аналогичное разделение: наличие ACCEPTED/JOINED не доказывает текущий room access.

Если удаление участника комнаты/завершение звонка оставляет такую запись, потенциально остаётся
прямое чтение приватного snapshot/code через REST. Это условный риск по коду, а не утверждение
о выполненном воспроизведении/production инциденте. Точные состояния после leave/end/remove
необходимо проверить HTTP regression с реальными доменными сервисами.

В отличие от этих HTTP helpers, WebSocketAccessService.checkCall из S02 проверяет и ENDED,
и active participant, и roomAccessService.getRoomForUser. Delivery interceptor повторяет ACL.
Этап 3 эти проверки не ослаблял. Новый reconnect сначала получает доступные rooms и
restoreCallSession и не восстанавливает инструменты утратившего доступ звонка. Это ограничивает
новую автоматическую догрузку; не является исправлением всех прямых REST вызовов приложения.

Источники:

* src/main/java/com/zvonok/service/CanvasBoardService.java: getActiveBoards, getBoard, getSnapshot,
  validateCallParticipant, validateCallParticipantStatus.
* src/main/java/com/zvonok/service/CodeSessionService.java: getActiveSession,
  validateSessionParticipant, validateCallParticipantStatus.
* src/main/java/com/zvonok/service/WebSocketAccessService.java: checkCall, checkCode.

Отдельный следующий security change: сформулировать одинаковую политику доступа к историческим
инструментам/ended call, добавить тесты removed room member и ended call, затем применять
политику в соответствующих HTTP/domain read paths. Пользовательские данные не удалять;
старую Hub/Server/Channel архитектуру не развивать. В этом этапе вне scope код не изменён.
