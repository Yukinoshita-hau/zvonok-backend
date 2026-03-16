package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.zvonok.exception.RoomReadStateNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.RoomReadState;
import com.zvonok.model.User;
import com.zvonok.repository.MessageRepository;
import com.zvonok.repository.RoomReadStateRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RoomReadStateService {

	private final RoomReadStateRepository roomReadStateRepository;
	private final MessageRepository messageRepository;
	private final UserService userService;
	private final RoomAccessService roomAccessService;

	public RoomReadState getRoomReadState(Long userId, Long roomId) {
		return roomReadStateRepository.findByUserIdAndRoomId(userId, roomId)
				.orElseThrow(() -> new RoomReadStateNotFoundException(
						HttpResponseMessage.HTTP_ROOM_READ_STATE_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	public void markRoomAsRead(String username, Long roomId) {
		User user = userService.getUser(username);
		Room room = roomAccessService.getRoomForUser(roomId, username);

		Long lastMessageId = room.getLastMessageId();

		RoomReadState state = roomReadStateRepository.findByUserIdAndRoomId(user.getId(), roomId)
				.orElseGet(() -> {
					RoomReadState s = new RoomReadState();
					s.setUser(user);
					s.setRoom(room);
					return s;
				});

		state.setLastReadMessageId(lastMessageId);
		state.setUpdatedAt(LocalDateTime.now());
		roomReadStateRepository.save(state);
	}

	public int getUnreadCount(Long userId, Long roomId) {
		return roomReadStateRepository.findByUserIdAndRoomId(userId, roomId).map(state -> {
			Long lastReadMessage = state.getLastReadMessageId();
			if (state.getLastReadMessageId() == null) {
				return messageRepository.countByRoomIdAndSenderIdNotAndDeletedAtIsNull(roomId,
						userId);
			}
			return messageRepository.countByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNull(
					roomId, lastReadMessage, userId);
		}).orElseGet(() -> {
			return messageRepository.countByRoomIdAndSenderIdNotAndDeletedAtIsNull(roomId, userId);
		});
	}

	public Map<Long, Integer> getUnreadCountsForRooms(Long userId, List<Long> roomIds) {
		List<RoomReadState> states =
				roomReadStateRepository.findAllByUserIdAndRoomIdIn(userId, roomIds);

		Map<Long, Long> lastReadByRoom =
				states.stream().collect(Collectors.toMap(s -> s.getRoom().getId(),
						s -> s.getLastReadMessageId() != null ? s.getLastReadMessageId() : 0L));

		Map<Long, Integer> result = new HashMap<>();
		for (Long roomId : roomIds) {
			Long lastRead = lastReadByRoom.getOrDefault(roomId, 0L);
			if (lastRead == 0L) {
				result.put(roomId, messageRepository
						.countByRoomIdAndSenderIdNotAndDeletedAtIsNull(roomId, userId));
			} else {
				result.put(roomId,
						messageRepository
								.countByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNull(
										roomId, lastRead, userId));
			}
		}

		return result;
	}

	public Long getFirstUnreadMessageId(Long userId, Long roomId) {
		return roomReadStateRepository.findByUserIdAndRoomId(userId, roomId).map(state -> {
			Long lastRead = state.getLastReadMessageId();
			if (lastRead == null) {
				Message first = messageRepository
						.findFirstByRoomIdAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(roomId,
								userId);
				return first != null ? first.getId() : null;
			}
			Message first = messageRepository
					.findFirstByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(
							roomId, lastRead, userId);
			return first != null ? first.getId() : null;
		}).orElse(null);

	}
}
