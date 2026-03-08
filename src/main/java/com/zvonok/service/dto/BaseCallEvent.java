package com.zvonok.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseCallEvent {
	CallType type;
	Long chatRoomId;
}
