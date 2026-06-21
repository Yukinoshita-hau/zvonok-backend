package com.zvonok.service;

import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.controller.dto.JoinCallDto;
import com.zvonok.controller.dto.LeaveCallDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CallService {

	private final CallSessionService callSessionService;

	public void callInvite(String callerUsername, InviteCallDto dto) {
		callSessionService.startCall(callerUsername, dto);
	}

	public void callAccept(String acceptUsername, AcceptCallDto dto) {
		callSessionService.accept(acceptUsername, dto);
	}

	public void callJoin(String joinUsername, JoinCallDto dto) {
		callSessionService.join(joinUsername, dto);
	}

	public void callDecline(String declineUsername, DeclineCallDto dto) {
		callSessionService.decline(declineUsername, dto);
	}

	public void callEnd(String username, EndCallDto dto) {
		callSessionService.end(username, dto);
	}

	public void callLeave(String username, LeaveCallDto dto) {
		callSessionService.leave(username, dto);
	}
}
