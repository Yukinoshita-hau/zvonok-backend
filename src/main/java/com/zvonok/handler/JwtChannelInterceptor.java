package com.zvonok.handler;

import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.WebSocketAuthorizationSessions;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.WebSocketAccessService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

	private final JwtTokenProvider jwtTokenProvider;
	private final WebSocketAccessService access;
	private final WebSocketAuthorizationSessions sessions;

	public JwtChannelInterceptor(JwtTokenProvider jwtTokenProvider, @Lazy WebSocketAccessService access,
			WebSocketAuthorizationSessions sessions) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.access = access;
		this.sessions = sessions;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
		StompCommand command = headers.getCommand();
		if (command == null) { return message; } // Heartbeat, cannot reach broker destinations.
		String sessionId = headers.getSessionId();
		if (command == StompCommand.DISCONNECT) {
			sessions.disconnect(sessionId);
			return message;
		}
		if (!(headers.getUser() instanceof UserPrincipal principal) || sessionId == null
				|| !jwtTokenProvider.isValidToken(principal.getToken())
				|| !principal.getName().equals(jwtTokenProvider.getUsername(principal.getToken()))) {
			throw new AuthenticatedPrincipalRequiredException("Authenticated session required");
		}
		if (command == StompCommand.CONNECT) {
			if (sessions.connect(sessionId, principal) == null) { throw denied(); }
			return message;
		}
		var session = sessions.get(sessionId);
		if (session == null || !session.getPrincipal().getName().equals(principal.getName())
				|| !session.getPrincipal().getToken().equals(principal.getToken())) { throw denied(); }
		switch (command) {
			case SEND -> access.checkSend(headers.getDestination(), principal.getName());
			case SUBSCRIBE -> {
				access.checkSubscribe(headers.getDestination(), principal.getName());
				String id = headers.getSubscriptionId();
				if (id == null || session.getSubscriptions().size() >= 128
						|| session.getSubscriptions().putIfAbsent(id, headers.getDestination()) != null) { throw denied(); }
			}
			case UNSUBSCRIBE -> {
				if (headers.getSubscriptionId() == null) { throw denied(); }
				session.getSubscriptions().remove(headers.getSubscriptionId());
			}
			default -> throw denied();
		}
		return message;
	}

	private InsufficientPermissionsException denied() {
		return new InsufficientPermissionsException("Destination access denied");
	}
}
