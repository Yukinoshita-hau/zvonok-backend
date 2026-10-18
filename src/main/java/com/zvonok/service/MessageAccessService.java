package com.zvonok.service;

import com.zvonok.exception.MessageNotFoundException;
import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.RoomNotFoundException;
import com.zvonok.exception.UserNotMemberRoomException;
import com.zvonok.model.Message;
import com.zvonok.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The same non-disclosing read policy for REST and read receipts. */
@Service
@RequiredArgsConstructor
public class MessageAccessService {

	private final MessageRepository messageRepository;
	private final RoomAccessService roomAccessService;

	@Transactional(readOnly = true)
	public Message getReadableMessage(Long id, String username) {
		if (username == null || username.isBlank() || id == null) {
			throw unavailable();
		}
		Message message = messageRepository.findById(id).orElseThrow(this::unavailable);
		if (message.isDeleted() || message.getRoom() == null) {
			throw unavailable();
		}
		try {
			roomAccessService.getRoomForUser(message.getRoom().getId(), username);
		} catch (RoomNotFoundException | UserNotMemberRoomException
				| UserNotFoundException e) {
			throw unavailable();
		}
		return message;
	}

	private MessageNotFoundException unavailable() {
		return new MessageNotFoundException("Message not found");
	}
}
