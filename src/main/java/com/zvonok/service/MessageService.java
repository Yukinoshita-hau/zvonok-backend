package com.zvonok.service;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.controller.dto.ReplyPreviewDto;
import com.zvonok.exception.CannotEditDeletedMessageException;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.MessageNotFoundException;
import com.zvonok.exception.MessageTargetValidationException;
import com.zvonok.exception.RoomNotFoundException;
import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.UserNotMemberRoomException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.logging.LogEvent;
import com.zvonok.logging.LogEventConstants;
import com.zvonok.logging.LogTimingUtils;
import com.zvonok.model.Channel;
import com.zvonok.service.dto.EventType;
import com.zvonok.service.dto.Permission;
import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.controller.dto.ShortMessageWrapped;
import com.zvonok.controller.dto.RoomShortDto;
import com.zvonok.controller.dto.SenderDto;
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.MessageType;
import com.zvonok.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing messages in private rooms, group rooms, and channels. Сервис для управления
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MessageService {

	private final MessageRepository messageRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final RoomService roomService;
	private final UserService userService;
	private final ChannelService channelService;
	private final PermissionService permissionService;


	public ShortMessageWrapped sendMessage(String senderUsername, long roomId, String content,
			Long replyToMessageId) {
		long durationStart = System.currentTimeMillis();
		Message message = null;

		try {
			Room room = roomService.getRoom(roomId, senderUsername);
			User sender = userService.getUser(senderUsername);

			// Проверяем, что отправитель является участником комнаты
			boolean isMember = room.getMembers().stream()
					.anyMatch(member -> member.getId().equals(sender.getId()));
			if (!isMember) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_USER_NOT_MEMBER_GROUP_ROOM_MESSAGE
								.getMessage());
			}

			validateReplyTarget(replyToMessageId, room, null);

			message = createMessage(sender, content, room, null, replyToMessageId);
			Message savedMessage = messageRepository.save(message);

			// MessageResponse response = mapToMessageResponse(savedMessage, room.getId());
			ShortMessageWrapped response = toWrappedShortMessage(savedMessage, EventType.MESSAGE);

			// messagingTemplate.convertAndSend("/topic/room." + room.getId(), response);
			for (User member : room.getMembers()) {
				messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages",
						response);
			}

			roomService.updateRoom(room.getId(), senderUsername, room.getName(), message.getId(),
					message.getContent(), message.getSentAt());

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.roomId(roomId).messageId(message.getId()).build());
			return response;
		} catch (RoomNotFoundException | UserNotMemberRoomException
				| InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart,
					roomId, message, false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart,
					roomId, message, true);
			throw e;
		}

	}

	public ChannelMessageResponse sendChannelMessage(String senderUsername, Long channelId,
			String content, Long replyToMessageId) {
		long durationStart = System.currentTimeMillis();
		Message message = null;

		try {
			User sender = userService.getUser(senderUsername);
			Channel channel = channelService.getChannelByIdInternal(channelId);

			if (!permissionService.canUserSendMessages(sender.getId(), channelId)) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			validateReplyTarget(replyToMessageId, null, channel);

			message = createMessage(sender, content, null, channel, replyToMessageId);
			Message savedMessage = messageRepository.save(message);

			ChannelMessageResponse response =
					mapToChannelMessageResponse(savedMessage, channel, EventType.MESSAGE);


			String topicDestination = "/topic/channel." + channelId;
			messagingTemplate.convertAndSend(topicDestination, response);

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.channelId(channelId).messageId(message.getId()).build());
			return response;

		} catch (UserNotFoundException | ChannelNotFoundException
				| InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION,
					durationStart, message, channelId, false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION,
					durationStart, message, channelId, true);
			throw e;
		}
	}

	@Transactional
	public ShortMessageWrapped editMessage(Long messageId, String senderUsername,
			String newContent) {
		long durationStart = System.currentTimeMillis();
		User sender = null;

		try {
			Message message = messageRepository.findById(messageId)
					.orElseThrow(() -> new MessageNotFoundException(String.format("%s (ID: %d)",
							HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE
									.getMessage(),
							messageId)));

			sender = userService.getUser(senderUsername);

			if (!message.getSender().getId().equals(sender.getId())) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_ONLY_SENDER_CAN_EDIT_MESSAGE.getMessage());
			}

			if (message.isDeleted()) {
				throw new CannotEditDeletedMessageException(
						BusinessRuleMessage.BUSINESS_CANNOT_EDIT_DELETED_MESSAGE.getMessage());
			}

			message.setContent(newContent);
			message.setEditedAt(LocalDateTime.now());
			Message savedMessage = messageRepository.save(message);

			ShortMessageWrapped response =
					toWrappedShortMessage(savedMessage, EventType.MESSAGE_EDIT);

			roomService.updateRoom(message.getRoom().getId(), senderUsername, null, message.getId(),
					message.getContent(), message.getSentAt());

			// Отправляем обновление через WebSocket
			if (savedMessage.getRoom() != null) {
				for (User member : savedMessage.getRoom().getMembers()) {
					messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages",
							response);
				}
			} else if (savedMessage.getChannel() != null) {
				ChannelMessageResponse channelResponse = mapToChannelMessageResponse(savedMessage,
						savedMessage.getChannel(), EventType.MESSAGE);
				channelResponse.setEventType(EventType.MESSAGE_EDIT);
				messagingTemplate.convertAndSend(
						"/topic/channel." + savedMessage.getChannel().getId(), channelResponse);
			}

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.userId(sender.getId()).messageId(messageId).build());

			return response;
		} catch (MessageNotFoundException | UserNotFoundException | InsufficientPermissionsException
				| CannotEditDeletedMessageException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart,
					sender, messageId, false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart,
					sender, messageId, true);
			throw e;
		}
	}

	@Transactional
	public void deleteMessage(Long messageId, String username) {
		long durationStart = System.currentTimeMillis();
		User user = null;

		try {
			Message message = messageRepository.findById(messageId)
					.orElseThrow(() -> new MessageNotFoundException(
							HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE
									.getMessage()));

			if (message.isDeleted()) {
				throw new MessageNotFoundException(
						HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
			}
			user = userService.getUser(username);


			// администратора
			boolean isSender = message.getSender().getId().equals(user.getId());
			boolean isAdmin = false;

			if (message.getChannel() != null) {
				isAdmin = permissionService.hasPermissionInServer(user.getId(),
						message.getChannel().getFolder().getServer().getId(),
						Permission.ADMINISTRATOR);
			}

			if (!isSender && !isAdmin) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_MESSAGE_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}


			message.setDeletedAt(LocalDateTime.now());
			messageRepository.save(message);

			if (message.getRoom() != null) {
				List<ShortMessageWrapped> RoomMessages =
						getRoomMessages(username, message.getRoom().getId(), null, 1);

				ShortMessageWrapped lastRoomMessage =
						RoomMessages.size() == 1 ? RoomMessages.getFirst() : null;

				if (lastRoomMessage != null) {
					roomService.updateRoom(message.getRoom().getId(), username, null,
							lastRoomMessage.getId(), lastRoomMessage.getContent(),
							lastRoomMessage.getSentAt());
				} else {
					roomService.updateRoom(message.getRoom().getId(), username, null, null, null);
				}
			}

			// Отправляем событие удаления через WebSocket
			if (message.getRoom() != null) {
				ShortMessageWrapped response =
						toWrappedShortMessage(message, EventType.MESSAGE_DELETE);


				for (User member : message.getRoom().getMembers()) {
					messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages",
							response);
				}
			} else if (message.getChannel() != null) {
				ChannelMessageResponse response = mapToChannelMessageResponse(message,
						message.getChannel(), EventType.MESSAGE);
				response.setEventType(EventType.MESSAGE_DELETE);

				messagingTemplate.convertAndSend("/topic/channel." + message.getChannel().getId(),
						response);
			}

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_DELETE_MESSAGE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.userId(user.getId()).messageId(messageId).build());
		} catch (MessageNotFoundException | UserNotFoundException
				| InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart,
					user, messageId, false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart,
					user, messageId, true);
			throw e;
		}
	}

	public Message getMessage(Long messageId) {
		return messageRepository.findById(messageId).orElseThrow(() -> new MessageNotFoundException(
				HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public List<ShortMessageWrapped> getRoomMessages(String currentUsername, Long roomId,
			Long beforeMessageId, int limit) {
		roomService.getRoom(roomId, currentUsername);

		PageRequest pageRequest = PageRequest.of(0, limit);

		org.springframework.data.domain.Page<Message> page;

		if (beforeMessageId == null) {
			page = messageRepository.findByRoomIdAndDeletedAtIsNullOrderBySentAtDesc(roomId,
					pageRequest);
		} else {
			Message beforeMessage = getMessage(beforeMessageId);
			page = messageRepository.findByRoomIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
					roomId, beforeMessage.getId(), pageRequest);
		}

		Map<Long, ReplyPreviewDto> replyPreviewByParentId =
				buildReplyPreviewByParentId(page.getContent());

		return page.getContent().stream().sorted(java.util.Comparator.comparing(Message::getId))
				.map(msg -> {
					Long replyToMessageId = msg.getReplyToMessageId();
					ReplyPreviewDto replyPreview = replyToMessageId == null ? null
							: replyPreviewByParentId.get(replyToMessageId);

					return toWrappedShortMessage(msg, EventType.MESSAGE, replyPreview);
				}).toList();

	}

	public List<MessageResponse> getPrivateMessages(String currentUsername, Long userId) {
		userService.getUser(userId);
		Room privateRoom = roomService.getPrivateRoomIfExists(currentUsername, userId);
		if (privateRoom == null) {
			return new ArrayList<>();
		}

		List<Message> messages = messageRepository
				.findByRoomIdAndDeletedAtIsNullOrderBySentAtAsc(privateRoom.getId());
		Map<Long, ReplyPreviewDto> replyPreviewByParentId = buildReplyPreviewByParentId(messages);

		return messages.stream().map(message -> {
			Long replyToMessageId = message.getReplyToMessageId();
			ReplyPreviewDto replyPreview =
					replyToMessageId == null ? null : replyPreviewByParentId.get(replyToMessageId);

			return mapToMessageResponse(message, privateRoom.getId(), replyPreview);
		}).toList();
	}

	public void sendErrorMessage(String username, String message, HttpStatus status) {
		ChatErrorMessageResponse messageResponse = new ChatErrorMessageResponse();
		messageResponse.setMessage(message);
		messageResponse.setStatus(status.value());
		messagingTemplate.convertAndSendToUser(username, "/queue/errors", messageResponse);
	}

	public ShortMessageWrapped getShortMessageWrapped(Long id) {
		Message message = getMessage(id);
		return toWrappedShortMessage(message, EventType.MESSAGE);
	}

	// ===== PRIVATE HELPER METHODS =====

	private Message createMessage(User sender, String content, Room room, Channel channel,
			Long replyToMessageId) {
		Message message = new Message();
		message.setSender(sender);
		message.setContent(content);
		message.setType(MessageType.DEFAULT);
		message.setRoom(room);
		message.setChannel(channel);
		message.setReplyToMessageId(replyToMessageId);
		message.setEditedAt(null);
		message.setDeletedAt(null);
		message.setSentAt(LocalDateTime.now());
		return message;
	}

	private ShortMessageWrapped toWrappedShortMessage(Message message, EventType eventType) {
		ReplyPreviewDto replyPreview = resolveReplyPreview(message.getReplyToMessageId());
		return toWrappedShortMessage(message, eventType, replyPreview);
	}

	private ShortMessageWrapped toWrappedShortMessage(Message message, EventType eventType,
			ReplyPreviewDto replyPreview) {
		SenderDto sender = new SenderDto(message.getSender().getId(),
				message.getSender().getUsername(), message.getSender().getDisplayName(),
				message.getSender().getAvatarUrl(), message.getSender().getStatus());
		RoomShortDto room =
				new RoomShortDto(message.getRoom().getId(), message.getRoom().getType());
		return new ShortMessageWrapped(message.getId(), message.getContent(), message.getType(),
				eventType, message.getSentAt(), sender, room, message.getEditedAt(),
				message.getReplyToMessageId(), replyPreview);
	}

	private MessageResponse mapToMessageResponse(Message message, Long roomId,
			ReplyPreviewDto replyPreview) {
		MessageResponse response = new MessageResponse();
		response.setId(message.getId());
		response.setContent(message.getContent());
		response.setSenderUsername(message.getSender().getUsername());
		response.setSentAt(message.getSentAt());
		response.setMessageType(message.getType());
		response.setRoomId(roomId);
		response.setReplyToMessageId(message.getReplyToMessageId());
		response.setReplyPreview(replyPreview);
		return response;
	}

	private ChannelMessageResponse mapToChannelMessageResponse(Message message, Channel channel,
			EventType eventType) {
		SenderDto sender = new SenderDto(message.getSender().getId(),
				message.getSender().getUsername(), message.getSender().getDisplayName(),
				message.getSender().getAvatarUrl(), message.getSender().getStatus());

		ChannelMessageResponse response = new ChannelMessageResponse();
		response.setId(message.getId());
		response.setContent(message.getContent());
		response.setSender(sender);
		response.setSentAt(message.getSentAt());
		response.setType(message.getType());
		response.setChannelId(channel.getId());
		response.setEventType(eventType);
		response.setReplyToMessageId(message.getReplyToMessageId());
		response.setReplyPreview(resolveReplyPreview(message.getReplyToMessageId()));
		response.setEditedAt(message.getEditedAt());
		return response;
	}

	private void validateReplyTarget(Long replyToMessageId, Room room, Channel channel) {
		if (replyToMessageId == null) {
			return;
		}

		Message parent = getMessage(replyToMessageId);
		if (parent.isDeleted()) {
			throw new MessageTargetValidationException("Cannot reply to a deleted message");
		}

		if (room != null) {
			if (parent.getRoom() == null || !room.getId().equals(parent.getRoom().getId())) {
				throw new MessageTargetValidationException(
						"Reply target message must belong to the same room");
			}
			return;
		}

		if (channel != null && (parent.getChannel() == null
				|| !channel.getId().equals(parent.getChannel().getId()))) {
			throw new MessageTargetValidationException(
					"Reply target message must belong to the same channel");
		}
	}

	private Map<Long, ReplyPreviewDto> buildReplyPreviewByParentId(List<Message> messages) {
		Set<Long> replyToIds = new HashSet<>();
		for (Message message : messages) {
			if (message.getReplyToMessageId() != null) {
				replyToIds.add(message.getReplyToMessageId());
			}
		}

		if (replyToIds.isEmpty()) {
			return new HashMap<>();
		}

		Map<Long, Message> parentById = new HashMap<>();
		for (Message parent : messageRepository.findAllById(replyToIds)) {
			parentById.put(parent.getId(), parent);
		}

		Map<Long, ReplyPreviewDto> previewByParentId = new HashMap<>();
		for (Long replyToId : replyToIds) {
			Message parent = parentById.get(replyToId);
			if (parent == null) {
				previewByParentId.put(replyToId, buildMissingReplyPreview(replyToId));
			} else {
				previewByParentId.put(replyToId, buildReplyPreview(parent));
			}
		}

		return previewByParentId;
	}

	private ReplyPreviewDto resolveReplyPreview(Long replyToMessageId) {
		if (replyToMessageId == null) {
			return null;
		}

		return messageRepository.findById(replyToMessageId).map(this::buildReplyPreview)
				.orElseGet(() -> buildMissingReplyPreview(replyToMessageId));
	}

	private ReplyPreviewDto buildReplyPreview(Message parent) {
		boolean deleted = parent.isDeleted();
		String snippet = deleted ? null : buildSnippet(parent.getContent());

		return new ReplyPreviewDto(parent.getId(), parent.getSender().getId(),
				parent.getSender().getUsername(), parent.getSender().getDisplayName(), snippet,
				parent.getType(), deleted);
	}

	private ReplyPreviewDto buildMissingReplyPreview(Long replyToMessageId) {
		return new ReplyPreviewDto(replyToMessageId, null, null, null, null, null, true);
	}

	private String buildSnippet(String content) {
		if (content == null) {
			return null;
		}

		String trimmed = content.trim();
		int maxLength = 120;
		if (trimmed.length() <= maxLength) {
			return trimmed;
		}

		return trimmed.substring(0, maxLength) + "...";
	}

	private void buildFailedMessage(Exception e, String action, long durationStart, Room room,
			Message message, boolean isErrorLog) {
		LogEvent.LogEventBuilder logBuilder = LogEvent.buildFailedEvent(action, e.getMessage(),
				LogTimingUtils.calculateDurationDifference(durationStart));
		if (room != null) {
			logBuilder.roomId(room.getId());
		}

		if (message != null) {
			logBuilder.messageId(message.getId());
		}

		if (isErrorLog) {
			log.error("{}", logBuilder.build());
		} else {
			log.warn("{}", logBuilder.build());
		}
	}

	private void buildFailedMessage(Exception e, String action, long durationStart, Long roomId,
			Message message, boolean isErrorLog) {
		LogEvent.LogEventBuilder logBuilder = LogEvent.buildFailedEvent(action, e.getMessage(),
				LogTimingUtils.calculateDurationDifference(durationStart));
		logBuilder.roomId(roomId);

		if (message != null) {
			logBuilder.messageId(message.getId());
		}

		if (isErrorLog) {
			log.error("{}", logBuilder.build());
		} else {
			log.warn("{}", logBuilder.build());
		}
	}

	private void buildFailedMessage(Exception e, String action, long durationStart, Message message,
			Long channelId, boolean isErrorLog) {
		LogEvent.LogEventBuilder logBuilder = LogEvent.buildFailedEvent(action, e.getMessage(),
				LogTimingUtils.calculateDurationDifference(durationStart));
		logBuilder.channelId(channelId);

		if (message != null) {
			logBuilder.messageId(message.getId());
		}

		if (isErrorLog) {
			log.error("{}", logBuilder.build());
		} else {
			log.warn("{}", logBuilder.build());
		}
	}

	private void buildFailedMessage(Exception e, String action, long durationStart, User user,
			Long messageId, boolean isErrorLog) {
		LogEvent.LogEventBuilder logBuilder = LogEvent.buildFailedEvent(action, e.getMessage(),
				LogTimingUtils.calculateDurationDifference(durationStart));
		logBuilder.messageId(messageId);

		if (user != null) {
			logBuilder.userId(user.getId());
		}

		if (isErrorLog) {
			log.error("{}", logBuilder.build());
		} else {
			log.warn("{}", logBuilder.build());
		}
	}
}
