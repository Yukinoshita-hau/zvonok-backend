package com.zvonok.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.controller.dto.JoinCallDto;
import com.zvonok.controller.dto.LeaveCallDto;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.CallService;
import com.zvonok.service.enums.BrokerPath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@MessageMapping("/call")
@RequiredArgsConstructor
@Slf4j
public class WSCallController {

	private final CallService callService;
	private final SimpMessagingTemplate messagingTemplate;

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

	@MessageMapping("/join")
	public void callJoin(Principal principal, @Payload JoinCallDto dto) {
		String username = resolvePrincipalName(principal);
		callService.callJoin(username, dto);
	}

	@MessageMapping("/decline")
	public void callDecline(Principal principal, @Payload DeclineCallDto dto) {
		String username = resolvePrincipalName(principal);
		callService.callDecline(username, dto);
	}

	@MessageMapping("/end")
	public void callEnd(Principal principal, @Payload EndCallDto dto) {
		String username = resolvePrincipalName(principal);
		callService.callEnd(username, dto);
	}

	@MessageMapping("/leave")
	public void callLeave(Principal principal, @Payload LeaveCallDto dto) {
		String username = resolvePrincipalName(principal);
		callService.callLeave(username, dto);
	}

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName() : null;
		String destination = (String) message.getHeaders().get("simpDestination");
		log.error("Call WS error: user='{}', destination='{}', message='{}'", username,
				destination, ex.getMessage(), ex);

		if (username != null) {
			ChatErrorMessageResponse response = new ChatErrorMessageResponse();
			response.setMessage(ex.getMessage());
			response.setStatus(getStatusFromException(ex).value());
			messagingTemplate.convertAndSendToUser(username, BrokerPath.ERRORS_QUEUE_PATH.getPath(),
					response);
		}
	}

	private String resolvePrincipalName(Principal principal) {
		if (principal == null || principal.getName() == null) {
			throw new AuthenticatedPrincipalRequiredException(
					BusinessRuleMessage.BUSINESS_AUTHENTICATED_PRINCIPAL_REQUIRED_MESSAGE
							.getMessage());
		}
		return principal.getName();
	}

	private HttpStatus getStatusFromException(Exception ex) {
		ApiException apiException = ex.getClass().getAnnotation(ApiException.class);
		return apiException != null ? apiException.status() : HttpStatus.INTERNAL_SERVER_ERROR;
	}
}
