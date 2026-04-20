package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.zvonok.model.enumeration.RoomType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomResponse {
	private Long id;
	private String name;
	private RoomType type;
	private String avatarUrl;
	private Boolean isActive;
	private LocalDateTime createdAt;
	private Long lastMessageId;
	private String lastMessageContent;
	private LocalDateTime lastActivityAt;
	private List<RoomMemberShortDto> members;
	private int unreadCount;
	private Long firstUnreadMessageId;
}
