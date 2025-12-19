package com.zvonok.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateServerRequest {

    @NotBlank(message = "Server name cannot be empty")
    @Size(min = 5, max = 100, message = "Name must be between 5 and 100 characters")
    private String name;

    @Size(min = 10, max = 10000, message = "MaxMembers must be between 10 and 10000 members")
    private Integer maxMembers;
}
