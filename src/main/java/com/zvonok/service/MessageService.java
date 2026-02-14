package com.zvonok.service;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.exception.CannotEditDeletedMessageException;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.MessageNotFoundException;
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
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.MessageType;
import com.zvonok.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing messages in private rooms, group rooms, and channels.
 * Сервис для управления
 * сообщениями в приватных комнатах, групповых комнатах и каналах.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MessageService {

	private final MessageRepository messageRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final RoomService roomService;
	private final UserService userService;
	private final ChannelService channelService;
	private final PermissionService permissionService;

	public MessageResponse sendPrivateMessage(String senderUsername, String receiverUsername,
			String content) {
		long durationStart = System.currentTimeMillis();
		Message message = null;
		Room privateRoom = null;

		try {
			privateRoom = roomService.createOrGetPrivateRoom(senderUsername, receiverUsername);
			User sender = userService.getUser(senderUsername);

			// Проверяем, что отправитель является участником комнаты
			boolean isMember = privateRoom.getMembers().stream()
					.anyMatch(member -> member.getId().equals(sender.getId()));
			if (!isMember) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_USER_NOT_MEMBER_PRIVATE_ROOM_MESSAGE.getMessage());
			}

			message = createMessage(sender, content, privateRoom, null);
			Message savedMessage = messageRepository.save(message);

			MessageResponse response = mapToMessageResponse(savedMessage, privateRoom.getId());
			response.setEventType(EventType.MESSAGE);

			for (User member : privateRoom.getMembers()) {
				messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages",
						response);
			}

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_PRIVATE_MESSAGE_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).roomId(privateRoom.getId())
					.messageId(message.getId()).build());
			return response;
		} catch (UserNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_PRIVATE_MESSAGE_ACTION, durationStart, privateRoom,
					message,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_PRIVATE_MESSAGE_ACTION, durationStart, privateRoom,
					message,
					false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_PRIVATE_MESSAGE_ACTION, durationStart, privateRoom,
					message,
					true);
			throw e;
		}

	}

	public MessageResponse sendGroupMessage(String senderUsername, long roomId, String content) {
		long durationStart = System.currentTimeMillis();
		Message message = null;

		try {
			Room groupRoom = roomService.getRoom(roomId, senderUsername);
			User sender = userService.getUser(senderUsername);

			// Проверяем, что отправитель является участником комнаты
			boolean isMember = groupRoom.getMembers().stream()
					.anyMatch(member -> member.getId().equals(sender.getId()));
			if (!isMember) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_USER_NOT_MEMBER_GROUP_ROOM_MESSAGE.getMessage());
			}

			message = createMessage(sender, content, groupRoom, null);
			Message savedMessage = messageRepository.save(message);

			MessageResponse response = mapToMessageResponse(savedMessage, groupRoom.getId());
			response.setEventType(EventType.MESSAGE);

			messagingTemplate.convertAndSend("/topic/room." + groupRoom.getId(), response);

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).roomId(roomId)
					.messageId(message.getId()).build());
			return response;
		} catch (RoomNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart, roomId, message,
					false);
			throw e;
		} catch (UserNotMemberRoomException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart, roomId, message,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart, roomId, message,
					false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION, durationStart, roomId, message,
					true);
			throw e;
		}

	}

	public ChannelMessageResponse sendChannelMessage(String senderUsername, Long channelId,
			String content) {
		long durationStart = System.currentTimeMillis();
		Message message = null;

		try {
			User sender = userService.getUser(senderUsername);
			Channel channel = channelService.getChannel(channelId);

			if (!permissionService.canUserSendMessages(sender.getId(), channelId)) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			message = createMessage(sender, content, null, channel);
			Message savedMessage = messageRepository.save(message);

			ChannelMessageResponse response = mapToChannelMessageResponse(savedMessage, channel);
			response.setEventType(EventType.MESSAGE);

			String topicDestination = "/topic/channel." + channelId;
			messagingTemplate.convertAndSend(topicDestination, response);

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).channelId(channelId)
					.messageId(message.getId()).build());
			return response;

		} catch (UserNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION, durationStart, message,
					channelId,
					false);
			throw e;
		} catch (ChannelNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION, durationStart, message,
					channelId,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION, durationStart, message,
					channelId,
					false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_SEND_CHANNEL_MESSAGE_ACTION, durationStart, message,
					channelId,
					true);
			throw e;
		}
	}

	@Transactional
	public MessageResponse editMessage(Long messageId, String senderUsername, String newContent) {
		long durationStart = System.currentTimeMillis();
		User sender = null;

		try {
			Message message = messageRepository.findById(messageId)
					.orElseThrow(() -> new MessageNotFoundException(String.format("%s (ID: %d)",
							HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage(),
							messageId)));

			sender = userService.getUser(senderUsername);

			// Проверяем, что пользователь является отправителем
			if (!message.getSender().getId().equals(sender.getId())) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_ONLY_SENDER_CAN_EDIT_MESSAGE.getMessage());
			}

			// Проверяем, что сообщение не удалено
			if (message.isDeleted()) {
				throw new CannotEditDeletedMessageException(
						BusinessRuleMessage.BUSINESS_CANNOT_EDIT_DELETED_MESSAGE.getMessage());
			}

			message.setContent(newContent);
			message.setEditedAt(LocalDateTime.now());
			Message savedMessage = messageRepository.save(message);

			MessageResponse response = mapToMessageResponse(savedMessage,
					savedMessage.getRoom() != null ? savedMessage.getRoom().getId() : null);
			response.setEventType(EventType.MESSAGE_EDIT);

			// Отправляем обновление через WebSocket
			if (savedMessage.getRoom() != null) {
				messagingTemplate.convertAndSend("/topic/room." + savedMessage.getRoom().getId(),
						response);
			} else if (savedMessage.getChannel() != null) {
				ChannelMessageResponse channelResponse = mapToChannelMessageResponse(savedMessage,
						savedMessage.getChannel());
				channelResponse.setEventType(EventType.MESSAGE_EDIT);
				messagingTemplate.convertAndSend("/topic/channel." + savedMessage.getChannel().getId(),
						channelResponse);
			}

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).userId(sender.getId())
					.messageId(messageId).build());
			return response;
		} catch (MessageNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart, sender, messageId,
					false);
			throw e;
		} catch (UserNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart, sender, messageId,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart, sender, messageId,
					false);
			throw e;
		} catch (CannotEditDeletedMessageException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart, sender, messageId,
					false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_UPDATE_MESSAGE_ACTION, durationStart, sender, messageId,
					true);
			throw e;
		}
	}

	@Transactional
	public void deleteMessage(Long messageId, String username) {
		long durationStart = System.currentTimeMillis();
		User user = null;

		try {
			Message message = messageRepository.findById(messageId)
					.orElseThrow(() -> new MessageNotFoundException(String.format("%s (ID: %d)",
							HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage(),
							messageId)));

			user = userService.getUser(username);

			// Проверяем, что пользователь является отправителем или имеет права
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
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			message.setDeletedAt(LocalDateTime.now());
			messageRepository.save(message);

			// Отправляем событие удаления через WebSocket
			if (message.getRoom() != null) {
				MessageResponse response = mapToMessageResponse(message, message.getRoom().getId());
				response.setEventType(EventType.MESSAGE_DELETE);
				messagingTemplate.convertAndSend("/topic/room." + message.getRoom().getId(), response);
			} else if (message.getChannel() != null) {
				ChannelMessageResponse response = mapToChannelMessageResponse(message, message.getChannel());
				response.setEventType(EventType.MESSAGE_DELETE);
				messagingTemplate.convertAndSend("/topic/channel." + message.getChannel().getId(),
						response);
			}

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_DELETE_MESSAGE_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).userId(user.getId())
					.messageId(messageId).build());
		} catch (MessageNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart, user, messageId,
					false);
			throw e;
		} catch (UserNotFoundException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart, user, messageId,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart, user, messageId,
					false);
			throw e;
		} catch (Exception e) {
			buildFailedMessage(e, LogEventConstants.EVENT_DELETE_MESSAGE_ACTION, durationStart, user, messageId,
					true);
			throw e;
		}
	}

	public Message getMessage(Long messageId) {
		return messageRepository.findById(messageId)
				.orElseThrow(() -> new MessageNotFoundException(String.format("%s (ID: %d)",
						HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage(),
						messageId)));
	}

	public List<MessageResponse> getPrivateMessages(String currentUsername, Long userId) {
		userService.getUser(userId);
		Room privateRoom = roomService.getPrivateRoomIfExists(currentUsername, userId);
		if (privateRoom == null) {
			return new ArrayList<>();
		}

		return messageRepository.findByRoomIdAndDeletedAtIsNullOrderBySentAtAsc(privateRoom.getId())
				.stream().map(message -> mapToMessageResponse(message, privateRoom.getId()))
				.toList();
	}

	public void sendErrorMessage(String username, String message, HttpStatus status) {
		ChatErrorMessageResponse messageResponse = new ChatErrorMessageResponse();
		messageResponse.setMessage(message);
		messageResponse.setStatus(status.value());
		messagingTemplate.convertAndSendToUser(username, "/queue/errors", messageResponse);
	}

	// ===== PRIVATE HELPER METHODS =====

	private Message createMessage(User sender, String content, Room room, Channel channel) {
		Message message = new Message();
		message.setSender(sender);
		message.setContent(content);
		message.setType(MessageType.DEFAULT);
		message.setRoom(room);
		message.setChannel(channel);
		message.setReplyToMessage(null);
		message.setEditedAt(null);
		message.setDeletedAt(null);
		message.setSentAt(LocalDateTime.now());
		return message;
	}

	private MessageResponse mapToMessageResponse(Message message, Long roomId) {
		MessageResponse response = new MessageResponse();
		response.setId(message.getId());
		response.setContent(message.getContent());
		response.setSenderUsername(message.getSender().getUsername());
		response.setSentAt(message.getSentAt());
		response.setMessageType(message.getType());
		response.setRoomId(roomId);
		return response;
	}

	private ChannelMessageResponse mapToChannelMessageResponse(Message message, Channel channel) {
		ChannelMessageResponse response = new ChannelMessageResponse();
		response.setId(message.getId());
		response.setContent(message.getContent());
		response.setSenderUsername(message.getSender().getUsername());
		response.setSenderId(message.getSender().getId());
		response.setSentAt(message.getSentAt());
		response.setMessageType(message.getType());
		response.setChannelId(channel.getId());
		response.setChannelName(channel.getName());
		response.setServerId(channel.getFolder().getServer().getId());
		response.setIsEdited(message.isEdited());
		if (message.getReplyToMessage() != null) {
			response.setReplyToMessageId(message.getReplyToMessage().getId().toString());
		}
		return response;
	}

	private void buildFailedMessage(Exception e, String action, long durationStart, Room room, Message message,
			boolean isErrorLog) {
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

	private void buildFailedMessage(Exception e, String action, long durationStart, Long roomId, Message message,
			boolean isErrorLog) {
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

	private void buildFailedMessage(Exception e, String action, long durationStart, Message message, Long channelId,
			boolean isErrorLog) {
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

	private void buildFailedMessage(Exception e, String action, long durationStart, User user, Long messageId,
			boolean isErrorLog) {
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
