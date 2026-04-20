package com.zvonok.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDto {
	@Size(min = 3, max = 50, message = "Display name must be between 3 and 50 characters")
	private String displayName;

    //@Email(message = "Invalid email format")
    //@Size(min = 6, max = 100, message = "Email must be between 5 and 100 characters")
    //private String email;

	@Size(min= 20, max = 100, message = "Avatar url must be between 20 and 100 characters")
    private String avatarUrl;

	@Size(max = 1000, message = "About me must be not more then 1000 characters")
	private String aboutMe;
}
