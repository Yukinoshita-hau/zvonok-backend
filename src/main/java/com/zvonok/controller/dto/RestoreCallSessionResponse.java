package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.service.enums.CallRestoreType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RestoreCallSessionResponse {
	private CallRestoreType callRestoreType;
	private boolean restorable;
	private Long callId;
	private Long chatRoomId;
	private Long roomId;
	private RoomType roomType;
	private CallSessionStatus callStatus;
	private String hostUsername;
	private CallParticipantStatus participantStatus;
	private String liveKitRoomName;
	private String serverUrl;
	private String participantToken;
	private LocalDateTime expiresAt;

	public static RestoreCallSessionResponse empty() {
		return RestoreCallSessionResponse.builder().restorable(false).callRestoreType(CallRestoreType.NONE).build();
	}
}
