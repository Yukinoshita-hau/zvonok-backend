package com.zvonok.controller;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.CallService;
import lombok.RequiredArgsConstructor;

@Controller
@MessageMapping("/call")
@RequiredArgsConstructor
public class CallController {

	private final CallService callService;

	@MessageMapping("/invite")
	public void callInviteEvent(Principal principal, @Payload InviteCallDto dto) {
		String username = resolvePrincipalName(principal);

		callService.callInvite(username, dto);
	}

	@MessageMapping("/accept")
	public void callAcceptEvent(Principal principal, @Payload AcceptCallDto dto) {
		String username = resolvePrincipalName(principal);

		callService.callAccept(username, dto);
	}


	@MessageMapping("/decline")
	public void callDecline(Principal principal, @Payload DeclineCallDto dto) {
		String username = resolvePrincipalName(principal);

		callService.callDecline(username, dto);
	}

	private String resolvePrincipalName(Principal principal) {
		if (principal == null || principal.getName() == null) {
			throw new AuthenticatedPrincipalRequiredException(
					BusinessRuleMessage.BUSINESS_AUTHENTICATED_PRINCIPAL_REQUIRED_MESSAGE
							.getMessage());
		}
		return principal.getName();
	}
}
