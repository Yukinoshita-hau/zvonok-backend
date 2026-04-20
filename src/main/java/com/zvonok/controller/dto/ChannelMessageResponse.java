package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.MessageType;
import com.zvonok.service.dto.EventType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelMessageResponse {
    private Long id;
    private String content;
	private SenderDto sender;
    private LocalDateTime sentAt;
    private MessageType type;
    private Long channelId;
    private EventType eventType;
    private Long replyToMessageId;
	private ReplyPreviewDto replyPreview;
	private LocalDateTime editedAt;
}
