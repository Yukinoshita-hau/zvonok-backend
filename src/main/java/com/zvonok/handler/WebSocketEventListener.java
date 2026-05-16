package com.zvonok.handler;

import java.security.Principal;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.zvonok.service.UserService;
import com.zvonok.service.WebSocketActiveSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

	private static final int OFFLINE_GRACE_SECONDS = 5;

	private final WebSocketActiveSessionService sessionService;
	private final UserService userService;
	private final TaskScheduler taskScheduler;

	@EventListener
	public void handleSessionConnect(SessionConnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

		Principal principal = accessor.getUser();
		String sessionId = accessor.getSessionId();

		if (principal == null || sessionId == null) {
			return;
		}

		String username = principal.getName();

		WebSocketActiveSessionService.AddSessionResult result =
				sessionService.addSession(username, sessionId);

		if (result.firstSession()) {
			userService.markOnline(username);
		}

		log.info(
				"User '{}' connected to WebSocket, sessionId={}, sessions={}",
				username,
				sessionId,
				result.sessionCount()
		);

		log.debug("Online users: {}", sessionService.getOnlineUserSessionsCounts());
	}

	@EventListener
	public void handleSessionDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

		String sessionId = accessor.getSessionId();

		if (sessionId == null) {
			return;
		}

		WebSocketActiveSessionService.RemoveSessionResult result =
				sessionService.removeSession(sessionId);

		if (result == null) {
			log.info(
					"Unknown WebSocket session disconnected, sessionId={}, closeStatus={}",
					sessionId,
					event.getCloseStatus()
			);
			return;
		}

		log.info(
				"User '{}' disconnected from WebSocket, sessionId={}, remainingSessions={}, closeStatus={}",
				result.username(),
				sessionId,
				result.remainingSessions(),
				event.getCloseStatus()
		);

		if (result.lastSessionRemoved()) {
			scheduleOfflineIfStillDisconnected(result.username());
		}

		log.debug("Online users: {}", sessionService.getOnlineUserSessionsCounts());
	}

	private void scheduleOfflineIfStillDisconnected(String username) {
		taskScheduler.schedule(
				() -> {
					if (sessionService.isUserOnline(username)) {
						return;
					}

					userService.markOffline(username);

					log.info("User '{}' marked OFFLINE after grace period", username);
				},
				Instant.now().plusSeconds(OFFLINE_GRACE_SECONDS)
		);
	}
}
