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

	@MessageMapping("/accept/{requestId}")
	public void acceptFriendRequest(Principal principal, @DestinationVariable Long requestId) {

		String username = resolvePrincipalName(principal);

		friendService.acceptFriendRequest(requestId, username);
	}

	@MessageMapping("/reject/{requestId}")
	public void rejectFriendRequest(Principal principal, @DestinationVariable Long requestId) {

		String username = resolvePrincipalName(principal);

		friendService.rejectFriendRequest(requestId, username);
	}

	@MessageMapping("/cancel/{requestId}")
	public void cancelFriendRequest(Principal principal, @DestinationVariable Long requestId) {
		String username = resolvePrincipalName(principal);

		friendService.cancelFriendRequest(requestId, username);
	}

	@MessageMapping("/remove/{friendUsername}")
	public void cancelFriend(Principal principal, @DestinationVariable String friendUsername) {

		String username = resolvePrincipalName(principal);

		friendService.removeFriend(username, friendUsername);
	}

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName() : "anonymous";
		String destination = (String) message.getHeaders().get("simpDestination");

		log.error("WS error: user='{}', destination='{}', exception='{}', message='{}'", username,
				destination, ex.getClass().getSimpleName(), ex.getMessage(), ex);
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
