package com.zvonok.service;

import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.InvalidRoomSizeException;
import com.zvonok.exception.RoomNotFoundException;
import com.zvonok.exception.RoomSizeMaxTenMembersException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

	public Room getRoom(Long id) {
		return roomRepository.findById(id).orElseThrow(() -> new RoomNotFoundException(
				HttpResponseMessage.HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
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
		room.setName(null);
		room.setType(RoomType.PRIVATE);
		room.setIsActive(true);
		room.setCreatedAt(LocalDateTime.now());
		room.setMembers(members);

		return roomRepository.save(room);
	}

	public Room createGroupRoom(String creatorUsername, String roomName,
			List<String> roomMemberUsernames) {
		User creator = userService.getUser(creatorUsername);

		if (roomMemberUsernames.size() < 2) {
			throw new InvalidRoomSizeException(
					HttpResponseMessage.HTTP_INVALID_ROOM_SIZE_RESPONSE_MESSAGE.getMessage());
		}

		// Получаем пользователей по именам через UserService
		List<User> members = new ArrayList<>();
		for (String username : roomMemberUsernames) {
			members.add(userService.getUser(username));
		}

		if (!members.contains(creator)) {
			members.add(creator);
		}

		if (members.size() > 10) {
			throw new RoomSizeMaxTenMembersException(
					HttpResponseMessage.HTTP_ROOM_SIZE_MAX_TEN_MEMBERS_RESPONSE_MESSAGE
							.getMessage());
		}

		Room room = new Room();
		room.setName(roomName);
		room.setType(RoomType.GROUP);
		room.setIsActive(true);
		room.setCreatedAt(LocalDateTime.now());
		room.setMembers(members);

		return roomRepository.save(room);
	}

	private Optional<Room> findPrivateRoomBetweenUsers(Long user1, Long user2) {
		return roomRepository.findPrivateRoomBetweenUsers(user1, user2);
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

	public void leaveRoom(String username, long roomId) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId);

		room.getMembers().remove(user);

		if (room.getMembers().isEmpty()) {
			room.setIsActive(false);
		}

		roomRepository.save(room);
	}

	@Transactional
	public Room updateRoom(Long roomId, String username, String newName) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId);

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

		return roomRepository.save(room);
	}

	@Transactional
	public void deleteRoom(Long roomId, String username) {
		User user = userService.getUser(username);
		Room room = getRoom(roomId);

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
