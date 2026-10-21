package com.zvonok.service;

import com.zvonok.utils.TransactionUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.controller.dto.MessageAttachmentDto;
import com.zvonok.controller.dto.ReplyPreviewDto;
import com.zvonok.exception.CannotEditDeletedMessageException;
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
import com.zvonok.model.MessageAttachment;
import com.zvonok.service.dto.EventType;
import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.controller.dto.ShortMessageWrapped;
import com.zvonok.controller.dto.RoomAttachmentPage;
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
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.EnumSet;

/**
 * Service for managing messages in private and group rooms. Сервис для управления
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MessageService {

	private final MessageRepository messageRepository;
	private final MessageAccessService messageAccessService;
	private final SimpMessagingTemplate messagingTemplate;
	private final RoomService roomService;
	private final UserService userService;
	private final MessageAttachmentRepository messageAttachmentRepository;
	private final S3Service s3Service;
	private final RoomReadStateService roomReadStateService;
	private final PlatformTransactionManager transactionManager;

	private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
	private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L;
	private static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L;
	private static final long MAX_AUDIO_SIZE_BYTES = 25L * 1024L * 1024L;
	private static final long MAX_VIDEO_NOTE_SIZE_BYTES = 50L * 1024L * 1024L;
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
			Room room = roomService.getRoomForUpdate(roomId, senderUsername);
			User sender = userService.getUser(senderUsername);

			// Проверяем, что отправитель является участником комнаты
			boolean isMember = room.getMembers().stream()
					.anyMatch(member -> member.getId().equals(sender.getId()));
			if (!isMember) {
				throw new InsufficientPermissionsException(
						BusinessRuleMessage.BUSINESS_USER_NOT_MEMBER_GROUP_ROOM_MESSAGE
								.getMessage());
			}

			validateReplyTarget(replyToMessageId, room);

			message = createMessage(sender, content, room, replyToMessageId);
			Message savedMessage = messageRepository.save(message);

			// MessageResponse response = mapToMessageResponse(savedMessage, room.getId());
			ShortMessageWrapped response = toWrappedShortMessage(savedMessage, EventType.MESSAGE);

			// publish("/topic/room." + room.getId(), response);
			for (User member : room.getMembers()) {
				publishToUser(member.getUsername(), "/queue/messages",
						response);
			}

			roomService.updateRoom(room.getId(), senderUsername, room.getName(), message.getId(),
					previewContent(message), message.getSentAt());

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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShortMessageWrapped sendMessageWithAttachments(String senderUsername, long roomId,
            String content, List<MultipartFile> files, AttachmentType attachmentType,
            Long durationMs, String waveform, Long replyToMessageId) {
        return sendMessageWithAttachments(senderUsername, roomId, content, files, attachmentType,
                durationMs, waveform, replyToMessageId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShortMessageWrapped sendMessageWithAttachments(String senderUsername, long roomId,
            String content, List<MultipartFile> files, AttachmentType attachmentType,
            Long durationMs, String waveform, Long replyToMessageId, String clientRequestId) {
        long preflightStart = System.nanoTime();
        List<MultipartFile> normalized = normalizeFiles(files);
        if ((content == null || content.isBlank()) && normalized.isEmpty()) {
            throw new InvalidMessageAttachmentException(HttpResponseMessage.HTTP_MESSAGE_EMPTY_RESPONSE_MESSAGE.getMessage());
        }
        String requestKey = normalizeUploadRequestKey(clientRequestId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Room room = roomService.getRoom(roomId, senderUsername);
            validateReplyTarget(replyToMessageId, room);
        });
        for (MultipartFile file : normalized) {
            AttachmentType type = resolveAttachmentType(file, attachmentType);
            validateAttachmentSize(file, type);
            validateAttachmentDuration(file, type, durationMs);
        }
        if (normalized.stream().mapToLong(MultipartFile::getSize).sum() > MAX_VIDEO_SIZE_BYTES)
            throw new InvalidMessageAttachmentException("Суммарный размер вложений больше 50 MiB");
        uploadTiming("authorization_policy", preflightStart);
        long fingerprintStart = System.nanoTime();
        String requestHash = requestKey == null ? null : attachmentRequestHash(roomId, content, normalized,
                attachmentType, durationMs, waveform, replyToMessageId);
        uploadTiming("fingerprint", fingerprintStart);
        ShortMessageWrapped previous = tx.execute(status -> findPreviousUpload(senderUsername, roomId, requestKey, requestHash));
        if (previous != null) return previous;
        List<String> createdKeys = new ArrayList<>();
        boolean[] completionOwnsCleanup = {false};
        try {
            List<MessageAttachment> attachments = new ArrayList<>();
            for (MultipartFile file : normalized) {
                attachments.add(createAttachment(file, attachmentType, durationMs, waveform, createdKeys));
            }
            long commitStart = System.nanoTime();
            ShortMessageWrapped committed = tx.execute(status -> {
                // Reauthorize after network I/O and serialize with send/edit/delete/clear.
                Room room = roomService.getRoomForUpdate(roomId, senderUsername);
                ShortMessageWrapped duplicate = findPreviousUpload(senderUsername, roomId, requestKey, requestHash);
                if (duplicate != null) {
                    // These newly staged keys have never been linked to a row; every outcome may clean them.
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int result) { cleanupUploads(createdKeys); }
                    });
                    completionOwnsCleanup[0] = true;
                    return duplicate;
                }
                validateReplyTarget(replyToMessageId, room);
                Message message = createMessage(userService.getUser(senderUsername), content == null ? "" : content,
                        room, replyToMessageId);
                message.setUploadRequestKey(requestKey);
                message.setUploadRequestHash(requestHash);
                for (MessageAttachment attachment : attachments) {
                    attachment.setMessage(message);
                    message.getAttachments().add(attachment);
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int result) {
                        if (result == STATUS_ROLLED_BACK) cleanupUploads(createdKeys);
                        // UNKNOWN retains objects: commit might have succeeded.
                    }
                });
                completionOwnsCleanup[0] = true;
                Message saved = messageRepository.save(message);
                ShortMessageWrapped response = toWrappedShortMessage(saved, EventType.MESSAGE);
                for (User member : room.getMembers()) publishToUser(member.getUsername(), "/queue/messages", response);
                roomService.updateRoom(roomId, senderUsername, room.getName(), saved.getId(), previewContent(saved), saved.getSentAt());
                return response;
            });
            uploadTiming("db_commit_and_publish", commitStart);
            return committed;
        } catch (RuntimeException e) {
            if (!completionOwnsCleanup[0]) cleanupUploads(createdKeys);
            throw e;
        }
    }

    private void uploadTiming(String stage, long start) {
        String trace = MDC.get("attachmentUpload");
        if (trace != null) log.info("attachment_upload trace={} stage={} duration_ms={}", trace, stage, (System.nanoTime() - start) / 1_000_000);
    }

	@Transactional
	public ShortMessageWrapped editMessage(Long messageId, String senderUsername,
			String newContent) {
		long durationStart = System.currentTimeMillis();
		User sender = null;

		try {
			lockMessageRoom(messageId, senderUsername);
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

			if (message.getRoom() != null && message.getId().equals(message.getRoom().getLastMessageId())) {
				roomService.updateRoom(message.getRoom().getId(), senderUsername, null, message.getId(),
						previewContent(message), message.getSentAt());
			}

			// Отправляем обновление через WebSocket
			if (savedMessage.getRoom() != null) {
				for (User member : savedMessage.getRoom().getMembers()) {
					publishToUser(member.getUsername(), "/queue/messages",
							response);
				}
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
			lockMessageRoom(messageId, username);
			Message message = messageRepository.findById(messageId)
					.orElseThrow(() -> new MessageNotFoundException(
							HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE
									.getMessage()));

			if (message.isDeleted()) {
				throw new MessageNotFoundException(
						HttpResponseMessage.HTTP_MESSAGE_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
			}
			user = userService.getUser(username);


			boolean isSender = message.getSender().getId().equals(user.getId());

			if (!isSender) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_MESSAGE_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}


			message.setDeletedAt(LocalDateTime.now());
			messageRepository.save(message);

            if (message.getRoom() != null && message.getId().equals(message.getRoom().getLastMessageId())) {
                Message last = messageRepository.findFirstByRoomIdAndDeletedAtIsNullOrderByIdDesc(message.getRoom().getId());
                roomService.updateRoom(message.getRoom().getId(), username,
                        last == null ? null : last.getId(), last == null ? null : previewContent(last),
                        last == null ? message.getRoom().getCreatedAt() : last.getSentAt());
            }

			if (message.getRoom() != null) {
				ShortMessageWrapped response =
						toWrappedShortMessage(message, EventType.MESSAGE_DELETE);


				for (User member : message.getRoom().getMembers()) {
					publishToUser(member.getUsername(), "/queue/messages",
							response);
				}
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
		Room room = roomService.getRoomForUpdate(roomId, username);
		if (room.getType() == com.zvonok.model.enumeration.RoomType.PRIVATE) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_ROOM_MESSAGES_CLEAR_PRIVATE_DENIED_RESPONSE_MESSAGE
							.getMessage());
		}
		LocalDateTime clearedAt = LocalDateTime.now();
		messageRepository.softDeleteAllByRoomId(roomId, clearedAt);
		roomReadStateService.resetRoomReadStates(roomId);
		roomService.updateRoom(roomId, username, null, null, room.getCreatedAt());

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
			publishToUser(member.getUsername(),
					BrokerPath.ROOM_EVENTS_QUEUE_PATH.getPath(), event);
		}
	}

	public List<ShortMessageWrapped> getRoomMessages(String currentUsername, Long roomId,
			Long beforeMessageId, int limit) {
		roomService.getRoom(roomId, currentUsername);

		PageRequest pageRequest = PageRequest.of(0, limit);

		org.springframework.data.domain.Page<Message> page;

		if (beforeMessageId == null) {
			page = messageRepository.findByRoomIdAndDeletedAtIsNullOrderByIdDesc(roomId,
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
				.findByRoomIdAndDeletedAtIsNullOrderByIdAsc(privateRoom.getId());
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
		publishToUser(username, "/queue/errors", messageResponse);
	}

	@Transactional(readOnly = true)
	public ShortMessageWrapped getShortMessageWrapped(Long id, String username) {
		Message message = messageAccessService.getReadableMessage(id, username);
		return toWrappedShortMessage(message, EventType.MESSAGE);
	}

	@Transactional(readOnly = true)
	public MessageAttachment getAttachmentForDownload(Long attachmentId, String username) {
		MessageAttachment attachment = messageAttachmentRepository.findById(attachmentId)
				.orElseThrow(() -> new MessageAttachmentNotFoundException(
						HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
		messageAccessService.getReadableMessage(attachment.getMessage().getId(), username);
		return attachment;
	}

    @Transactional(readOnly = true)
    public RoomAttachmentPage attachments(long roomId, String username,
            String category, String cursor, int limit, String query, String direction) {
        roomService.getRoom(roomId, username);
        if (limit < 1 || limit > 60 || query.length() > 128 || (!direction.equals("asc") && !direction.equals("desc")))
            throw new InvalidMessageAttachmentException("Invalid pagination or query");
        query = query.strip().toLowerCase(Locale.ROOT);
        AttachmentCursor position = AttachmentCursor.decode(cursor, roomId, category, query, direction);
        Set<AttachmentType> types = switch (category) {
            case "all" -> EnumSet.allOf(AttachmentType.class);
            case "media" -> Set.of(AttachmentType.IMAGE, AttachmentType.VIDEO, AttachmentType.VIDEO_NOTE);
            case "image" -> Set.of(AttachmentType.IMAGE);
            case "video" -> Set.of(AttachmentType.VIDEO, AttachmentType.VIDEO_NOTE);
            case "document" -> Set.of(AttachmentType.FILE);
            case "audio" -> Set.of(AttachmentType.AUDIO);
            default -> throw new InvalidMessageAttachmentException("Invalid category");
        };
        List<MessageAttachment> rows = messageAttachmentRepository.library(roomId,
                position == null ? null : position.sentAt(), position == null ? 0 : position.id(), direction.equals("asc"), types, query,
                PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.fromString(direction), "message.sentAt", "id")));
        var items = rows.stream().limit(limit).map(a -> new RoomAttachmentPage.Item(
                libraryAttachmentDto(a), a.getMessage().getId(), a.getMessage().getSentAt(),
                a.getMessage().getSender().getId(), a.getMessage().getSender().getDisplayName())).toList();
        return new RoomAttachmentPage(items,
                rows.size() > limit ? new AttachmentCursor(rows.get(limit - 1).getMessage().getSentAt(),
                        rows.get(limit - 1).getId()).encode(roomId, category, query, direction) : null);
    }

    @Transactional(readOnly = true)
    public ShortMessageWrapped readableMessage(long roomId, long messageId, String username) {
        Message message = messageAccessService.getReadableMessage(messageId, username);
        if (!message.getRoom().getId().equals(roomId)) throw new MessageNotFoundException("Message not found");
        return toWrappedShortMessage(message, EventType.MESSAGE);
    }

    private MessageAttachmentDto libraryAttachmentDto(MessageAttachment a) {
        String name = a.getOriginalFileName().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "");
        if (name.isBlank()) name = "file";
        return new MessageAttachmentDto(a.getId(), a.getType(), null, null, name, a.getContentType(),
                a.getSizeBytes(), a.getWidth(), a.getHeight(), a.getDurationMs(), null);
    }

    @Transactional(readOnly = true)
    public List<ShortMessageWrapped> messageContext(long roomId, long messageId, String username) {
        Message target = messageAccessService.getReadableMessage(messageId, username);
        if (!target.getRoom().getId().equals(roomId)) throw new MessageNotFoundException("Message not found");
        var rows = new ArrayList<>(messageRepository.findByRoomIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                roomId, messageId, PageRequest.of(0, 15)).getContent());
        rows.add(target);
        rows.addAll(messageRepository.findByRoomIdAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                roomId, messageId, PageRequest.of(0, 15)));
        rows.sort(java.util.Comparator.comparing(Message::getId));
        var previews = buildReplyPreviewByParentId(rows);
        return rows.stream().map(m -> toWrappedShortMessage(m, EventType.MESSAGE, previews.get(m.getReplyToMessageId()))).toList();
    }

	// ===== PRIVATE HELPER METHODS =====

    private String normalizeUploadRequestKey(String value) {
        if (value == null) return null;
        try {
            String key = UUID.fromString(value).toString();
            if (!key.equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException e) {
            throw new InvalidMessageAttachmentException("clientRequestId must be a UUID");
        }
    }

    private ShortMessageWrapped findPreviousUpload(String username, long roomId, String key, String hash) {
        if (key == null) return null;
        // Check current access even on retries, including after membership revocation.
        roomService.getRoom(roomId, username);
        Message previous = messageRepository.findBySenderUsernameAndUploadRequestKey(username, key).orElse(null);
        if (previous == null) return null;
        if (previous.isDeleted() || previous.getRoom() == null || previous.getRoom().getId() != roomId
                || !hash.equals(previous.getUploadRequestHash())) {
            throw new InvalidMessageAttachmentException("clientRequestId already used for another or deleted message");
        }
        return toWrappedShortMessage(previous, EventType.MESSAGE);
    }

    private String attachmentRequestHash(long roomId, String content, List<MultipartFile> files,
            AttachmentType type, Long durationMs, String waveform, Long replyToMessageId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeLong(roomId);
                for (Object value : new Object[]{content == null ? "" : content, type, durationMs, waveform, replyToMessageId}) {
                    writeHashField(output, value);
                }
                output.writeInt(files.size());
                for (MultipartFile file : files) {
                    writeHashField(output, file.getOriginalFilename()); writeHashField(output, file.getContentType());
                    output.writeLong(file.getSize());
                    try (var input = file.getInputStream()) { input.transferTo(output); }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new InvalidMessageAttachmentException("Unable to fingerprint attachments");
        }
    }

    private void writeHashField(DataOutputStream output, Object value) throws IOException {
        if (value == null) { output.writeInt(-1); return; }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length); output.write(bytes);
    }

    private void lockMessageRoom(Long messageId, String username) {
        Long roomId = messageRepository.findRoomIdByMessageId(messageId);
        if (roomId == null) throw new MessageNotFoundException("Message not found");
        roomService.getRoomForUpdate(roomId, username);
    }

    private String previewContent(Message message) {
        String text = message.getContent() == null || message.getContent().isBlank()
                ? (message.getAttachments().isEmpty() ? message.getContent() : buildAttachmentLastMessageText(message))
                : message.getContent();
        return text == null || text.codePointCount(0, text.length()) <= 255
                ? text : text.substring(0, text.offsetByCodePoints(0, 255));
    }

    private void cleanupUploads(List<String> keys) {
        for (String key : List.copyOf(keys)) {
            try { s3Service.deleteFile(key); }
            catch (RuntimeException failure) { log.error("Attachment orphan requires cleanup: key={}", key, failure); }
        }
        keys.clear();
    }

    private void publishToUser(String username, String destination, Object payload) {
        TransactionUtils.runAfterCommit(() -> messagingTemplate.convertAndSendToUser(username, destination, payload));
    }


	private Message createMessage(User sender, String content, Room room,
			Long replyToMessageId) {
		Message message = new Message();
		message.setSender(sender);
		message.setContent(content);
		message.setType(MessageType.DEFAULT);
		message.setRoom(room);
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

	private MessageAttachment createAttachment(MultipartFile file,
			AttachmentType requestedType, Long durationMs, String waveform, List<String> createdKeys) {
		AttachmentType type = resolveAttachmentType(file, requestedType);
		validateAttachmentSize(file, type);
		validateAttachmentDuration(file, type, durationMs);
		String storageKey = "message-attachments/" + UUID.randomUUID();
        createdKeys.add(storageKey);
		try (var input = file.getInputStream()) {
			s3Service.uploadFile(storageKey, input, file.getSize(),
					AttachmentFilePolicy.contentType(file));
		} catch (IOException e) {
			throw new InvalidMessageAttachmentException(
					HttpResponseMessage.HTTP_MESSAGE_ATTACHMENT_UPLOAD_FAILED_RESPONSE_MESSAGE
							.getMessage());
		}

		MessageAttachment attachment = new MessageAttachment();
		attachment.setType(type);
		attachment.setStorageKey(storageKey);
		attachment.setOriginalFileName(resolveOriginalFileName(file));
		attachment.setContentType(AttachmentFilePolicy.contentType(file));
		attachment.setSizeBytes(file.getSize());
		attachment.setDurationMs(durationMs);
		attachment.setWaveformJson(waveform);
		if (type == AttachmentType.IMAGE) {
			applyImageDimensions(attachment, file);
		}
		return attachment;
	}

	private AttachmentType resolveAttachmentType(MultipartFile file, AttachmentType requestedType) {
		String contentType = AttachmentFilePolicy.contentType(file);
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
		if (ALLOWED_AUDIO_CONTENT_TYPES.contains(normalized)) return AttachmentType.AUDIO;
		if (AttachmentFilePolicy.isDocument(normalized)) return AttachmentType.FILE;
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
			case FILE -> AttachmentFilePolicy.isDocument(contentType);
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
			case FILE -> 50L * 1024 * 1024;
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
			case FILE -> "File exceeds 50 MB";
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
		try (var input = file.getInputStream()) {
			BufferedImage image = ImageIO.read(input);
			if (image != null) {
				attachment.setWidth(image.getWidth());
				attachment.setHeight(image.getHeight());
			}
		} catch (IOException ignored) {
			// Image dimensions are optional metadata; upload should not fail if unavailable.
		}
	}


	private String resolveOriginalFileName(MultipartFile file) {
		String originalFileName = file.getOriginalFilename();
		return originalFileName == null || originalFileName.isBlank()
				? "attachment"
				: originalFileName;
	}

	private String buildAttachmentLastMessageText(Message message) {
		if (message.getAttachments().stream().anyMatch(a -> a.getType() == AttachmentType.FILE)) return "Файл";
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
		RoomShortDto room = message.getRoom() == null ? null
				: new RoomShortDto(message.getRoom().getId(), message.getRoom().getType());
		ShortMessageWrapped response = new ShortMessageWrapped(message.getId(), message.getContent(),
				message.getType(), eventType, message.getSentAt(), sender, room, message.getEditedAt(),
				message.getReplyToMessageId(), replyPreview, mapAttachments(message));
		return response;
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

	private List<MessageAttachmentDto> mapAttachments(Message message) {
		if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
			return List.of();
		}
		return message.getAttachments().stream()
				.map(this::toAttachmentDto)
				.toList();
	}

	public MessageAttachmentDto toAttachmentDto(MessageAttachment attachment) {
		String downloadUrl = "/api/message-attachments/" + attachment.getId() + "/download";
		return new MessageAttachmentDto(attachment.getId(), attachment.getType(), downloadUrl,
				downloadUrl, attachment.getOriginalFileName(), attachment.getContentType(),
				attachment.getSizeBytes(), attachment.getWidth(), attachment.getHeight(),
				attachment.getDurationMs(), attachment.getWaveformJson());
	}

	private void validateReplyTarget(Long replyToMessageId, Room room) {
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
