package com.zvonok.service;

import com.zvonok.controller.dto.CreateRoomInviteRequest;
import com.zvonok.controller.dto.CreateRoomInviteResponse;
import com.zvonok.controller.dto.JoinRoomInviteResponse;
import com.zvonok.controller.dto.RoomInvitePreviewDto;
import com.zvonok.controller.dto.AddedRoomMemberDto;
import com.zvonok.exception.InvalidRoomInviteException;
import com.zvonok.exception.RoomInviteNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Room;
import com.zvonok.model.RoomInvite;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.RoomInviteRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.service.dto.RoomEvents;
import com.zvonok.service.enums.BrokerPath;
import com.zvonok.service.enums.RoomEventsType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomInviteService {

	private final RoomInviteRepository roomInviteRepository;
	private final RoomRepository roomRepository;
	private final RoomService roomService;
	private final UserService userService;
	private final SimpMessagingTemplate messagingTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	@Transactional
	public CreateRoomInviteResponse createInvite(Long roomId, String username,
			CreateRoomInviteRequest request) {
		User user = userService.getUser(username);
		Room room = roomService.getRoom(roomId, username);
		ensureGroupRoom(room);
		validateCreateRequest(request);
		String token = generateToken();
		RoomInvite invite = new RoomInvite();
		invite.setRoom(room);
		invite.setTokenHash(hashToken(token));
		invite.setCreatedBy(user);
		invite.setExpiresAt(request == null ? null : request.expiresAt());
		invite.setMaxUses(request == null ? null : request.maxUses());
		invite = roomInviteRepository.save(invite);
		return new CreateRoomInviteResponse(room.getId(), token, "/invite/" + token,
				invite.getExpiresAt(), invite.getMaxUses());
	}

	@Transactional(readOnly = true)
	public RoomInvitePreviewDto preview(String token, String username) {
		User user = userService.getUser(username);
		RoomInvite invite = getInvite(token);
		Room room = invite.getRoom();
		boolean alreadyMember = isMember(room, user.getId());
		return new RoomInvitePreviewDto(room.getId(), room.getName(), room.getAvatarUrl(),
				room.getMembers().size(), invite.getCreatedBy().getUsername(), alreadyMember,
				isExpired(invite));
	}

	@Transactional
	public JoinRoomInviteResponse join(String token, String username) {
		User user = userService.getUser(username);
		RoomInvite invite = getInvite(token);
		validateInvite(invite);
		Room room = roomRepository.findByIdForUpdate(invite.getRoom().getId())
				.orElseThrow(() -> new RoomInviteNotFoundException(
						HttpResponseMessage.HTTP_ROOM_INVITE_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
		if (isMember(room, user.getId())) {
			return new JoinRoomInviteResponse(room.getId(), false, true);
		}
		room.getMembers().add(user);
		roomRepository.save(room);
		invite.setUsesCount(invite.getUsesCount() + 1);
		roomInviteRepository.save(invite);
		publishJoined(room, user);
		return new JoinRoomInviteResponse(room.getId(), true, false);
	}

	private RoomInvite getInvite(String token) {
		return roomInviteRepository.findByTokenHashAndActiveTrue(hashToken(token))
				.orElseThrow(() -> new RoomInviteNotFoundException(
						HttpResponseMessage.HTTP_ROOM_INVITE_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	private void validateCreateRequest(CreateRoomInviteRequest request) {
		if (request == null) {
			return;
		}
		if (request.maxUses() != null && request.maxUses() <= 0) {
			throw new InvalidRoomInviteException(
					HttpResponseMessage.HTTP_ROOM_INVITE_INVALID_RESPONSE_MESSAGE.getMessage());
		}
		if (request.expiresAt() != null && request.expiresAt().isBefore(LocalDateTime.now())) {
			throw new InvalidRoomInviteException(
					HttpResponseMessage.HTTP_ROOM_INVITE_INVALID_RESPONSE_MESSAGE.getMessage());
		}
	}

	private void validateInvite(RoomInvite invite) {
		if (isExpired(invite) || (invite.getMaxUses() != null
				&& invite.getUsesCount() >= invite.getMaxUses())) {
			throw new InvalidRoomInviteException(
					HttpResponseMessage.HTTP_ROOM_INVITE_INVALID_RESPONSE_MESSAGE.getMessage());
		}
	}

	private boolean isExpired(RoomInvite invite) {
		return invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now());
	}

	private void ensureGroupRoom(Room room) {
		if (room.getType() != RoomType.GROUP) {
			throw new InvalidRoomInviteException(
					HttpResponseMessage.HTTP_ROOM_GROUP_ONLY_RESPONSE_MESSAGE.getMessage());
		}
	}

	private boolean isMember(Room room, Long userId) {
		return room.getMembers().stream().anyMatch(member -> member.getId().equals(userId));
	}

	private void publishJoined(Room room, User user) {
		AddedRoomMemberDto member = new AddedRoomMemberDto(user.getId(), user.getUsername(),
				user.getAvatarUrl(), LocalDateTime.now());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("roomId", room.getId());
		payload.put("userId", user.getId());
		payload.put("username", user.getUsername());
		payload.put("avatarUrl", user.getAvatarUrl());
		payload.put("joinedAt", member.joinedAt());
		RoomEvents event = RoomEvents.builder().type(RoomEventsType.ROOM_MEMBER_JOINED_BY_INVITE)
				.payload(payload).build();
		for (User roomMember : room.getMembers()) {
			messagingTemplate.convertAndSendToUser(roomMember.getUsername(),
					BrokerPath.ROOM_EVENTS_QUEUE_PATH.getPath(), event);
		}
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new InvalidRoomInviteException(
					HttpResponseMessage.HTTP_ROOM_INVITE_INVALID_RESPONSE_MESSAGE.getMessage());
		}
	}
}
