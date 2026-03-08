package com.zvonok.controller.dto;

import com.zvonok.controller.enums.CallInviteType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InviteCallDto {
	CallInviteType callType;
	Long chatRoomId;
}
