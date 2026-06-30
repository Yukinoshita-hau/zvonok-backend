package com.zvonok.service;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.controller.dto.MessageAttachmentDto;
import com.zvonok.controller.dto.ReplyPreviewDto;
import com.zvonok.exception.CannotEditDeletedMessageException;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.InvalidMessageAttachmentException;
import com.zvonok.exception.MessageAttachmentNotFoundException;
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
import com.zvonok.model.MessageAttachment;
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
import com.zvonok.model.enumeration.AttachmentType;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.dto.RoomEvents;
import com.zvonok.service.enums.BrokerPath;
import com.zvonok.service.enums.RoomEventsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
	private final MessageAttachmentRepository messageAttachmentRepository;
	private final S3Service s3Service;
	private final RoomReadStateService roomReadStateService;

	private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
	private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L;
	private static final long MAX_VIDEO_SIZE_BYTES = 100L * 1024L * 1024L;
	private static final long MAX_AUDIO_SIZE_BYTES = 25L * 1024L * 1024L;
	private static final long MAX_VIDEO_NOTE_SIZE_BYTES = 100L * 1024L * 1024L;
	private static final long MAX_AUDIO_DURATION_MS = 5L * 60L * 1000L;
	private static final long MAX_VIDEO_NOTE_DURATION_MS = 60L * 1000L;
	private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES =
			Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
	private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES =
			Set.of("video/mp4", "video/webm", "video/quicktime");
	private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES =
			Set.of("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav");
	private static final Set<String> ALLOWED_VIDEO_NOTE_CONTENT_TYPES =
			Set.of("video/webm", "video/mp4", "video/quicktime");


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

	public ShortMessageWrapped sendMessageWithAttachments(String senderUsername, long roomId,
			String content, List<MultipartFile> files, AttachmentType attachmentType,
			Long durationMs, String waveform, Long replyToMessageId) {
		long durationStart = System.currentTimeMillis();
		Message message = null;

		try {
			Room room = roomService.getRoom(roomId, senderUsername);
			User sender = userService.getUser(senderUsername);
			boolean isMember = room.getMembers().stream()
					.anyMatch(member -> member.getId().equals(sender.getId()));
			if (!isMember) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_USER_NOT_MEMBER_GROUP_ROOM_MESSAGE
								.getMessage());
			}

			List<MultipartFile> normalizedFiles = normalizeFiles(files);
			if ((content == null || content.trim().isEmpty()) && normalizedFiles.isEmpty()) {
				throw new InvalidMessageAttachmentException(
						HttpResponseMessage.HTTP_MESSAGE_EMPTY_RESPONSE_MESSAGE.getMessage());
			}
			validateReplyTarget(replyToMessageId, room, null);

			message = createMessage(sender, content == null ? "" : content, room, null,
					replyToMessageId);
			Message savedMessage = messageRepository.save(message);
			for (MultipartFile file : normalizedFiles) {
				MessageAttachment attachment = createAttachment(savedMessage, file, attachmentType,
						durationMs, waveform);
				savedMessage.getAttachments().add(attachment);
			}
			savedMessage = messageRepository.save(savedMessage);

			ShortMessageWrapped response = toWrappedShortMessage(savedMessage, EventType.MESSAGE);
			for (User member : room.getMembers()) {
				messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages",
						response);
			}

			String lastContent = content == null || content.trim().isEmpty()
					? buildAttachmentLastMessageText(savedMessage)
					: content;
			roomService.updateRoom(room.getId(), senderUsername, room.getName(),
					savedMessage.getId(), lastContent, savedMessage.getSentAt());

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_SEND_GROUP_MESSAGE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.roomId(roomId).messageId(savedMessage.getId()).build());
			return response;
		} catch (RoomNotFoundException | UserNotMemberRoomException
				| InsufficientPermissionsException | InvalidMessageAttachmentException e) {
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

	@Transactional
	public void clearRoomMessages(Long roomId, String username) {
		User user = userService.getUser(username);
		Room room = roomService.getRoom(roomId, username);
		if (room.getType() == com.zvonok.model.enumeration.RoomType.PRIVATE) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_ROOM_MESSAGES_CLEAR_PRIVATE_DENIED_RESPONSE_MESSAGE
							.getMessage());
		}
		LocalDateTime clearedAt = LocalDateTime.now();
		messageRepository.softDeleteAllByRoomId(roomId, clearedAt);
		roomReadStateService.resetRoomReadStates(roomId);
		roomService.updateRoom(roomId, username, null, null, null);

		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "ROOM_MESSAGES_CLEARED");
		payload.put("roomId", roomId);
		payload.put("clearedByUserId", user.getId());
		payload.put("clearedAt", clearedAt);
		RoomEvents event = RoomEvents.builder()
				.type(RoomEventsType.ROOM_MESSAGES_CLEARED)
				.payload(payload)
				.build();
		for (User member : room.getMembers()) {
			messagingTemplate.convertAndSendToUser(member.getUsername(),
					BrokerPath.ROOM_EVENTS_QUEUE_PATH.getPath(), event);
		}
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

	@Transactional(readOnly = true)
	public MessageAttachment getAttachmentForDownload(Long attachmentId, String username) {
		MessageAttachment attachment = messageAttachmentRepository.findById(attachmentId)
				.orElseThrow(() -> new MessageAttachmentNotFoundException(
						HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
		Message message = attachment.getMessage();
		if (message.getRoom() == null) {
			throw new MessageAttachmentNotFoundException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_NOT_FOUND_RESPONSE_MESSAGE
							.getMessage());
		}
		roomService.getRoom(message.getRoom().getId(), username);
		return attachment;
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

	private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
		if (files == null) {
			return List.of();
		}
		List<MultipartFile> normalized = files.stream()
				.filter(file -> file != null && !file.isEmpty())
				.toList();
		if (normalized.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_TOO_MANY_RESPONSE_MESSAGE
							.getMessage());
		}
		return normalized;
	}

	private MessageAttachment createAttachment(Message message, MultipartFile file,
			AttachmentType requestedType, Long durationMs, String waveform) {
		AttachmentType type = resolveAttachmentType(file, requestedType);
		validateAttachmentSize(file, type);
		validateAttachmentDuration(file, type, durationMs);
		String storageKey = buildAttachmentStorageKey(message.getId(), file.getOriginalFilename());
		try {
			s3Service.uploadFile(storageKey, file.getInputStream(), file.getSize(),
					file.getContentType());
		} catch (IOException e) {
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_UPLOAD_FAILED_RESPONSE_MESSAGE
							.getMessage());
		}

		MessageAttachment attachment = new MessageAttachment();
		attachment.setMessage(message);
		attachment.setType(type);
		attachment.setStorageKey(storageKey);
		attachment.setOriginalFileName(resolveOriginalFileName(file));
		attachment.setContentType(file.getContentType());
		attachment.setSizeBytes(file.getSize());
		attachment.setDurationMs(durationMs);
		attachment.setWaveformJson(waveform);
		if (type == AttachmentType.IMAGE) {
			applyImageDimensions(attachment, file);
		}
		return attachment;
	}

	private AttachmentType resolveAttachmentType(MultipartFile file, AttachmentType requestedType) {
		String contentType = file.getContentType();
		if (contentType == null || contentType.isBlank()) {
			logAttachmentValidationFailure("missing_content_type", file, requestedType, null);
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_TYPE_INVALID_RESPONSE_MESSAGE
							.getMessage());
		}
		String normalized = normalizeContentType(contentType);
		if (requestedType != null) {
			validateRequestedAttachmentType(file, requestedType, normalized);
			return requestedType;
		}
		if (ALLOWED_IMAGE_CONTENT_TYPES.contains(normalized)) {
			return AttachmentType.IMAGE;
		}
		if (ALLOWED_VIDEO_CONTENT_TYPES.contains(normalized)) {
			return AttachmentType.VIDEO;
		}
		logAttachmentValidationFailure("unsupported_content_type", file, requestedType, null);
		throw new InvalidMessageAttachmentException(
				HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_TYPE_INVALID_RESPONSE_MESSAGE
						.getMessage());
	}

	private String normalizeContentType(String contentType) {
		return contentType.toLowerCase().split(";", 2)[0].trim();
	}

	private void validateRequestedAttachmentType(MultipartFile file, AttachmentType requestedType,
			String contentType) {
		boolean valid = switch (requestedType) {
			case IMAGE -> ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType);
			case VIDEO -> ALLOWED_VIDEO_CONTENT_TYPES.contains(contentType);
			case AUDIO -> ALLOWED_AUDIO_CONTENT_TYPES.contains(contentType);
			case VIDEO_NOTE -> ALLOWED_VIDEO_NOTE_CONTENT_TYPES.contains(contentType);
		};
		if (!valid) {
			logAttachmentValidationFailure("requested_type_content_type_mismatch", file,
					requestedType, null);
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_TYPE_INVALID_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void validateAttachmentSize(MultipartFile file, AttachmentType type) {
		long maxSize = switch (type) {
			case IMAGE -> MAX_IMAGE_SIZE_BYTES;
			case VIDEO -> MAX_VIDEO_SIZE_BYTES;
			case AUDIO -> MAX_AUDIO_SIZE_BYTES;
			case VIDEO_NOTE -> MAX_VIDEO_NOTE_SIZE_BYTES;
		};
		if (file.getSize() > maxSize) {
			logAttachmentValidationFailure("file_too_large", file, type, null);
			throw new InvalidMessageAttachmentException(attachmentSizeErrorMessage(type));
		}
	}

	private String attachmentSizeErrorMessage(AttachmentType type) {
		return switch (type) {
			case IMAGE -> HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_IMAGE_TOO_LARGE_RESPONSE_MESSAGE
					.getMessage();
			case VIDEO -> HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_VIDEO_TOO_LARGE_RESPONSE_MESSAGE
					.getMessage();
			case AUDIO -> HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_AUDIO_TOO_LARGE_RESPONSE_MESSAGE
					.getMessage();
			case VIDEO_NOTE -> HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_TOO_LARGE_RESPONSE_MESSAGE
					.getMessage();
		};
	}

	private void validateAttachmentDuration(MultipartFile file, AttachmentType type,
			Long durationMs) {
		if (durationMs == null) {
			return;
		}
		if (durationMs < 0) {
			logAttachmentValidationFailure("negative_duration", file, type, durationMs);
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_DURATION_INVALID_RESPONSE_MESSAGE
							.getMessage());
		}
		if (type == AttachmentType.AUDIO && durationMs > MAX_AUDIO_DURATION_MS) {
			logAttachmentValidationFailure("audio_duration_too_long", file, type, durationMs);
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_AUDIO_DURATION_TOO_LONG_RESPONSE_MESSAGE
							.getMessage());
		}
		if (type == AttachmentType.VIDEO_NOTE && durationMs > MAX_VIDEO_NOTE_DURATION_MS) {
			logAttachmentValidationFailure("video_note_duration_too_long", file, type, durationMs);
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_VIDEO_NOTE_DURATION_TOO_LONG_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void logAttachmentValidationFailure(String reason, MultipartFile file,
			AttachmentType attachmentType, Long durationMs) {
		log.warn(
				"Message attachment validation failed: reason={}, originalFileName={}, contentType={}, attachmentType={}, durationMs={}, sizeBytes={}",
				reason,
				file == null ? null : file.getOriginalFilename(),
				file == null ? null : file.getContentType(),
				attachmentType,
				durationMs,
				file == null ? null : file.getSize());
	}

	private void applyImageDimensions(MessageAttachment attachment, MultipartFile file) {
		try {
			BufferedImage image = ImageIO.read(file.getInputStream());
			if (image != null) {
				attachment.setWidth(image.getWidth());
				attachment.setHeight(image.getHeight());
			}
		} catch (IOException ignored) {
			// Image dimensions are optional metadata; upload should not fail if unavailable.
		}
	}

	private String buildAttachmentStorageKey(Long messageId, String originalFileName) {
		String extension = "";
		if (originalFileName != null && originalFileName.contains(".")) {
			extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
		}
		return "message-attachments/" + messageId + "/" + UUID.randomUUID() + extension;
	}

	private String resolveOriginalFileName(MultipartFile file) {
		String originalFileName = file.getOriginalFilename();
		return originalFileName == null || originalFileName.isBlank()
				? "attachment"
				: originalFileName;
	}

	private String buildAttachmentLastMessageText(Message message) {
		if (message.getAttachments().stream()
				.anyMatch(attachment -> attachment.getType() == AttachmentType.AUDIO)) {
			return "[voice]";
		}
		if (message.getAttachments().stream()
				.anyMatch(attachment -> attachment.getType() == AttachmentType.VIDEO_NOTE)) {
			return "[video note]";
		}
		if (message.getAttachments().stream()
				.anyMatch(attachment -> attachment.getType() == AttachmentType.VIDEO)) {
			return "[video]";
		}
		return "[image]";
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
				message.getReplyToMessageId(), replyPreview, mapAttachments(message));
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
		response.setAttachments(mapAttachments(message));
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
		response.setAttachments(mapAttachments(message));
		return response;
	}

	private List<MessageAttachmentDto> mapAttachments(Message message) {
		if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
			return List.of();
		}
		return message.getAttachments().stream()
				.map(this::toAttachmentDto)
				.toList();
	}

	private MessageAttachmentDto toAttachmentDto(MessageAttachment attachment) {
		String downloadUrl = "/api/message-attachments/" + attachment.getId() + "/download";
		return new MessageAttachmentDto(attachment.getId(), attachment.getType(), downloadUrl,
				downloadUrl, attachment.getOriginalFileName(), attachment.getContentType(),
				attachment.getSizeBytes(), attachment.getWidth(), attachment.getHeight(),
				attachment.getDurationMs(), attachment.getWaveformJson());
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
