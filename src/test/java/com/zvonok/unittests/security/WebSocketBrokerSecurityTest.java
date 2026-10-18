package com.zvonok.unittests.security;

import org.springframework.transaction.PlatformTransactionManager;
import com.zvonok.config.WebSocketConfig;
import com.zvonok.controller.WSChatController;
import com.zvonok.handler.*;
import com.zvonok.model.Message;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring inbound/application/broker/user-destination/outbound pipeline, without a TCP transport. */
class WebSocketBrokerSecurityTest {

	AnnotationConfigWebApplicationContext context;
	WebSocketAclSecurityTest fixture;
	ExecutorSubscribableChannel inbound;
	SimpMessagingTemplate template;
	BlockingQueue<org.springframework.messaging.Message<?>> deliveries;

	@BeforeEach void setup() throws Exception {
		context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
		fixture = context.getBean(WebSocketAclSecurityTest.class);
		inbound = context.getBean("clientInboundChannel", ExecutorSubscribableChannel.class);
		template = context.getBean(SimpMessagingTemplate.class);
		deliveries = new LinkedBlockingQueue<>();
		context.getBean("clientOutboundChannel", ExecutorSubscribableChannel.class).subscribe(message -> {
			if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE) { deliveries.add(message); }
		});
		inbound.send(frame(StompCommand.CONNECT, null, ""));
		// StompSubProtocolHandler normally emits this after CONNECTED reaches the transport.
		context.publishEvent(new SessionConnectedEvent(this, frame(StompCommand.CONNECT, null, ""), fixture.principal));
	}

	@AfterEach void close() { if (context != null) { context.close(); } }

	@Test void personalQueueReceivesOnlyThroughUserDestinationTranslation() throws Exception {
		subscribe("/user/queue/messages");
		template.convertAndSendToUser("alice", "/queue/messages", "private");
		assertNotNull(deliveries.poll(2, TimeUnit.SECONDS));
		template.convertAndSendToUser("bob", "/queue/messages", "foreign");
		assertNull(deliveries.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test void existingRoomSubscriptionStopsReceivingAfterRemoval() throws Exception {
		subscribe("/topic/room.10");
		template.convertAndSend("/topic/room.10", "allowed");
		assertNotNull(deliveries.poll(2, TimeUnit.SECONDS));
		fixture.room.setMembers(List.of());
		template.convertAndSend("/topic/room.10", "secret");
		assertNull(deliveries.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test void formerChannelTopicCannotDeliverEvenIfPublisherAttemptsSend() throws Exception {
        assertThrows(org.springframework.messaging.MessageDeliveryException.class,
                () -> subscribe("/topic/channel.20"));
        template.convertAndSend("/topic/channel.20", "secret");
        assertNull(deliveries.poll(200, TimeUnit.MILLISECONDS));
    }

	@Test void legitimateAppSendUsesActualChatControllerAndPublishesMessageDto() throws Exception {
		subscribe("/user/queue/messages");
		inbound.send(frame(StompCommand.SEND, "/app/chat/send/10", "\"hello\""));
		var delivered = deliveries.poll(2, TimeUnit.SECONDS);
		assertNotNull(delivered);
		String json = new String((byte[]) delivered.getPayload(), StandardCharsets.UTF_8);
		assertTrue(json.contains("hello")); assertTrue(json.contains("sender"));
		assertFalse(json.contains("password")); assertFalse(json.contains("email"));
	}

	@Test void brokerSendCannotInjectAnEvent() throws Exception {
		subscribe("/topic/room.10");
		assertThrows(RuntimeException.class, () -> inbound.send(frame(StompCommand.SEND, "/topic/room.10", "injected")));
		assertThrows(RuntimeException.class, () -> inbound.send(frame(StompCommand.SEND, "/queue/messages", "injected")));
		assertNull(deliveries.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test void logoutStopsExistingPersonalBrokerSubscription() throws Exception {
		subscribe("/user/queue/messages"); fixture.sessions.revokeUser("alice");
		template.convertAndSendToUser("alice", "/queue/messages", "secret");
		assertNull(deliveries.poll(200, TimeUnit.MILLISECONDS));
	}

	void subscribe(String destination) {
		var subscription = frame(StompCommand.SUBSCRIBE, destination, "");
		String brokerDestination = destination;
		if (destination.startsWith("/user/")) {
			brokerDestination = context.getBean(UserDestinationResolver.class).resolveDestination(subscription)
					.getTargetDestinations().iterator().next();
		}
		inbound.send(subscription);
		var h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); h.setDestination(brokerDestination);
		var probe = MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
		await().atMost(Duration.ofSeconds(2)).until(() -> context.getBean(SimpleBrokerMessageHandler.class)
				.getSubscriptionRegistry().findSubscriptions(probe).containsKey("wire1"));
	}

	org.springframework.messaging.Message<byte[]> frame(StompCommand command, String destination, String body) {
		var h = StompHeaderAccessor.create(command); h.setSessionId("wire1"); h.setUser(fixture.principal);
		h.setSessionAttributes(new java.util.HashMap<>());
		h.setSubscriptionId("sub1"); if (destination != null) { h.setDestination(destination); }
		h.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
		return MessageBuilder.createMessage(body.getBytes(StandardCharsets.UTF_8), h.getMessageHeaders());
	}

	@Configuration(proxyBeanMethods = false)
	@Import(WebSocketConfig.class)
	static class Config implements WebSocketMessageBrokerConfigurer {
		@Bean WebSocketAclSecurityTest fixture() { var fixture = new WebSocketAclSecurityTest(); fixture.setup(); return fixture; }
		@Bean JwtChannelInterceptor inbound(WebSocketAclSecurityTest f) { return f.inbound; }
		@Bean WebSocketDeliveryInterceptor outbound(WebSocketAclSecurityTest f) { return f.outbound; }
		@Bean JwtHandshakeInterceptor handshake(WebSocketAclSecurityTest f) { return new JwtHandshakeInterceptor(f.tokens); }
		@Bean JwtHandshakeHandler handshakeHandler() { return new JwtHandshakeHandler(); }
		@Bean MessageService messages(WebSocketAclSecurityTest f, SimpMessagingTemplate template) {
			var repository = mock(MessageRepository.class);
			when(repository.save(any(Message.class))).thenAnswer(invocation -> { Message m = invocation.getArgument(0); m.setId(100L); return m; });
			var roomService = mock(RoomService.class); when(roomService.getRoomForUpdate(10L, "alice")).thenReturn(f.room);
			return new MessageService(repository, f.messageAccess, template, roomService, f.users, mock(MessageAttachmentRepository.class), mock(S3Service.class), mock(RoomReadStateService.class), mock(PlatformTransactionManager.class));
		}
		@Bean WSChatController chat(MessageService messages) { return new WSChatController(messages, mock(MessageReadStatusService.class)); }
		@Override public void configureClientInboundChannel(ChannelRegistration registration) {
			registration.taskExecutor().corePoolSize(1).maxPoolSize(1);
		}
	}
}
