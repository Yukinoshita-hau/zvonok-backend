package com.zvonok.service.mapper;

import com.zvonok.model.User;
import com.zvonok.service.dto.UserShortDto;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserShortDto toUserShortDto(User user) {
        UserShortDto dto = new UserShortDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(null);
        dto.setAboutMe(null);
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setStatus(user.getStatus());
        dto.setLastSeenAt(user.getLastSeenAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setCreatedAt(user.getCreateAt());
        return dto;
    }
}
