package com.zvonok.service;

import com.zvonok.exception.InsufficientPermissionsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebSocketAccessService {

	private static final Pattern ROOM = Pattern.compile("^/topic/room\\.([1-9][0-9]*)$");
	private static final Set<String> PERSONAL = Set.of("messages", "call", "call-recording",
			"friend-requests", "errors", "message-read", "room-events", "users");
	private static final Pattern COMMAND = Pattern.compile(
			"^/app/(?:chat/(?:send|edit|delete|read)/[1-9][0-9]*"
			+ "|call/(?:invite|accept|join|decline|end|leave)"
			+ "|friend/(?:send|remove)/[^/\\s*{}]+|friend/(?:accept|reject|cancel)/[1-9][0-9]*"
			+ ")$");

	private final RoomAccessService roomAccessService;
	private final MessageAccessService messageAccessService;

	public void checkSend(String destination, String username) {
		if (destination == null || !COMMAND.matcher(destination).matches()) { throw denied(); }
		// Existing command services authorize mutation rights and IDs carried in payloads.
		String[] parts = destination.split("/");
		switch (parts[2]) {
			case "chat" -> {
				Long id = Long.valueOf(parts[4]);
				if (parts[3].equals("send")) {
					roomAccessService.getRoomForUser(id, username);
				} else {
					messageAccessService.getReadableMessage(id, username);
				}
			}
			default -> { /* Call/friend services authorize payload/path IDs. */ }
		}
	}

	public void checkSubscribe(String destination, String username) {
		if (destination == null || username == null) { throw denied(); }
		if (destination.startsWith("/user/queue/") && PERSONAL.contains(destination.substring(12))) { return; }
		Matcher match = ROOM.matcher(destination);
		if (match.matches()) {
			roomAccessService.getRoomForUser(id(match, 1), username);
			return;
		}
		throw denied();
	}

	private Long id(Matcher matcher, int group) {
		try { return Long.valueOf(matcher.group(group)); }
		catch (NumberFormatException e) { throw denied(); }
	}

	private InsufficientPermissionsException denied() {
		return new InsufficientPermissionsException("Destination access denied");
	}
}
