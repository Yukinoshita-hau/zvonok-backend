package com.zvonok.service.dto.response;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MeResponse {
    private String username;
    private String message;
    private LocalDateTime time;
}
