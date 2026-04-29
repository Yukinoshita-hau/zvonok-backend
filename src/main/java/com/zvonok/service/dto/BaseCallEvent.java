package com.zvonok.service.dto;

import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BaseCallEvent {
	String eventId;
	CallType type;
	Long callId;
	Long chatRoomId;
	Long roomId;
	RoomType roomType;
	String liveKitRoomName;
	String callerUsername;
	String hostUsername;
	String participantUsername;
	String callType;
	CallSessionStatus callStatus;
	CallParticipantStatus participantStatus;
	Integer participantsCount;
	LocalDateTime occurredAt;
}
