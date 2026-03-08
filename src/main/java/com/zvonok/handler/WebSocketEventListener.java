package com.zvonok.handler;

import java.security.Principal;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.zvonok.service.WebSocketActiveSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

	private final WebSocketActiveSessionService sessionService;

	@EventListener
	public void handleSessionConnect(SessionConnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		Principal principal = accessor.getUser();
		String sessionId = accessor.getSessionId();

		if (principal != null && sessionId != null) {
			sessionService.addSession(principal.getName(), sessionId);
			log.info("User '{}' connected to WebSocket, sessionId={}", principal.getName(),
					sessionId);

			System.out.println("------------------------------------------");
			Set<String> onlineUsers = sessionService.getOnlineUsers();
			for (int i = 0; i < onlineUsers.size(); i++) {
				onlineUsers.forEach(user -> {
					System.out.println(user);
				});
			}

			System.out.println("------------------------------------------");
		}
	}

	@EventListener
	public void handleSessionDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();

		if (sessionId != null) {
			sessionService.removeSession(sessionId);
			log.info("User disconnected from WebSocket, sessionId={}, closeStatus={}", sessionId,
					event.getCloseStatus());
		}
		log.info("Disconnect sessionId=" + sessionId + ", closeStatus=" + event.getCloseStatus());
		System.out.println("------------------------------------------");
		Set<String> onlineUsers = sessionService.getOnlineUsers();
		for (int i = 0; i < onlineUsers.size(); i++) {
			onlineUsers.forEach(user -> {
				System.out.println(user);
			});
		}

		System.out.println("------------------------------------------");
	}
}
