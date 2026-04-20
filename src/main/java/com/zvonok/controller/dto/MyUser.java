package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.UserStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyUser {
	private Long id;
	private String username;
	private String displayName;
	private String aboutMe;
	private String email;
	private Boolean isEmailVerified;
	private String avatarUrl;
	private UserStatus status = UserStatus.OFFLINE;
	private LocalDateTime lastSeenAt;
	private LocalDateTime updatedAt;
	private LocalDateTime createdAt;
}
