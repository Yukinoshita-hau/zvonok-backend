package com.zvonok.service.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeclineCallResponse extends BaseCallEvent {
	String fromUser;
	LocalDateTime timestamp;
}
