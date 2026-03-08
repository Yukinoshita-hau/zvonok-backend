package com.zvonok.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AcceptCallDto {
	Long chatRoomId;
	String callerUsername;
}
