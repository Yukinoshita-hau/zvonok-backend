package com.zvonok.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeclineCallDto {
	Long chatRoomId;
	String declineUsername;
}
