package com.zvonok.security;

import com.zvonok.security.dto.UserPrincipal;
import lombok.Getter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Original subscriptions before Spring's user-destination translation, for delivery ACLs. */
@Component
public class WebSocketAuthorizationSessions {

	@Getter
	public static class Session {
		private final UserPrincipal principal;
		private final Map<String, String> subscriptions = new ConcurrentHashMap<>();
		private volatile boolean revoked;
		Session(UserPrincipal principal) { this.principal = principal; }
	}

	private final Map<String, Session> sessions = new ConcurrentHashMap<>();

	public Session connect(String id, UserPrincipal principal) {
		Session session = new Session(principal);
		return sessions.putIfAbsent(id, session) == null ? session : null;
	}

	public Session get(String id) {
		Session session = id == null ? null : sessions.get(id);
		return session == null || session.revoked ? null : session;
	}
	public void disconnect(String id) { if (id != null) sessions.remove(id); }
	public void revokeUser(String username) {
		sessions.values().stream().filter(session -> session.principal.getName().equals(username))
				.forEach(session -> { session.revoked = true; session.subscriptions.clear(); });
	}

	@EventListener
	public void disconnected(SessionDisconnectEvent event) { disconnect(event.getSessionId()); }
}
