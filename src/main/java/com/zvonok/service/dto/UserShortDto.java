package com.zvonok.service.dto;

import com.zvonok.model.enumeration.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserShortDto {
    private Long id;
    private String username;
    private String displayName;
    private String aboutMe;
    private String avatarUrl;
    private UserStatus status;
    private LocalDateTime lastSeenAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
