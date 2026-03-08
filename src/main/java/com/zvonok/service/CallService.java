package com.zvonok.service;

import java.time.LocalDateTime;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.exception.LiveKitRoomException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.service.dto.AcceptCallResponse;
import com.zvonok.service.dto.CallType;
import com.zvonok.service.dto.InviteCallResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CallService {

	private final RoomService roomService;
	private final SimpMessagingTemplate messagingTemplate;
	private final String CALL_QUEUE_PATH = "/queue/call";

	@Transactional(readOnly = true)
	public void callInvite(String callerUsername, InviteCallDto dto) {
		Room room = roomService.getRoom(dto.getChatRoomId(), callerUsername);

		InviteCallResponse response = inviteResponseWrapper(dto, room, callerUsername);

		for (User member : room.getMembers()) {
			if (!member.getUsername().equals(callerUsername)) {
				messagingTemplate.convertAndSendToUser(member.getUsername(), CALL_QUEUE_PATH,
						response);
			}
		}
	}

	public void callAccept(String acceptUsername, AcceptCallDto dto) {
		Room room = roomService.getRoom(dto.getChatRoomId(), acceptUsername);

		AcceptCallResponse response = acceptResponseWrapper(dto, room, acceptUsername);

		messagingTemplate.convertAndSendToUser(dto.getCallerUsername(), CALL_QUEUE_PATH, response);
	}

	private AcceptCallResponse acceptResponseWrapper(AcceptCallDto dto, Room room,
			String username) {
		AcceptCallResponse response = new AcceptCallResponse();

		response.setType(CallType.CALL_ACCEPT);
		response.setChatRoomId(room.getId());
		response.setFromUser(username);
		response.setLiveKitRoomName(createLiveKitRoomName(room));
		response.setTimestamp(LocalDateTime.now());

		return response;
	}

	private InviteCallResponse inviteResponseWrapper(InviteCallDto dto, Room room,
			String username) {
		InviteCallResponse response = new InviteCallResponse();

		response.setType(CallType.CALL_INVITE);
		response.setChatRoomId(room.getId());
		response.setCallType(dto.getCallType());
		response.setLiveKitRoomName(createLiveKitRoomName(room));
		response.setFromUser(username);
		response.setTimestamp(LocalDateTime.now());

		return response;
	}

	private String createLiveKitRoomName(Room room) {
		if (room.getType().equals(RoomType.PRIVATE)) {
			return "dm-" + room.getId();
		} else if (room.getType().equals(RoomType.GROUP)) {
			return "group-" + room.getId();
		} else {
			throw new LiveKitRoomException(
					HttpResponseMessage.HTTP_LIVEKIT_ROOM_RESPONSE_MESSAGE.getMessage());
		}
	}
}

