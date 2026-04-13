package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.zvonok.controller.dto.GetMessagesReaders;
import com.zvonok.model.Message;
import com.zvonok.model.MessageReadStatus;
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
	private final MessageService messageService;
	private final UserService userService;
	// private final MessageRepository messageRepository;

	@Transactional
	public void markMessageAsRead(Long messageId, String username) {
		Message message = messageService.getMessage(messageId);
		User user = userService.getUser(username);
		readStatusRepo.findByMessageIdAndUserId(messageId, user.getId()).orElseGet(() -> {
			MessageReadStatus readStatus = new MessageReadStatus();
			readStatus.setMessage(message);
			readStatus.setUser(user);
			readStatus.setReadAt(LocalDateTime.now());
			return readStatusRepo.save(readStatus);
		});

		Room room = message.getRoom();

		room.getMembers().stream().filter(member -> !member.getId().equals(user.getId()))
				.forEach(member -> messagingTemplate.convertAndSendToUser(member.getUsername(),
						"/queue/message-read",
						MessageReadStatusContent.builder().messageId(messageId).roomId(room.getId())
								.readBy(username).build()));
	}

	public List<String> getReaders(Long messageId) {
		return readStatusRepo.findByMessageId(messageId).stream()
				.map(status -> status.getUser().getUsername()).collect(Collectors.toList());
	}

	public List<GetMessagesReaders> getReaders(List<Long> messageIds, String username) {
		List<GetMessagesReaders> response = new ArrayList<>();
		messageIds.forEach(m -> {
			List<String> readers = getReaders(m);
			response.add(GetMessagesReaders.builder().messageId(m).readers(readers).build());
		});

		return response;
	}
}
