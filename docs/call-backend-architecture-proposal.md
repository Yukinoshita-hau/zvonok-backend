# Call Backend Architecture Proposal — Phase 1 MVP (implementation plan only)

> Scope of this revision: **Phase 1 only**. No implementation of guest links, moderation endpoints, webhooks, or lobby in this pass.

## 1) Exact Phase 1 implementation plan

### Goal
Implement minimal, production-safe call state layer that:
1. introduces `CallSession` + `CallParticipant`;
2. separates PRIVATE and GROUP call behavior;
3. moves token issuance to `callId`-bound endpoint;
4. preserves backward compatibility for current WS contracts where possible.

### Phase 1 steps (ordered)
1. **DB schema**: add `call_session` and `call_participant` tables with indexes + constraints.
2. **Domain layer**: add entities + enums + repositories.
3. **State service**: add `CallSessionService` (or `CallStateService`) with strict transition rules.
4. **WS flow upgrade**:
   - keep existing `/app/call/invite|accept|decline` handlers;
   - route logic through persisted call session state;
   - add `CALL_ENDED` and `CALL_CANCELLED` publish paths.
5. **Token hardening**:
   - add `/api/calls/{callId}/token` endpoint;
   - derive room name from session only;
   - deprecate unsafe `/livekit/token?room=...` for call use.
6. **Idempotency/race guards**: enforce transition checks and unique constraints.
7. **Compatibility release**:
   - continue sending legacy event names (`CALL_INVITE`, `CALL_ACCEPT`, `CALL_DECLINE`),
   - enrich payload with `callId` and metadata,
   - introduce new events without removing old ones in same release.

---

## 2) Entity definitions (Java shape)

## Enums

```java
public enum CallSessionStatus {
    RINGING,
    ACTIVE,
    ENDED,
    DECLINED,
    CANCELLED,
    MISSED
}

public enum CallParticipantStatus {
    INVITED,
    RINGING,
    ACCEPTED,
    JOINED,
    DECLINED,
    LEFT,
    KICKED
}

public enum CallParticipantRole {
    HOST,
    MEMBER
}
```

## `CallSession` (proposed)

```java
@Entity
@Table(name = "call_session",
       indexes = {
         @Index(name = "idx_call_session_room_status", columnList = "room_id,status"),
         @Index(name = "idx_call_session_livekit_room", columnList = "livekit_room_name")
       },
       uniqueConstraints = {
         @UniqueConstraint(name = "uk_call_session_livekit_room", columnNames = "livekit_room_name")
       })
public class CallSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 16)
    private RoomType roomType; // PRIVATE/GROUP snapshot

    @Column(name = "livekit_room_name", nullable = false, length = 128)
    private String livekitRoomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CallSessionStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User hostUser;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @ManyToOne @JoinColumn(name = "ended_by_user_id")
    private User endedByUser;

    @Column(name = "end_reason", length = 64)
    private String endReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

## `CallParticipant` (proposed)

```java
@Entity
@Table(name = "call_participant",
       indexes = {
         @Index(name = "idx_call_participant_call_status", columnList = "call_session_id,status"),
         @Index(name = "idx_call_participant_call_user", columnList = "call_session_id,user_id")
       },
       uniqueConstraints = {
         @UniqueConstraint(name = "uk_call_participant_call_user", columnNames = {"call_session_id","user_id"})
       })
public class CallParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "call_session_id", nullable = false)
    private CallSession callSession;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "identity", nullable = false, length = 128)
    private String identity; // usually username/userId-based

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CallParticipantRole role; // HOST|MEMBER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CallParticipantStatus status;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

## 3) Migration shape (Flyway SQL proposal)

```sql
-- Vxxx__add_call_session_and_call_participant.sql

create table call_session (
    id bigserial primary key,
    room_id bigint not null references room(id),
    room_type varchar(16) not null,
    livekit_room_name varchar(128) not null,
    status varchar(24) not null,
    created_by_user_id bigint not null references users(id),
    host_user_id bigint not null references users(id),
    started_at timestamp not null,
    ended_at timestamp null,
    ended_by_user_id bigint null references users(id),
    end_reason varchar(64) null,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),

    constraint chk_call_session_status
      check (status in ('RINGING','ACTIVE','ENDED','DECLINED','CANCELLED','MISSED')),
    constraint chk_call_session_room_type
      check (room_type in ('PRIVATE','GROUP'))
);

create unique index uk_call_session_livekit_room_name
    on call_session(livekit_room_name);

create index idx_call_session_room_status
    on call_session(room_id, status);

-- PostgreSQL partial unique index: one active/ringing call per room
create unique index uk_call_session_one_active_per_room
    on call_session(room_id)
    where status in ('RINGING','ACTIVE');

create table call_participant (
    id bigserial primary key,
    call_session_id bigint not null references call_session(id) on delete cascade,
    user_id bigint not null references users(id),
    identity varchar(128) not null,
    display_name varchar(128) null,
    role varchar(16) not null,
    status varchar(24) not null,
    joined_at timestamp null,
    left_at timestamp null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),

    constraint uk_call_participant_call_user unique (call_session_id, user_id),
    constraint chk_call_participant_role
      check (role in ('HOST','MEMBER')),
    constraint chk_call_participant_status
      check (status in ('INVITED','RINGING','ACCEPTED','JOINED','DECLINED','LEFT','KICKED'))
);

create index idx_call_participant_call_status
    on call_participant(call_session_id, status);

create index idx_call_participant_call_user
    on call_participant(call_session_id, user_id);
```

Notes:
- If DB is not PostgreSQL, partial unique index for “one active call per room” should be replaced by transactional check + lock.
- Store enums as `varchar` for readability + migration safety.

---

## 4) Repository/service/controller changes

## Repositories
- `CallSessionRepository`
  - `Optional<CallSession> findById(Long id)`
  - `Optional<CallSession> findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(...)`
  - `boolean existsByRoomIdAndStatusIn(...)`
  - `Optional<CallSession> findByLivekitRoomName(String roomName)`
- `CallParticipantRepository`
  - `Optional<CallParticipant> findByCallSessionIdAndUserId(...)`
  - `List<CallParticipant> findAllByCallSessionId(...)`
  - `long countByCallSessionIdAndStatusIn(...)`

## Services
- `CallSessionService` (or `CallStateService`)
  - `startCall(roomId, initiatorUsername, inviteType)`
  - `accept(callId, username)`
  - `decline(callId, username)`
  - `end(callId, username, reason)`
  - `leave(callId, username)`
  - `getActiveCallByRoom(roomId, username)`
- `CallEventPublisher`
  - central helper for WS emission to room participants/user queues,
  - maps domain transitions -> outgoing event DTOs.
- `LiveKitCallTokenService` (new or refactor existing)
  - `issueToken(callId, username)` with authorization + session checks.

## Controllers
- Keep current WS controller (`/app/call/invite|accept|decline`) but route to `CallSessionService`.
- Add WS `end` mapping (`/app/call/end`) for explicit hangup.
- Add REST token endpoint:
  - `POST /api/calls/{callId}/token` (preferred; explicit action)
  - return `{ serverUrl, participantToken, expiresAt, callId }`.
- Keep existing `GET /livekit/token` temporarily, but mark as deprecated for call flows.

---

## 5) Event DTO changes (compatibility-first)

### Keep legacy event names
- `CALL_INVITE`
- `CALL_ACCEPT` (legacy)
- `CALL_DECLINE` (legacy)

### Add/standardize new names in same release
- `CALL_ACCEPTED`
- `CALL_DECLINED`
- `CALL_ENDED`
- `CALL_CANCELLED`
- `CALL_PARTICIPANT_JOINED` (group mostly)
- `CALL_PARTICIPANT_LEFT` (group mostly)

### Unified payload (proposed)
```json
{
  "eventId": "uuid",
  "type": "CALL_INVITE",
  "callId": 123,
  "roomId": 10,
  "roomType": "PRIVATE",
  "livekitRoomName": "dm-10-call-123",
  "callerUsername": "alice",
  "hostUsername": "alice",
  "participantUsername": "bob",
  "occurredAt": "2026-04-28T12:00:00Z",
  "callStatus": "RINGING",
  "participantStatus": "RINGING",
  "compat": {
    "chatRoomId": 10,
    "fromUser": "alice"
  }
}
```

### Critical rule
- `accept/decline/end` must not trust `callerUsername` from client payload.
- Backend resolves actor, host, and allowed recipients from persisted `CallSession` and `CallParticipant`.

---

## 6) Token endpoint changes

## New safe endpoint
- `POST /api/calls/{callId}/token`

## Validation flow
1. Load `CallSession` by `callId`.
2. Verify session status allows token issue (`RINGING|ACTIVE` depending on scenario).
3. Verify requester is valid participant (or allowed member pre-created as participant record).
4. Resolve `livekitRoomName` from session only.
5. Generate token with short TTL (5–15 min), include:
   - `roomJoin=true`
   - `room=<session.livekit_room_name>`
   - `canSubscribe=true`
   - `canPublish=true`
   - `canPublishData=true` (optional, if app requires data channel)
   - **no `roomAdmin`** in Phase 1.

## Deprecation
- Keep `/livekit/token?room=...` only for legacy/non-call usage temporarily.
- Frontend call flow must migrate to callId token endpoint in same milestone.

---

## 7) PRIVATE call state transitions (Phase 1)

Participants: caller + receiver only.

### Start
- Preconditions:
  - room exists, `RoomType=PRIVATE`, caller is member, exactly 2 members.
  - no active/ringing call in this room.
- Effects:
  - create `CallSession(status=RINGING, host=caller)`;
  - create participants:
    - caller: `HOST`, status `ACCEPTED` (or `JOINED` once actual join is tracked)
    - receiver: `MEMBER`, status `RINGING`;
  - publish `CALL_INVITE` to receiver only.

### Receiver accept
- Preconditions: session in `RINGING`, receiver status in `RINGING/INVITED`.
- Effects:
  - receiver -> `ACCEPTED`;
  - session -> `ACTIVE`;
  - caller receives `CALL_ACCEPTED`;
  - receiver requests token via `/api/calls/{callId}/token`.

### Receiver decline
- Preconditions: session in `RINGING`.
- Effects:
  - receiver -> `DECLINED`;
  - session -> `DECLINED` then terminal (`ENDED`) OR directly terminal reason `DECLINED`;
  - both sides receive `CALL_DECLINED` and terminal event (`CALL_ENDED` or consistent terminal status event).

### End
- Either participant may end when `RINGING|ACTIVE`.
- session -> `ENDED`, mark `endedByUser`.
- both participants receive `CALL_ENDED`.

---

## 8) GROUP call state transitions (Phase 1)

### Start status decision
Recommended: create group call as `ACTIVE` immediately when initiator starts.

Reason:
- initiator typically enters call immediately;
- group invite is “join ongoing call”, not strict two-party handshake;
- avoids ambiguous “ringing group call with no active media owner”.

### Start
- Preconditions:
  - room exists, `RoomType=GROUP`, initiator is member;
  - no active/ringing call in room.
- Effects:
  - create `CallSession(status=ACTIVE, host=initiator)`;
  - initiator participant -> `HOST`, `ACCEPTED` (or `JOINED` if tracked post-token/join);
  - for each other member: participant `MEMBER`, `INVITED` (or `RINGING` if UI expects ringing);
  - publish `GROUP_CALL_STARTED`/`CALL_INVITE` to other members.

### Member accept
- member status `INVITED/RINGING -> ACCEPTED`;
- member obtains token individually;
- optionally broadcast `CALL_PARTICIPANT_JOINED` after actual join signal.

### Member decline
- member status -> `DECLINED`;
- session remains `ACTIVE`;
- optional event to host `CALL_PARTICIPANT_DECLINED`.

### Member leave
- member status -> `LEFT`;
- session stays active while at least one joined/accepted participant remains.
- if no active participants remain -> end after short idle timeout policy (e.g., 30–60s).

### Host end
- host action ends whole call: session -> `ENDED`, broadcast `CALL_ENDED` to all participants.

---

## 9) Backward compatibility notes

1. Keep current WS destinations and base event names to avoid immediate FE breakage.
2. Add new payload fields (`callId`, `roomType`, `eventId`, timestamps) as additive changes.
3. Keep old DTO fields (`chatRoomId`, `fromUser`) inside compatibility block during migration window.
4. Keep existing `/livekit/token` temporarily, but FE call flow should switch to `/api/calls/{callId}/token`.
5. `CALL_ACCEPT` can be emitted alongside `CALL_ACCEPTED` until FE migration completes.

---

## 10) Risks / open questions

## Risks
- DB portability of partial unique indexes.
- Ambiguity between `ACCEPTED` and `JOINED` without LiveKit webhook integration in Phase 1.
- Duplicate client retries if FE doesn’t dedupe by `eventId`.
- In-flight legacy clients may still depend on old payload shape.

## Open questions
1. For private decline, keep terminal status as `DECLINED` only, or always move to `ENDED` with reason?
2. Group idle timeout value when no one remains: 30s, 60s, or immediate?
3. Should group non-host member be allowed to end call in Phase 1 (recommended: no)?
4. Should group start use participant default `INVITED` or `RINGING` for non-initiators (UI preference)?

---

## 11) Explicitly out of scope for Phase 1 (Phase 2/3)

### Phase 2
- moderation actions: kick/mute/unmute,
- extended roles (`MODERATOR`) and moderation permissions,
- LiveKit RoomService moderation control.

### Phase 3
- guest invite links,
- guest identities and guest-limited grants,
- lobby/approval flows,
- LiveKit webhooks for authoritative join/leave sync,
- recording/advanced governance.

This document is an implementation blueprint only. **No code changes are proposed in this section beyond Phase 1 scope.**
