package com.zvonok.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zvonok.exception.RoomNotFoundException;
import com.zvonok.exception.UserNotMemberRoomException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.repository.RoomRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomAccessService {

	private final RoomRepository roomRepository;
	private final UserService userService;

	@Transactional(readOnly = true)
	public Room getRoomForUser(Long roomId, String username) {
		User user = userService.getUser(username);
		Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(
				HttpResponseMessage.HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));

		if (!room.getIsActive()) {
			throw new RoomNotFoundException(
					HttpResponseMessage.HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
		} ;

		boolean isMember = room.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));

		if (!isMember) {
			throw new UserNotMemberRoomException(
					HttpResponseMessage.HTTP_USER_NOT_MEMBER_ROOM_RESPONSE_MESSAGE.getMessage());
		}

		return room;
	}

	@Transactional
	public Room getRoomForUserForRoomUpdate(Long roomId, String username) {
		User user = userService.getUser(username);
		Room room = roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new RoomNotFoundException(
				HttpResponseMessage.HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));

		if (!room.getIsActive()) {
			throw new RoomNotFoundException(
					HttpResponseMessage.HTTP_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
		} ;

		boolean isMember = room.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));

		if (!isMember) {
			throw new UserNotMemberRoomException(
					HttpResponseMessage.HTTP_USER_NOT_MEMBER_ROOM_RESPONSE_MESSAGE.getMessage());
		}

		return room;
	}
}
