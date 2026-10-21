package com.zvonok.unittests.security;

import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.RoomNotFoundException;
import com.zvonok.exception.UserNotMemberRoomException;
import com.zvonok.handler.JwtChannelInterceptor;
import com.zvonok.handler.WebSocketDeliveryInterceptor;
import com.zvonok.model.*;
import com.zvonok.repository.*;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.WebSocketAuthorizationSessions;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSocketAclSecurityTest {

	final RoomRepository rooms = mock(RoomRepository.class);
	final UserService users = mock(UserService.class);
	final RoomAccessService roomAccess = new RoomAccessService(rooms, users);
	final MessageRepository messages = mock(MessageRepository.class);
	final MessageAccessService messageAccess = new MessageAccessService(messages, roomAccess);
	final WebSocketAccessService access = new WebSocketAccessService(roomAccess, messageAccess);
	final JwtTokenProvider tokens = new JwtTokenProvider();
	final WebSocketAuthorizationSessions sessions = new WebSocketAuthorizationSessions();
	final JwtChannelInterceptor inbound = new JwtChannelInterceptor(tokens, access, sessions);
	final WebSocketDeliveryInterceptor outbound = new WebSocketDeliveryInterceptor(tokens, access, sessions);
	UserPrincipal principal;
	Room room;

	@BeforeEach void setup() {
		tokens.setJwtSecret(Base64.getEncoder().encodeToString(new byte[64])); tokens.setJwtExpirationMs(60000);
		principal = new UserPrincipal("alice", tokens.generateToken("alice", 1L));
		User alice = new User(); alice.setId(1L); alice.setUsername("alice");
		when(users.getUser("alice")).thenReturn(alice);
		room = new Room(); room.setId(10L); room.setIsActive(true); room.setMembers(List.of(alice));
		when(rooms.findById(10L)).thenReturn(Optional.of(room));
		Room foreignRoom = new Room(); foreignRoom.setId(999L); foreignRoom.setMembers(List.of()); foreignRoom.setIsActive(true);
		when(rooms.findById(999L)).thenReturn(Optional.of(foreignRoom));
		inbound.preSend(frame(StompCommand.CONNECT, null), null);
	}

	@ParameterizedTest @ValueSource(strings = {"/topic/room.10",
			"/user/queue/messages", "/user/queue/call", "/user/queue/errors",
			"/user/queue/friend-requests", "/user/queue/message-read", "/user/queue/room-events", "/user/queue/users"})
	void authorizedSubscriptionsWork(String destination) {
		Message<?> frame = frame(StompCommand.SUBSCRIBE, destination);
		assertSame(frame, inbound.preSend(frame, null));
		assertNotNull(outbound.preSend(delivery(destination), null));
	}

	@ParameterizedTest @ValueSource(strings = {"/topic/channel.20", "/topic/channel.999", "/topic/room.999",
			"/topic/calls/40/boards", "/topic/calls/40/boards/50", "/topic/calls/40/code-sessions",
			"/topic/code-sessions/60", "/queue/messages", "/topic/**",
			"/user/bob/queue/messages", "/user/queue/unknown", "/user/queue/call-recording",
			"/topic/unknown", "/app/chat/send/10", "/topic/channel.20/extra"})
	void forbiddenSubscriptionsDenied(String destination) {
		RuntimeException denied = assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.SUBSCRIBE, destination), null));
		assertTrue(denied instanceof InsufficientPermissionsException || denied instanceof UserNotMemberRoomException
				|| denied instanceof RoomNotFoundException, "Must fail by access policy, not an incidental runtime error");
		assertNull(outbound.preSend(delivery(destination), null));
	}

	@ParameterizedTest @ValueSource(strings = {"/topic/channel.20", "/queue/messages", "/user/queue/messages",
			"/app/unknown", "/topic/room.10", "/app/chat/private/10", "/app/chat/send/10/extra"})
	void brokerAndUnknownSendsDenied(String destination) {
		assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.SEND, destination), null));
	}

	@ParameterizedTest @ValueSource(strings = {"/app/chat/send/10", "/app/call/invite", "/app/call/accept",
			"/app/call/join", "/app/call/decline", "/app/call/end", "/app/call/leave", "/app/friend/send/bob",
			"/app/friend/remove/bob", "/app/friend/accept/1", "/app/friend/reject/1", "/app/friend/cancel/1"})
	void knownCommandsReachApplicationServices(String destination) {
		assertNotNull(inbound.preSend(frame(StompCommand.SEND, destination), null));
	}
	@ParameterizedTest @ValueSource(strings = {"/app/chat/channel/20", "/app/server/join/1", "/app/channel/send/20", "/app/calls/40/boards/50/draw",
			"/app/code-sessions/60/content-sync", "/app/code-sessions/60/stdin-sync",
			"/app/code-sessions/60/language-change", "/app/code-sessions/60/cursor-sync"})
	void removedFeatureCommandsAreDenied(String destination) {
		assertThrows(InsufficientPermissionsException.class,
				() -> inbound.preSend(frame(StompCommand.SEND, destination), null));
	}
	@Test void removedFeatureTopicsAreDenied() {
		assertThrows(InsufficientPermissionsException.class,
				() -> inbound.preSend(frame(StompCommand.SUBSCRIBE, "/topic/calls/40/boards"), null));
		assertThrows(InsufficientPermissionsException.class,
				() -> inbound.preSend(frame(StompCommand.SUBSCRIBE, "/topic/code-sessions/60"), null));
	}

	@Test void removedRoomMemberCannotSubscribeOrReceiveExistingSubscription() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room.10"), null);
		room.setMembers(List.of());
		assertThrows(RuntimeException.class, () -> access.checkSubscribe("/topic/room.10", "alice"));
		assertNull(outbound.preSend(delivery("/topic/room.10"), null));
		assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.SEND, "/app/chat/send/10"), null));
	}

	@Test void principalMustMatchJwtSubject() {
		principal = new UserPrincipal("alice", tokens.generateToken("bob", 2L));
		assertThrows(AuthenticatedPrincipalRequiredException.class, () -> inbound.preSend(frame(StompCommand.CONNECT, null), null));
	}

	@Test void logoutStopsOldPersonalDeliveryAndCommands() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null);
		sessions.revokeUser("alice");
		assertNull(outbound.preSend(delivery("/user/queue/messages"), null));
		assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.SEND, "/app/call/invite"), null));
		assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.CONNECT, null), null));
	}
	@Test void invalidatedTokenStopsOldDelivery() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null);
		byte[] changedSecret = new byte[64]; java.util.Arrays.fill(changedSecret, (byte) 1);
		tokens.setJwtSecret(Base64.getEncoder().encodeToString(changedSecret));
		assertNull(outbound.preSend(delivery("/user/queue/messages"), null));
	}
	@Test void expiredTokenCannotConnect() {
		tokens.setJwtExpirationMs(-1000);
		principal = new UserPrincipal("alice", tokens.generateToken("alice", 1L));
		assertThrows(AuthenticatedPrincipalRequiredException.class, () -> inbound.preSend(frame(StompCommand.CONNECT, null), null));
	}
	@Test void identityCannotChangeOnSameConnection() {
		principal = new UserPrincipal("bob", tokens.generateToken("bob", 2L));
		assertThrows(RuntimeException.class, () -> inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null));
	}
	@Test void queuedEventCannotUseReassignedSubscriptionId() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null);
		inbound.preSend(frame(StompCommand.UNSUBSCRIBE, null), null);
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room.10"), null);
		assertNull(outbound.preSend(delivery("/user/queue/messages"), null));
	}

	@Test void unsubscribeStopsDelivery() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null);
		inbound.preSend(frame(StompCommand.UNSUBSCRIBE, null), null);
		assertNull(outbound.preSend(delivery("/user/queue/messages"), null));
	}
	@Test void disconnectStopsDelivery() {
		inbound.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages"), null);
		inbound.preSend(frame(StompCommand.DISCONNECT, null), null);
		assertNull(outbound.preSend(delivery("/user/queue/messages"), null));
	}

	Message<?> frame(StompCommand command, String destination) {
		var h = StompHeaderAccessor.create(command); h.setSessionId("s1"); h.setUser(principal);
		h.setSubscriptionId("sub1"); if (destination != null) { h.setDestination(destination); }
		return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
	}
	Message<?> delivery(String destination) {
		var h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); h.setSessionId("s1"); h.setSubscriptionId("sub1");
		h.setDestination(destination.startsWith("/user/") ? destination.substring(5) + "-users1" : destination);
		return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
	}
}
