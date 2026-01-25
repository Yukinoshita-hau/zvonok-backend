package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateServerMemberNicknameRequest {

    @Size(max = 50, message = "Nickname must be at most 50 characters")
    private String nickname;
}

