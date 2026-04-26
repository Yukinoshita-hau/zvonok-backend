package com.zvonok.service.dto;

import com.zvonok.service.dto.response.UserShortResponse;
import com.zvonok.service.enums.UserEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserProfileUpdatedEvent {
	private UserEventType eventType;
	private UserShortResponse user;
}
