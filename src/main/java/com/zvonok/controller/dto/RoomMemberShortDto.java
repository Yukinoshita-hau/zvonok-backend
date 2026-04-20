package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.UserStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomMemberShortDto {
	private Long id;
	private String username;
	private String displayName;
	private UserStatus status;
	private LocalDateTime lastSeenAt;
	private String avatarUrl;
	private LocalDateTime updatedAt;
	private LocalDateTime createdAt;
}
