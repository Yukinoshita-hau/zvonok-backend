package com.zvonok.service.dto;

import java.time.LocalDateTime;
import com.zvonok.controller.enums.CallInviteType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InviteCallResponse extends BaseCallEvent {
	CallInviteType mediaType;
	String fromUser;
	String liveKitRoomName;
	LocalDateTime timestamp;
}
