package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SenderDto {
    private final Long id;
    private final String username;
    private final String avatarUrl;
    private final UserStatus status;
}
