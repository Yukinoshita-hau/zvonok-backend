package com.zvonok.controller.dto;

import java.util.List;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ActiveCallResponse {
	private Long callId;
	private Long chatRoomId;
	private Long roomId;
	private RoomType roomType;
	private CallSessionStatus status;
	private String liveKitRoomName;
	private String hostUsername;
	private String callerUsername;
	private String callType;
	private int participantsCount;
	private List<CallParticipantResponse> participants;
	private LocalDateTime startedAt;
	private LocalDateTime activatedAt;
	private LocalDateTime createAt;
}
