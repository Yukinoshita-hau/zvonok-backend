package com.zvonok.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenRefreshRequest {

    @NotBlank(message = "RefreshToken is required")
    @Size(min = 59, max = 59, message = "RefreshToken must be 59 characters")
    private String refreshToken;
}

