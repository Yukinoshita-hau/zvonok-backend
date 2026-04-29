# Frontend Integration Contract for Calls (after Phase 1 backend refactor)

> Важно: сейчас не писать frontend-код и не менять backend-логику. Это точный integration contract для frontend: какие WS пути, какие payload, какие events, когда брать token, как отличается PRIVATE и GROUP.

## Why current frontend is broken

Текущий frontend, судя по описанию, ориентирован на старую модель (chatRoomId + `GET /livekit/token?room=...` + локальный state без `callId`).
После Phase 1 backend:
- ввёл persisted `CallSession`/`CallParticipant` и state transitions;
- добавил `callId` в lifecycle и новый token endpoint `POST /api/calls/{callId}/token`;
- начал отправлять расширенный event payload с `callId`, `roomType`, `callStatus` и т.д.;
- accept/decline/end больше не должны полагаться на `callerUsername` из клиента.

Из-за этого старый frontend может:
- не сохранять/передавать `callId` на accept/decline/end;
- продолжать запрашивать токен по room string;
- неверно обрабатывать новые event types (например, `CALL_ACCEPTED`).

---

## Backend call flow summary (what exists now)

- STOMP inbound: `/app/call/invite`, `/app/call/accept`, `/app/call/decline`, `/app/call/end`.
- STOMP outbound (user queue): backend шлёт через `convertAndSendToUser(..., "/queue/call", ...)`, значит frontend подписывается на **`/user/queue/call`**.
- Token REST:
  - новый: `POST /api/calls/{callId}/token` (основной для call flow);
  - legacy: `GET /livekit/token?room=...` (deprecated, оставить только как fallback/dev).

---

## STOMP client -> backend destinations

| Action | Destination | Payload | Required fields | Used for PRIVATE | Used for GROUP | Backend result |
|---|---|---|---|---|---|---|
| Start call | `/app/call/invite` | `InviteCallDto` | `chatRoomId` | Yes | Yes | Creates/returns `CallSession`; PRIVATE sends invite to receiver only; GROUP invites other members |
| Accept call | `/app/call/accept` | `AcceptCallDto` | `callId` **or** `chatRoomId` fallback | Yes | Yes | PRIVATE: receiver -> ACCEPTED, session->ACTIVE; GROUP: participant->ACCEPTED |
| Decline call | `/app/call/decline` | `DeclineCallDto` | `callId` **or** `chatRoomId` fallback | Yes | Yes | PRIVATE terminal decline/end events; GROUP only participant decline, call continues |
| End call | `/app/call/end` | `EndCallDto` | `callId` **or** `chatRoomId` fallback | Yes | Yes | PRIVATE any participant can end; GROUP only host can end |

### DTO shapes (actual backend fields)

```ts
export type CallInviteType = "audio" | "video"; // from CallInviteType enum values

export interface InviteCallDto {
  callType?: CallInviteType; // backend currently does not use this field in events
  chatRoomId?: number;
}

export interface AcceptCallDto {
  callId?: number;      // preferred
  chatRoomId?: number;  // fallback for old flow
  callerUsername?: string; // legacy, backend does not trust for state decisions
}

export interface DeclineCallDto {
  callId?: number;      // preferred
  chatRoomId?: number;  // fallback
  declineUsername?: string; // legacy, backend does not trust for state decisions
}

export interface EndCallDto {
  callId?: number;      // preferred
  chatRoomId?: number;  // fallback
}
```

---

## Backend -> frontend WS events

### Subscription path
- Frontend STOMP subscription for call events: **`/user/queue/call`**.

### Event type enum exposed by backend

```ts
export type CallEventType =
  | "CALL_INVITE"
  | "CALL_ACCEPT"
  | "CALL_ACCEPTED"
  | "CALL_DECLINE"
  | "CALL_DECLINED"
  | "CALL_BUSY"
  | "CALL_END"
  | "CALL_ENDED"
  | "CALL_CANCELLED"
  | "GROUP_CALL_STARTED"
  | "CALL_PARTICIPANT_JOINED"
  | "CALL_PARTICIPANT_LEFT"
  | "CALL_PARTICIPANT_DECLINED";
```

> Note: не все enum значения реально эмитятся в текущем сервисе.

### Actual event payload shape (BaseCallEvent)

```ts
export type RoomType = "PRIVATE" | "GROUP";

export type CallSessionStatus =
  | "RINGING"
  | "ACTIVE"
  | "ENDED"
  | "DECLINED"
  | "CANCELLED"
  | "MISSED";

export type CallParticipantStatus =
  | "INVITED"
  | "RINGING"
  | "ACCEPTED"
  | "JOINED"
  | "DECLINED"
  | "LEFT"
  | "KICKED";

export interface BaseCallEvent {
  eventId?: string;
  type?: CallEventType;
  callId?: number;
  chatRoomId?: number;      // legacy-compatible room id field
  roomId?: number;
  roomType?: RoomType;
  liveKitRoomName?: string;
  callerUsername?: string;
  hostUsername?: string;
  callStatus?: CallSessionStatus;
  participantStatus?: CallParticipantStatus;
  occurredAt?: string; // LocalDateTime serialized by Jackson
}
```

### Emission matrix (what is currently sent)

| Event | Recipient | When backend sends | Required frontend reaction | PRIVATE behavior | GROUP behavior | Important fields |
|---|---|---|---|---|---|---|
| `CALL_INVITE` | Receiver only (private) / all non-initiator participants (group) | On start call | Show incoming ringing UI; persist `callId`, `roomType`, `liveKitRoomName` | Receiver gets invite | Non-host members get invite | `callId`, `roomId/chatRoomId`, `roomType`, `liveKitRoomName`, `callerUsername` |
| `CALL_STARTED` | Initiator/host only | On successful start call creation | Persist `callId` immediately; move to outgoing_ringing/connecting pipeline and request token by `callId` | Caller gets started event | Host gets started event | `callId`, `roomId/chatRoomId`, `roomType`, `liveKitRoomName`, `callerUsername`, `hostUsername`, `callStatus` |
| `CALL_ACCEPTED` | All call participants | On private accept | Move to connecting, fetch token by `callId` | Yes | No |
| `CALL_ACCEPT` (legacy) | All call participants | Also on private accept | Treat as legacy duplicate of `CALL_ACCEPTED` (dedupe by `eventId` and/or semantic guard) | Yes | No |
| `CALL_PARTICIPANT_JOINED` | All call participants | On group accept | Update group participant list/status | No | Yes | `participantStatus=ACCEPTED`, `callId` |
| `CALL_DECLINED` | All call participants | On private decline | Terminal for private call; reset ringing state | Yes | No |
| `CALL_DECLINE` (legacy) | All call participants | Also on private decline | Legacy duplicate signal; do not process twice | Yes | No |
| `CALL_ENDED` | All call participants | On private decline terminal and on explicit end | Terminal event: teardown call UI/livekit | Yes | Yes |
| `CALL_PARTICIPANT_DECLINED` | Host only | On group decline | Update participant state only; do not end whole call | No | Yes | `participantStatus=DECLINED` |

### Events declared but **not emitted now** (important for frontend)
- `CALL_END`, `CALL_CANCELLED`, `GROUP_CALL_STARTED`, `CALL_PARTICIPANT_LEFT`, `CALL_BUSY` currently есть в enum, но в `CallSessionService` не публикуются.

---


### livekitRoomName format (backend Phase 1)
- PRIVATE: `dm-{roomId}-call-{callId}`
- GROUP: `group-{roomId}-call-{callId}`
- Frontend must treat this value as opaque and always use the value from events/token flow.

## REST API contract

| Endpoint | Method | Request | Response | When frontend calls | Deprecated/current |
|---|---|---|---|---|---|
| `/api/calls/{callId}/token` | POST | path `callId`, auth required | `LiveKitTokenResponse` | After accept/join when call is valid | **Current** |
| `/livekit/token?room=...` | GET | query `room`, auth principal | `LiveKitTokenResponse` | Legacy/dev fallback only | **Deprecated** |

### LiveKitTokenResponse

```ts
export interface LiveKitTokenResponse {
  serverUrl?: string;
  participantToken?: string;
  callId?: number | null;
  expiresAt?: string | null;
}
```

### Auth requirement
- `/api/calls/{callId}/token` защищён обычным JWT auth (`anyRequest().authenticated()`).
- WS тоже требует валидный principal/token на message commands.

### Token usage rule for frontend
- Новый call flow: **только** `POST /api/calls/{callId}/token`.
- Frontend не должен сам придумывать roomName для call token endpoint.
- If frontend Axios `baseURL` is `/api`, request path must be `/calls/{callId}/token` (not `/api/calls/...`) to avoid `/api/api/...`.
- Outgoing flow source of truth should now be `CALL_STARTED` (contains `callId`), not locally derived room name.

---

## PRIVATE call sequence (frontend migration)

### Outgoing PRIVATE
1. User clicks call in private room.
2. Front sends `/app/call/invite` with `{ chatRoomId, callType }`.
3. Receiver gets `CALL_INVITE` on `/user/queue/call`.
4. Caller receives `CALL_STARTED` with `callId` and persists call context.
5. Receiver accept: sends `/app/call/accept` with `{ callId }` (preferred).
6. Backend sends `CALL_ACCEPTED` + legacy `CALL_ACCEPT` участникам.
7. Both sides call `POST /api/calls/{callId}/token`, then connect LiveKit using returned `serverUrl` + `participantToken`.
8. Receiver decline: sends `/app/call/decline` with `{ callId }`.
9. Backend sends `CALL_DECLINED` + `CALL_DECLINE` + `CALL_ENDED` -> обе стороны закрывают UI.

### Incoming PRIVATE
- On `CALL_INVITE`: открыть incoming modal, сохранить `callId/roomId/roomType/liveKitRoomName/callerUsername`.
- Accept button: `/app/call/accept` with `callId`.
- Decline button: `/app/call/decline` with `callId`.
- Token запрашивать только после accept flow (`CALL_ACCEPTED`/local accepted state).

### End PRIVATE
- Any participant sends `/app/call/end` with `callId`.
- `CALL_ENDED` -> teardown for both.

---

## GROUP call sequence (frontend migration)

### Outgoing GROUP (host)
1. Host sends `/app/call/invite` with `chatRoomId`.
2. Backend creates session `ACTIVE`, host participant `ACCEPTED`, others `INVITED`.
3. Other members get `CALL_INVITE` (сейчас не `GROUP_CALL_STARTED`).
4. Host receives `CALL_STARTED` with `callId` and then calls `POST /api/calls/{callId}/token`.

### Incoming GROUP member
- On `CALL_INVITE` with `roomType=GROUP`: показать "join ongoing group call" UI.
- Accept -> `/app/call/accept` with `callId`, потом `POST /api/calls/{callId}/token`.
- Decline -> `/app/call/decline` with `callId`; это **не** завершает звонок всем.

### Group end/leave behavior in current backend
- Host end: `/app/call/end` -> `CALL_ENDED` всем participants.
- Ordinary member leave отдельным endpoint пока **нет** (gap).
  - Если нужен "leave only self" UI сейчас: клиент может disconnect LiveKit локально, но backend participant status `LEFT` обновится только через existing end logic (где сейчас whole-call semantics).

---

## Required frontend changes (practical)

### call.slice
- Add source-of-truth fields: `callId`, `roomType`, `callStatus`, `participantStatus`, `hostUsername`, `lastEventId`.
- Keep `chatRoomId/livekitRoomName` for compatibility, but **not** as primary identity (primary key is `callId`).

Suggested shape:

```ts
export interface CallState {
  status:
    | "idle"
    | "outgoing_ringing"
    | "incoming_ringing"
    | "connecting"
    | "in_call"
    | "ended"
    | "error";

  callId: number | null;
  chatRoomId: number | null;
  roomType: "PRIVATE" | "GROUP" | null;
  livekitRoomName: string | null;

  serverUrl: string | null;
  participantToken: string | null;

  callerUsername: string | null;
  hostUsername: string | null;

  callStatus:
    | "RINGING"
    | "ACTIVE"
    | "ENDED"
    | "DECLINED"
    | "CANCELLED"
    | "MISSED"
    | null;
  participantStatus:
    | "INVITED"
    | "RINGING"
    | "ACCEPTED"
    | "JOINED"
    | "DECLINED"
    | "LEFT"
    | "KICKED"
    | null;

  lastEventId: string | null;
}
```

### websocketMiddleware
- Subscribe to `/user/queue/call`.
- Parse as `BaseCallEvent`.
- Dedupe by `eventId` (if same `eventId` ignore).
- Semantic dedupe for legacy dual events:
  - if `CALL_ACCEPTED` already handled for same `callId`, ignore `CALL_ACCEPT` follow-up.
  - if terminal already handled, ignore duplicate `CALL_DECLINE`/`CALL_DECLINED`/`CALL_ENDED` cascade.

### API client / thunks
- New thunk: `getCallToken(callId)` -> `POST /api/calls/{callId}/token`.
- Keep old `getLegacyToken(roomName)` only as fallback behind feature flag; new call flow must not use it.

### Outgoing actions
- `sendInvite`: still send `chatRoomId` (+ optional `callType`).
- `sendAccept`: send `callId` (fallback `chatRoomId` only for compatibility window).
- `sendDecline`: same.
- `sendEnd`: send `callId`.

---

## Legacy compatibility / breaking notes

1. Old WS destinations still valid (`invite/accept/decline`), so transport-level breakage нет.
2. Backend still accepts fallback by `chatRoomId` for accept/decline/end.
3. Backend ignores client `callerUsername/declineUsername` как authority for transitions.
4. Backend emits both legacy and new accept/decline events for PRIVATE => риск двойной обработки на фронте.
5. `GET /livekit/token?room=...` ещё работает, но это legacy/deprecated path.
6. В текущем backend нет отдельного leave endpoint/event (`CALL_PARTICIPANT_LEFT` не эмитится).

---

## Known backend gaps affecting frontend (must be handled)

1. Group "join/leave" granular events неполные (`CALL_PARTICIPANT_LEFT` не публикуется сейчас).
2. `CallType` enum содержит значения, которые пока не используются фактически.
3. `callType` из `InviteCallDto` не возвращается в `BaseCallEvent`.

---

## Suggested frontend migration order

1. **Types first**: add `BaseCallEvent` + enum unions + token response types.
2. **WS middleware**: subscribe `/user/queue/call`, event dedupe, terminal handling.
3. **State**: migrate `call.slice` from room-centric to `callId`-centric.
4. **API**: add `POST /api/calls/{callId}/token` client and switch token thunk.
5. **Flows**: private outgoing/incoming accept/decline/end.
6. **Group flows**: invite/accept/decline/end with non-terminal decline handling.
7. Keep legacy fallback (`chatRoomId` and `/livekit/token`) behind compatibility switch until frontend rollout complete.
