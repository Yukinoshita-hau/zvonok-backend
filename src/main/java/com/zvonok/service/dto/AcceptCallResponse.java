package com.zvonok.service.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AcceptCallResponse extends BaseCallEvent{
	String fromUser;
	String liveKitRoomName;
	LocalDateTime timestamp;
}
