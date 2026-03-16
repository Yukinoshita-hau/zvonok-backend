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
	private UserStatus status;
	private LocalDateTime lastSeenAt;
	private String avatartUrl;
	private LocalDateTime updatedAt;
	private LocalDateTime createdAt;
}
