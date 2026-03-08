package com.zvonok.controller;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import org.springframework.messaging.Message;
import com.zvonok.service.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@MessageMapping("/friend")
@RequiredArgsConstructor
@Slf4j
public class WSFriendController {

	private final FriendService friendService;


	@MessageMapping("/send/{receiverUsername}")
	public void sendFriendRequest(Principal principal,
			@DestinationVariable String receiverUsername) {

		String username = resolvePrincipalName(principal);

		friendService.sendFriendRequest(username, receiverUsername);
	}

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName() : null;
		log.error("WebSocket exception for user '{}': {}", username, ex.getMessage(), ex);
		friendService.sendErrorMessage(username, ex.getMessage(), getStatusFromException(ex));
	}

	private HttpStatus getStatusFromException(Exception ex) {
		ApiException apiException = ex.getClass().getAnnotation(ApiException.class);
		return apiException != null ? apiException.status() : HttpStatus.INTERNAL_SERVER_ERROR;
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
