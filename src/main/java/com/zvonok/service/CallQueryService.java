package com.zvonok.service;

import org.springframework.stereotype.Service;
import com.zvonok.controller.dto.ActiveCallResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallQueryService {

	private final CallSessionService callSessionService;
	private final LiveKitRoomAdminService liveKitRoomAdminService;

	public ActiveCallResponse findActiveCallResponse(Long roomId, String username) {
		ActiveCallResponse response =
				callSessionService.findActiveCallResponseFromDB(roomId, username);

		if (response == null) {
			return null;
		}

		int participantsCount =
				liveKitRoomAdminService.countParticipants(response.getLiveKitRoomName());

		response.setParticipantsCount(participantsCount);

		return response;
	}
}
