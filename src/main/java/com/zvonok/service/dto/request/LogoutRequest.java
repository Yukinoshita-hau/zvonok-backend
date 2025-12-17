package com.zvonok.service.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.zvonok.utils.StrictBooleanDeserializer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequest {

    @NotBlank(message = "RefreshToken is required")
    @Size(min = 59, max = 59, message = "RefreshToken must be 59 characters")
    private String refreshToken;

    @JsonDeserialize(using = StrictBooleanDeserializer.class)
    private boolean allDevices = false;

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }
}