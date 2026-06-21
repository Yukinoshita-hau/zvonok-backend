package com.zvonok.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinCallDto {
	Long callId;
	Long chatRoomId;
	String callerUsername;
}
