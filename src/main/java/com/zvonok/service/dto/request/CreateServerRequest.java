package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateServerRequest {

    @NotBlank(message = "Server name cannot be empty")
    @Size(min = 5, max = 100, message = "Name must be between 5 and 100 characters")
    private String name;

	@NotNull(message = "MaxMembers cannot be empty")
	@Min(value = 10)
	@Max(10000)
    private Integer maxMembers;
}
