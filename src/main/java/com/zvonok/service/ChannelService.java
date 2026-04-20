package com.zvonok.service;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.ReplyPreviewDto;
import com.zvonok.controller.dto.SenderDto;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.Message;
import com.zvonok.model.User;
import com.zvonok.repository.ChannelRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.dto.CreateChannelDto;
import com.zvonok.service.dto.EventType;
import com.zvonok.service.dto.UpdateChannelDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing channels within channel folders. Сервис для управления каналами в папках
 * каналов.
 */
@Service
public class ChannelService {

	private final ChannelRepository channelRepository;
	private final MessageRepository messageRepository;
	private final ChannelFolderService channelFolderService;
	private final UserService userService;
	private final PermissionService permissionService;

	public ChannelService(ChannelRepository channelRepository, MessageRepository messageRepository,
			@Lazy ChannelFolderService channelFolderService, UserService userService,
			@Lazy PermissionService permissionService) {
		this.channelRepository = channelRepository;
		this.messageRepository = messageRepository;
		this.channelFolderService = channelFolderService;
		this.userService = userService;
		this.permissionService = permissionService;
	}

	public Channel getChannel(String username, Long folderId, Long channelId) {
		User user = userService.getUser(username);
		Channel channel = getChannel(folderId, channelId);

		if (!permissionService.canUserViewChannel(user.getId(), channel.getId())) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		return channel;
	}

	public Channel getChannelByIdInternal(Long id) {
		return channelRepository.findById(id).orElseThrow(() -> new ChannelNotFoundException(
				HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public Channel getChannel(Long folderId, Long channelId) {
		return channelRepository.findByIdAndFolderId(channelId, folderId)
				.orElseThrow(() -> new ChannelNotFoundException(
						HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public List<Channel> getChannelsOrdered(Long folderId) {
		return channelRepository.findByFolderIdOrderByPosition(folderId).stream()
				.filter(Channel::getIsActive).collect(Collectors.toList());
	}

	public List<Channel> getChannelsByFolderId(Long folderId) {
		return channelRepository.findByFolderIdAndIsActiveTrue(folderId);
	}

	public Channel createChannel(CreateChannelDto createChannelDto, String username) {
		ChannelFolder folder =
				channelFolderService.getChannelFolder(createChannelDto.getFolderId(), username);

		Channel channel = new Channel();
		channel.setName(createChannelDto.getName());
		channel.setFolder(folder);
		channel.setType(createChannelDto.getType());
		channel.setPosition(createChannelDto.getPosition());
		channel.setTopic(createChannelDto.getTopic());
		channel.setCreatedAt(LocalDateTime.now());
		channel.setUserLimit(createChannelDto.getUserLimit());

		return channelRepository.save(channel);
	}

	public Channel updateChannel(Long channelId, UpdateChannelDto updateChannelDto) {
		Channel channel = getChannelByIdInternal(channelId);

		if (updateChannelDto.getName() != null) {
			channel.setName(updateChannelDto.getName());
		}
		if (updateChannelDto.getPosition() != null) {
			channel.setPosition(updateChannelDto.getPosition());
		}
		if (updateChannelDto.getUserLimit() != null) {
			channel.setUserLimit(updateChannelDto.getUserLimit());
		}
		if (updateChannelDto.getSlowModeSeconds() != null) {
			channel.setSlowModeSeconds(updateChannelDto.getSlowModeSeconds());
		}
		if (updateChannelDto.getTopic() != null) {
			channel.setTopic(updateChannelDto.getTopic());
		}
		if (updateChannelDto.getNsfw() != null) {
			channel.setNsfw(updateChannelDto.getNsfw());
		}
		if (updateChannelDto.getActive() != null) {
			channel.setIsActive(updateChannelDto.getActive());
		}

		return channelRepository.save(channel);
	}

	public void deleteChannel(Long channelId) {
		Channel channel = getChannelByIdInternal(channelId);
		channel.setIsActive(false);
		channelRepository.save(channel);
	}

	public List<ChannelMessageResponse> getChannelMessage(String username, Long folderId,
			Long channelId, Long beforeMessageId, int limit) {
		Channel channel = getChannel(username, folderId, channelId);
		PageRequest pageRequest = PageRequest.of(0, limit);
		Page<Message> page;

		if (beforeMessageId == null) {
			page = messageRepository.findByChannelIdAndDeletedAtIsNullOrderBySentAtDesc(channelId,
					pageRequest);
		} else {
			page = messageRepository.findByChannelIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
					channelId, beforeMessageId, pageRequest);
		}

		Map<Long, ReplyPreviewDto> replyPreviewByParentId =
				buildReplyPreviewByParentId(page.getContent());

		return page.getContent().stream().sorted(Comparator.comparing(Message::getId))
				.map(message -> mapToChannelMessageResponse(message, channel,
						replyPreviewByParentId.get(message.getReplyToMessageId())))
				.toList();
	}

	private ChannelMessageResponse mapToChannelMessageResponse(Message message, Channel channel,
			ReplyPreviewDto replyPreview) {
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
		response.setEventType(EventType.MESSAGE);
		response.setReplyToMessageId(message.getReplyToMessageId());
		response.setReplyPreview(replyPreview);
		response.setEditedAt(message.getEditedAt());
		return response;
	}

	private Map<Long, ReplyPreviewDto> buildReplyPreviewByParentId(List<Message> messages) {
		Set<Long> replyToIds = new HashSet<>();
		for (Message message : messages) {
			if (message.getReplyToMessageId() != null) {
				replyToIds.add(message.getReplyToMessageId());
			}
		}

		if (replyToIds.isEmpty()) {
			return Map.of();
		}

		Map<Long, Message> parentById = new HashMap<>();
		for (Message parent : messageRepository.findAllById(replyToIds)) {
			parentById.put(parent.getId(), parent);
		}

		Map<Long, ReplyPreviewDto> previewByParentId = new HashMap<>();
		for (Long replyToId : replyToIds) {
			Message parent = parentById.get(replyToId);
			if (parent == null) {
				previewByParentId.put(replyToId,
						new ReplyPreviewDto(replyToId, null, null, null, null, null, true));
				continue;
			}

			boolean deleted = parent.isDeleted();
			String snippet = deleted ? null : buildSnippet(parent.getContent());
			previewByParentId.put(replyToId,
					new ReplyPreviewDto(parent.getId(), parent.getSender().getId(),
							parent.getSender().getUsername(), parent.getSender().getDisplayName(),
							snippet, parent.getType(), deleted));
		}

		return previewByParentId;
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
}

