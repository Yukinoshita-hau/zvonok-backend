# S01 / S05 / S02 — implementation and verification

Date: 2026-10-17. Scope: backend message reads, read receipts, STOMP ACLs.
No commits, schema migrations, production access, LiveKit, hubs or design changes.
The pre-existing `.factorypath` modification was left untouched. Frontend sources and lockfile are unchanged.

## Implemented policies

* `GET /messages/{id}` receives the authenticated principal and delegates to `MessageAccessService`.
  Missing, deleted, malformed-target and inaccessible messages return the same `404 / Message not found`.
  Unauthenticated HTTP requests still receive `401` through the existing SecurityConfig/JWT filter.
* Room reads use `RoomAccessService`: current membership and an active room are required, including for the author.
* Channel reads use `PermissionService`: active server membership and effective VIEW_CHANNEL are required.
  `canUserViewChannel` also rejects an inactive channel. Existing role/override calculation is retained.
* Responses remain DTOs. Room response fields remain compatible; channel messages have `room: null`
  and an additive `channelId`. No JPA entity is serialized by this endpoint.
* Read marking authorizes before the sender shortcut or insert. The PostgreSQL conflict-ignore insert
  and unique `(message_id, user_id)` deduplication are retained. Channel receipts no longer dereference room.
* Readers authorizes every message before querying any read statuses. It now loads all readers rather than
  an Optional holding only one row. Batches require 1–100 non-null IDs; invalid input returns 400.
* Transactional service-level `markMessagesAsRead` pre-authorizes the whole batch before the first write
  or notification. No new REST/STOMP batch-mark endpoint was introduced.

## Destination inventory and compatibility

Compared backend `@MessageMapping` and all broker publishers with frontend
`wsPathes.ts`, `publishActions.ts` and `subscriptions.ts`.

| Operation | Destination | Authorization |
|---|---|---|
| SEND | `/app/chat/send/{roomId}` | JWT, current room access; existing MessageService checks sender membership |
| SEND | `/app/chat/channel/{channelId}` | JWT, VIEW_CHANNEL + SEND_MESSAGES; existing send service checks remain |
| SEND | `/app/chat/{edit,delete,read}/{messageId}` | JWT, message read policy; existing ownership/moderation rules remain in command services |
| SEND | `/app/call/{invite,accept,join,decline,end,leave}` | JWT; existing CallService/CallSessionService authorizes payload IDs and participant operations |
| SEND | `/app/friend/{send,remove}/{username}`, `/app/friend/{accept,reject,cancel}/{requestId}` | JWT; existing FriendService authorizes actor and request |
| SEND | `/app/calls/{callId}/boards/{boardId}/draw` | JWT, active call participant and current room membership; CanvasBoardService checks board and draw rights |
| SEND | `/app/code-sessions/{id}/{content-sync,stdin-sync,language-change,cursor-sync}` | JWT, session's call participant and current room membership; CodeSessionService checks editor/host rights |
| SUBSCRIBE | `/topic/channel.{id}` | Active channel, active server membership, effective VIEW_CHANNEL |
| SUBSCRIBE | `/topic/room.{id}` | Active room and current membership |
| SUBSCRIBE | `/topic/calls/{id}/boards`, `/topic/calls/{id}/code-sessions` | Non-ended call, ACCEPTED/JOINED participant, current room membership |
| SUBSCRIBE | `/topic/calls/{callId}/boards/{boardId}` | Same call policy; board must belong to that call |
| SUBSCRIBE | `/topic/code-sessions/{id}` | Resolve actual session and apply its call policy |
| SUBSCRIBE | `/user/queue/{messages,call,call-recording,friend-requests,errors,message-read,room-events,users}` | Current authenticated connection only; Spring user-destination translation retained |
| SEND/SUBSCRIBE | Everything else | Denied, including direct SEND to `/topic/**`, `/queue/**`, `/user/**`, wildcard subscriptions and other users' queue paths |

`/app/chat/private/{receiver}` is still declared in a frontend middleware action but has no current backend
handler and no UI call sites were found. It is explicitly denied as an unknown route, rather than adding a
new backend feature. Active private/group messages use `/app/chat/send/{roomId}`.

Frontend also subscribes to `/topic/code-sessions/{id}`. Current backend code-session events are published
to `/topic/calls/{callId}/code-sessions`; the session-specific subscription is now protected, but no new
publisher or event format was added.

Existing REST paths and functional STOMP destinations are preserved. Read receipts for channel messages
are additive events on the protected channel topic with `messageId`, `channelId`, and `readBy`; the current
channel reducer ignores events without a message eventType. Room receipts retain `/user/queue/message-read`.

## Existing subscriptions and authentication

`JwtChannelInterceptor` validates JWT and its subject on CONNECT and every operation, rejects identity/token
changes on the same connection, and permits only the inventoried SEND/SUBSCRIBE routes. Resource mutation
authorization remains in the existing application services; payload IDs are not trusted by route matching.

`WebSocketAuthorizationSessions` stores original subscriptions before user-queue translation, bound to the
connection principal. Duplicate subscription IDs and more than 128 subscriptions per connection are rejected.
UNSUBSCRIBE/DISCONNECT and transport disconnect events remove state.

`WebSocketDeliveryInterceptor` validates the bound JWT and current resource policy for every outgoing MESSAGE,
including messages on subscriptions opened before a membership/permission change. An invalid token, ACL denial
or policy-store failure suppresses delivery. Broker destination is checked against the original subscription
to prevent a queued old event from borrowing a reassigned subscription ID. No positive ACL cache is used.

Both logout methods invalidate all current WebSocket subscriptions of that user on the current application
instance. This deliberately includes other tabs/devices on that instance: the current JWT/refresh schema
does not map an individual refresh session to a WebSocket connection. A revoked connection cannot CONNECT
again under the same transport session ID. Logout methods are transactional so the lazy refresh-token user
can be resolved reliably.

## Verification

Environment: IBM Semeru Java 21.0.9, Maven 3.9.9. Set JAVA_HOME explicitly:

```powershell
$env:JAVA_HOME='C:\Users\Пользователь\.jdks\semeru-21.0.9'
mvn.cmd -o '-Dtest=MessageAccessSecurityTest,MessageHttpSecurityTest,WebSocketAclSecurityTest,WebSocketBrokerSecurityTest,AuthServiceTest,MessageServiceTest' package
mvn.cmd -o test
mvn.cmd -o -f target/security-baseline/pom.xml '-Dtest=ChannelControllerTest,CallSessionServiceTest' test
```

The first sandboxed Maven attempts failed reading the local dependency cache. Runs with access to that cache
compiled and executed the tests; no backend dependencies or pom changes were needed.

* `MessageAccessSecurityTest`: real RoomAccessService, PermissionService and MessageAccessService with repository
  fixtures. Covers author access, strangers, removed members, deleted/inactive targets, channel overrides,
  receipts, deduplication, readers and mixed batches containing a real inaccessible message. Asserts no writes
  or broadcasts occur for unauthorized batches.
* `MessageHttpSecurityTest`: real production SecurityConfig, JWT filter, controller and DTO mapping through
  MockMvc, including 401, uniform 404 responses, channel DTOs and no confidential entity fields.
* `WebSocketAclSecurityTest`: real JWTs, interceptors and policies, allowed/denied route matrix, current membership,
  permission revocation, call end, token invalidation, logout, identity changes, subscription-ID reuse.
* `WebSocketBrokerSecurityTest`: real Spring broker and inbound/application/user-destination/outbound channels.
  Verifies personalized delivery, current-room/channel delivery, revocation, broker injection rejection and
  actual WSChatController/MessageService execution and DTO publication for a legitimate `/app/chat/send`.
* Existing AuthServiceTest and MessageServiceTest are included; logout tests also assert actual registry invalidation.

Final results:

| Run | Passed | Assertion failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Targeted package: 97 new security cases + 15 existing AuthService/MessageService cases | 112 | 0 | 0 | 0 | BUILD SUCCESS; executable JAR produced |
| Full backend suite | 153 | 4 | 10 | 0 | BUILD FAILURE; 167 total |
| Unchanged HEAD: ChannelControllerTest + CallSessionServiceTest | 0 | 4 | 10 | 0 | Same 14 pre-existing failing cases |

Targeted classes: MessageAccessSecurityTest 17; MessageHttpSecurityTest 9;
WebSocketAclSecurityTest 65; WebSocketBrokerSecurityTest 6; AuthServiceTest 12; MessageServiceTest 3.
Full-suite failures are not hidden by the targeted package command.

Logs in the workspace: security-package.log, security-all-tests.log, security-baseline-tests.log.
Frontend logs: security-frontend-build.log and security-frontend-vite.log in the frontend root.
`git diff --check` passes. No commits were created.

Frontend commands (no source/lockfile edits):

```powershell
npm.cmd ci --ignore-scripts --no-audit --no-fund
npm.cmd run build
npm.cmd run build:web
```

Dependency installation succeeded. `npm run build:web` successfully built 2,087 modules.
`npm run build` fails with 88 existing TypeScript diagnostics, including duplicate loadRnnoise imports,
unused reducer arguments and incompatible code-session/call-quality types. Frontend Git status remains clean.

## Remaining risks and follow-up

* Access JWTs are still stateless and are not globally revoked by refresh-token logout. A still-valid JWT can
  create a new connection until expiry. Existing local subscriptions are revoked; distributed logout across
  application instances requires a shared auth-session/revocation mechanism. Per-delivery expiry prevents
  old subscriptions from receiving private data indefinitely even without a distributed invalidation event.
* Revoked subscriptions can remain physically registered in the simple broker until disconnect/unsubscribe;
  delivery is filtered. Data already sent or queued after a successful authorization cannot be recalled.
* Every private-topic delivery rechecks current permissions in the database. Load-test large fanouts/canvas
  traffic before production rollout; any future optimization must preserve prompt revocation.
* Persistence repositories are mocked in the new tests. Native PostgreSQL insert-conflict behavior and full
  transaction rollback against PostgreSQL were not re-tested. Broker integration excludes actual TCP/SockJS
  transport; browser/multi-device staging tests remain necessary.
* The existing PermissionService hierarchy/override precedence is retained. A broader role/folder/server
  permission audit, including soft-deleted parent resources, remains outside these three fixes.
* The 14 unrelated backend test failures and 88 frontend TypeScript diagnostics remain unfixed. The same
  backend failures were reproduced on an isolated, unmodified HEAD snapshot under target/security-baseline.
* No claims are made that all application security issues or all production acceptance gates are resolved.
