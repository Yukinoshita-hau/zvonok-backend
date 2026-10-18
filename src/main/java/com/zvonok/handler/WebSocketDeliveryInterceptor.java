package com.zvonok.handler;

import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.WebSocketAuthorizationSessions;
import com.zvonok.service.WebSocketAccessService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketDeliveryInterceptor implements ChannelInterceptor {

	private final WebSocketAuthorizationSessions sessions;
	private final JwtTokenProvider tokens;
	private final WebSocketAccessService access;

	public WebSocketDeliveryInterceptor(JwtTokenProvider tokens, @Lazy WebSocketAccessService access,
			WebSocketAuthorizationSessions sessions) {
		this.tokens = tokens;
		this.access = access;
		this.sessions = sessions;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		var headers = SimpMessageHeaderAccessor.wrap(message);
		if (headers.getMessageType() != SimpMessageType.MESSAGE) { return message; }
		var session = sessions.get(headers.getSessionId());
		if (session == null || headers.getSubscriptionId() == null) { return null; }
		String destination = session.getSubscriptions().get(headers.getSubscriptionId());
		if (destination == null) { return null; }
		String brokerDestination = destination.startsWith("/user/queue/")
				? destination.substring("/user".length()) + "-user" + headers.getSessionId()
				: destination;
		// A queued event from a previous subscription must not be authorized by a reused ID.
		if (!brokerDestination.equals(headers.getDestination())) { return null; }
		try {
			var principal = session.getPrincipal();
			if (!tokens.isValidToken(principal.getToken())
					|| !principal.getName().equals(tokens.getUsername(principal.getToken()))) {
				sessions.disconnect(headers.getSessionId());
				return null;
			}
			// Recheck every delivery: revoked rights apply to already open subscriptions.
			access.checkSubscribe(destination, principal.getName());
			return message;
		} catch (RuntimeException denied) {
			return null; // Fail closed, including a policy-store outage.
		}
	}
}
