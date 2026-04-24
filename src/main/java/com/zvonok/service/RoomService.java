package com.zvonok.service;

import com.zvonok.controller.dto.RoomMemberShortDto;
import com.zvonok.controller.dto.RoomResponse;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.InvalidRoomSizeException;
import com.zvonok.exception.RoomSizeMaxTenMembersException;
import com.zvonok.exception.UserIsNotYourFriendException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.RoomRepository;
import com.zvonok.service.dto.RoomEvents;
import com.zvonok.service.enums.BrokerPath;
import com.zvonok.service.enums.RoomEventsType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing private and group chat rooms. Сервис для управления приватными и групповыми
 * чат-комнатами.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

	private final RoomRepository roomRepository;
	private final UserService userService;
	private final RoomAccessService roomAccessService;
	private final RoomReadStateService roomReadStateService;
	private final FriendShipAccessService friendAccessService;
	private final SimpMessagingTemplate messagingTemplate;

	public Room getRoom(Long id, String username) {
		return roomAccessService.getRoomForUser(id, username);
	}

	public List<RoomResponse> getUserRoomsWithUnread(String username) {
		User user = userService.getUser(username);

		List<Room> rooms = roomRepository.findAllByMembersContainingAndIsActiveTrue(user);

		List<Long> roomIds = rooms.stream().map(Room::getId).toList();

		Map<Long, Integer> unreadCounts =
				roomReadStateService.getUnreadCountsForRooms(user.getId(), roomIds);


		return rooms.stream().map(room -> {
			int unread = unreadCounts.getOrDefault(room.getId(), 0);
			Long firstUnreadId = unread > 0
					? roomReadStateService.getFirstUnreadMessageId(user.getId(), room.getId())
					: null;
			return mapToRoomResponse(room, unread, firstUnreadId);
		}).toList();
	}

	public Room createOrGetPrivateRoom(String username1, String username2) {
		User user1 = userService.getUser(username1);
		User user2 = userService.getUser(username2);

		Optional<Room> existingRoom = findPrivateRoomBetweenUsers(user1.getId(), user2.getId());
		if (existingRoom.isPresent()) {
			return existingRoom.get();
		}

		List<User> members = new ArrayList<>();
		members.add(user1);
		members.add(user2);

		Room room = new Room();
		// У приватных комнат нет названия (name = null), так как это приватная комната
		// между двумя
		// чуваками.
		room.setName(null);
		room.setType(RoomType.PRIVATE);
		room.setIsActive(true);
		room.setCreatedAt(LocalDateTime.now());
		room.setMembers(members);
		room.setAvatarUrl(null);
		room.setLastMessageId(null);
		room.setLastMessageContent(null);
		room.setLastActivityAt(room.getCreatedAt());

		return roomRepository.save(room);
	}

	public Room createGroupRoom(String creatorUsername, String roomName,
			List<String> roomMemberUsernames) {
		User creator = userService.getUser(creatorUsername);

		List<User> members = new ArrayList<>();

		Long creatorUsernameId = creator.getId();
		for (String username : roomMemberUsernames) {
			User member = userService.getUser(username);

			if (member.getId().equals(creator.getId())) {
				continue;
			}

			if (!friendAccessService.areFriends(creatorUsernameId, member.getId())) {
				throw new UserIsNotYourFriendException(
						username + HttpResponseMessage.HTTP_USER_NOT_YOUR_FRIEND_RESPONSE_MESSAGE
								.getMessage());
			}

			if (members.stream().noneMatch(u -> u.getId().equals(member.getId()))) {
				members.add(member);
			}
		}

		members.add(creator);

		if (members.size() < 3) {
			throw new InvalidRoomSizeException(
					HttpResponseMessage.HTTP_INVALID_ROOM_SIZE_RESPONSE_MESSAGE.getMessage());
		}

		if (members.size() > 15) {
			throw new RoomSizeMaxTenMembersException(
					HttpResponseMessage.HTTP_ROOM_SIZE_MAX_FIFTEEN_MEMBERS_RESPONSE_MESSAGE
							.getMessage());
		}


		Room room = new Room();
		room.setName(roomName);
		room.setType(RoomType.GROUP);
		room.setIsActive(true);
		room.setCreatedAt(LocalDateTime.now());
		room.setAvatarUrl(null);
		room.setMembers(members);
		room.setLastMessageId(null);
		room.setLastMessageContent(null);
		room.setLastActivityAt(room.getCreatedAt());

		Room savedRoom = roomRepository.save(room);

		RoomEvents response	= RoomEvents.builder().type(RoomEventsType.ROOM_CREATED).build();

		room.getMembers().stream().forEach(m -> {
			messagingTemplate.convertAndSendToUser(m.getUsername(), BrokerPath.ROOM_EVENTS_QUEUE_PATH.getPath(), response);
		});

		return savedRoom;
	}

	private Optional<Room> findPrivateRoomBetweenUsers(Long user1, Long user2) {
		return roomRepository.findPrivateRoomBetweenUsers(user1, user2);
	}

	private RoomResponse mapToRoomResponse(Room room, int unreadCount, Long firstUnreadMessageId) {
		RoomResponse dto = new RoomResponse();
		dto.setId(room.getId());
		dto.setName(room.getName());
		dto.setType(room.getType());
		dto.setAvatarUrl(room.getAvatarUrl());
		dto.setIsActive(room.getIsActive());
		dto.setCreatedAt(room.getCreatedAt());
		dto.setLastMessageId(room.getLastMessageId());
		dto.setLastMessageContent(room.getLastMessageContent());
		dto.setLastActivityAt(room.getLastActivityAt());
		dto.setUnreadCount(unreadCount);
		dto.setFirstUnreadMessageId(firstUnreadMessageId);

		List<RoomMemberShortDto> members = room.getMembers().stream().map(u -> {
			RoomMemberShortDto m = new RoomMemberShortDto();
			m.setId(u.getId());
			m.setUsername(u.getUsername());
			m.setDisplayName(u.getDisplayName());
			m.setStatus(u.getStatus());
			m.setLastSeenAt(u.getLastSeenAt());
			m.setAvatarUrl(u.getAvatarUrl());
			m.setUpdatedAt(u.getUpdatedAt());
			m.setCreatedAt(u.getCreatedAt());
			return m;
		}).toList();
		dto.setMembers(members);
		return dto;
	}

	public Room getPrivateRoomIfExists(String username1, String username2) {
		User user1 = userService.getUser(username1);
		User user2 = userService.getUser(username2);
		return findPrivateRoomBetweenUsers(user1.getId(), user2.getId()).orElse(null);
	}

	public Room getPrivateRoomIfExists(String currentUsername, Long friendId) {
		User currentUser = userService.getUser(currentUsername);
		return findPrivateRoomBetweenUsers(currentUser.getId(), friendId).orElse(null);
	}

	public List<Room> getUserRooms(String username) {
		User user = userService.getUser(username);
		return roomRepository.findAllByMembersContainingAndIsActiveTrue(user);
	}

	public List<Room> getUserRooms(Long id) {
		User user = userService.getUser(id);
		return roomRepository.findAllByMembersContainingAndIsActiveTrue(user);
	}

	public void leaveRoom(String username, long roomId) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId, username);

		room.getMembers().remove(user);

		if (room.getMembers().isEmpty()) {
			room.setIsActive(false);
		}

		roomRepository.save(room);
	}

	@Transactional
	public Room updateRoom(Long roomId, String username, String newName, Long lastMessageId,
			String lastMessageContent, LocalDateTime lastActivityAt) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId, username);

		// Проверяем, что пользователь является участником комнаты
		boolean isMember =
				room.getMembers().stream().anyMatch(member -> member.getId().equals(user.getId()));
		if (!isMember) {
			throw new InsufficientPermissionsException(
					"Пользователь не является участником комнаты");
		}

		if (newName != null && !newName.isEmpty()) {
			room.setName(newName);
		}
		if (lastMessageId != null) {
			room.setLastMessageId(lastMessageId);
		}
		if (lastMessageContent != null) {
			room.setLastMessageContent(lastMessageContent);
		}
		if (lastActivityAt != null) {
			room.setLastActivityAt(lastActivityAt);
		}

		return roomRepository.save(room);
	}

	@Transactional
	public Room updateRoom(Long roomId, String username, Long lastMessageId,
			String lastMessageContent, LocalDateTime lastActivityAt) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId, username);

		// Проверяем, что пользователь является участником комнаты
		boolean isMember =
				room.getMembers().stream().anyMatch(member -> member.getId().equals(user.getId()));
		if (!isMember) {
			throw new InsufficientPermissionsException(
					"Пользователь не является участником комнаты");
		}

		room.setLastMessageId(lastMessageId);
		room.setLastMessageContent(lastMessageContent);
		room.setLastActivityAt(lastActivityAt);

		return roomRepository.save(room);
	}

	@Transactional
	public void deleteRoom(Long roomId, String username) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId, username);

		// Проверяем, что пользователь является участником комнаты
		boolean isMember =
				room.getMembers().stream().anyMatch(member -> member.getId().equals(user.getId()));
		if (!isMember) {
			throw new InsufficientPermissionsException(
					"Пользователь не является участником комнаты");
		}

		// Помечаем комнату как неактивную и очищаем участников
		room.setIsActive(false);
		room.getMembers().clear();
		roomRepository.save(room);
	}
}
