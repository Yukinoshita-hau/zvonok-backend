package com.zvonok.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WebSocketActiveSessionService {

	private final Map<String, String> sessionIdToUser = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();

	public void addSession(String username, String sessionId) {
		userToSessions.compute(username, (k, sessions) -> {
			if (sessions == null) {
				Set<String> newSessions = ConcurrentHashMap.newKeySet();
				newSessions.add(sessionId);
				return newSessions;
			}

			sessions.add(sessionId);
			return sessions;
		});
		sessionIdToUser.put(sessionId, username);
	}

	public void removeSession(String sessionId) {
		String username = sessionIdToUser.remove(sessionId);
		if (username == null) {
			return;
		}
		userToSessions.computeIfPresent(username, (u, sessions) -> {
			sessions.remove(sessionId);
			return sessions.isEmpty() ? null : sessions;
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
		return sessions == null ? 0: sessions.size();
	}

	public Map<String, Integer> getOnlineUserSessionsCounts() {
		Map<String, Integer> result = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry: userToSessions.entrySet()) {
			result.put(entry.getKey(), entry.getValue().size());	
		}
		return result;
	}
}
