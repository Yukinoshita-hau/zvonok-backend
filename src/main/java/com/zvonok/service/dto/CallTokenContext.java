package com.zvonok.service.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;

public record CallTokenContext (
	Long callId,
	String livekitRoomName,
	RoomType roomType,
	CallSessionStatus callStatus,
	LocalDateTime livekitRoomReadyAt,
	String username,
	String displayName,
	CallParticipantStatus participantStatus
){}
