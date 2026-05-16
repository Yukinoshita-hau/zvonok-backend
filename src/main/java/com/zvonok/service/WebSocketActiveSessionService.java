package com.zvonok.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class WebSocketActiveSessionService {

	private final Map<String, String> sessionIdToUser = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();

	public record AddSessionResult(String username, String sessionId, int sessionCount,
			boolean firstSession) {
	}

	public record RemoveSessionResult(String username, String sessionId, int remainingSessions,
			boolean lastSessionRemoved) {
	}

	public AddSessionResult addSession(String username, String sessionId) {
		String previousUsername = sessionIdToUser.put(sessionId, username);

		if (previousUsername != null && !previousUsername.equals(username)) {
			removeSessionFromUser(previousUsername, sessionId);
		}

		AtomicInteger sessionCount = new AtomicInteger();

		userToSessions.compute(username, (key, sessions) -> {
			if (sessions == null) {
				sessions = ConcurrentHashMap.newKeySet();
			}

			sessions.add(sessionId);
			sessionCount.set(sessions.size());

			return sessions;
		});

		return new AddSessionResult(username, sessionId, sessionCount.get(),
				sessionCount.get() == 1);
	}


	public RemoveSessionResult removeSession(String sessionId) {
		String username = sessionIdToUser.remove(sessionId);

		if (username == null) {
			return null;
		}

		AtomicInteger remainingSessions = new AtomicInteger();

		userToSessions.computeIfPresent(username, (key, sessions) -> {
			sessions.remove(sessionId);
			remainingSessions.set(sessions.size());

			return sessions.isEmpty() ? null : sessions;
		});

		return new RemoveSessionResult(username, sessionId, remainingSessions.get(),
				remainingSessions.get() == 0);
	}

	public void removeSessionFromUser(String username, String sessionId) {
		userToSessions.computeIfPresent(username, (key, sessions) -> {
			sessions.remove(sessionId);	
			return sessions.isEmpty() ? null: sessions;
		});
	}

	public boolean isUserOnline(String username) {
		Set<String> sessions = userToSessions.get(username);

		return sessions != null && !sessions.isEmpty();
	}

	public Set<String> getOnlineUsers() {
		return Set.copyOf(userToSessions.keySet());
	}

	public int getUserSessionCount(String username) {
		Set<String> sessions = userToSessions.get(username);
		return sessions == null ? 0 : sessions.size();
	}

	public Map<String, Integer> getOnlineUserSessionsCounts() {
		Map<String, Integer> result = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry : userToSessions.entrySet()) {
			result.put(entry.getKey(), entry.getValue().size());
		}
		return result;
	}
}
