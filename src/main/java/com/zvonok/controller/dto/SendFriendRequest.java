package com.zvonok.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "SendFriendRequest", description = "Запрос на отправку заявки в друзья.")
public class SendFriendRequest {

	@Schema(description = "Username пользователя, которому отправляется заявка.", example = "Aliya",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED, minLength = 3)
	@NotBlank(message = "Receiver username must be provided")
	private String receiverUsername;
}

