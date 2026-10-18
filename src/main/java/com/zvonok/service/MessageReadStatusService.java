package com.zvonok.service;

import com.zvonok.utils.TransactionUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.zvonok.controller.dto.GetMessagesReaders;
import com.zvonok.exception.InvalidMessageBatchException;
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.repository.MessageReadStatusRepository;
import com.zvonok.service.dto.MessageReadStatusContent;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class MessageReadStatusService {

	private final MessageReadStatusRepository readStatusRepo;
	private final SimpMessagingTemplate messagingTemplate;
	private final MessageAccessService messageAccessService;
	private final UserService userService;

	@Transactional
	public void markMessageAsRead(Long messageId, String username) {
		Message message = messageAccessService.getReadableMessage(messageId, username);
		User user = userService.getUser(username);
	
		if (message.getSender().getId().equals(user.getId())) {
			return;
		}

		int inserted = readStatusRepo.insertIngnore(messageId, user.getId(), LocalDateTime.now());

		if (inserted == 0) {
			return;
		}

        Room room = message.getRoom();
        MessageReadStatusContent event = MessageReadStatusContent.builder().messageId(messageId)
                .roomId(room.getId()).readBy(username).build();
        for (User member : room.getMembers()) {
            String recipient = member.getUsername();
            TransactionUtils.runAfterCommit(() -> messagingTemplate.convertAndSendToUser(recipient, "/queue/message-read", event));
        }
	}

	@Transactional
	public void markMessagesAsRead(List<Long> messageIds, String username) {
		validateBatch(messageIds);
		// Authorize the entire batch before the first write or external notification.
		List<Message> messages = messageIds.stream()
				.map(id -> messageAccessService.getReadableMessage(id, username)).toList();
		messages.forEach(message -> markMessageAsRead(message.getId(), username));
	}

	private void validateBatch(List<Long> ids) {
		if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)) {
			throw new InvalidMessageBatchException("Expected 1 to 100 message IDs");
		}
	}

	private List<String> getReaders(Long messageId) {
		return readStatusRepo.findAllByMessageId(messageId).stream()
				.map(status -> status.getUser().getUsername()).collect(Collectors.toList());
	}

	@Transactional
	public List<GetMessagesReaders> getReaders(List<Long> messageIds, String username) {
		validateBatch(messageIds);
		messageIds.forEach(id -> messageAccessService.getReadableMessage(id, username));
		List<GetMessagesReaders> response = new ArrayList<>();
		messageIds.forEach(m -> {
			List<String> readers = getReaders(m);
			response.add(GetMessagesReaders.builder().messageId(m).readers(readers).build());
		});

		return response;
	}
}
