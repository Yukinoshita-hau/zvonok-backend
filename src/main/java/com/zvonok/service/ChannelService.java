package com.zvonok.service;

import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.model.ChannelFolder;
import com.zvonok.repository.ChannelRepository;
import com.zvonok.service.dto.CreateChannelDto;
import com.zvonok.service.dto.UpdateChannelDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing channels within channel folders.
 * Сервис для управления каналами в папках каналов.
 */
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelFolderService channelFolderService;

    public ChannelService(
            ChannelRepository channelRepository,
            @Lazy ChannelFolderService channelFolderService) {
        this.channelRepository = channelRepository;
        this.channelFolderService = channelFolderService;
    }

    public Channel getChannel(long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException(
                        HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public List<Channel> getChannelsByFolderId(Long folderId) {
        return channelRepository.findByFolderIdAndIsActiveTrue(folderId);
    }

    public Channel createChannel(CreateChannelDto createChannelDto) {
        ChannelFolder folder = channelFolderService.getChannelFolder(createChannelDto.getFolderId());

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
        Channel channel = getChannel(channelId);

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
        Channel channel = getChannel(channelId);
        channel.setIsActive(false);
        channelRepository.save(channel);
    }

    public Channel getChannel(Long folderId, Long channelId) {
        return channelRepository.findByIdAndFolderId(channelId, folderId)
                .orElseThrow(() -> new ChannelNotFoundException(
                        HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public List<Channel> getChannelsOrdered(Long folderId) {
        return channelRepository.findByFolderIdOrderByPosition(folderId).stream()
                .filter(Channel::getIsActive)
                .collect(Collectors.toList());
    }
}
