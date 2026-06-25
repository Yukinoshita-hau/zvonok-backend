package com.zvonok.controller;

import com.zvonok.controller.dto.CanvasDrawEventDto;
import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.CanvasBoardService;
import com.zvonok.service.enums.BrokerPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WSCanvasBoardController {

	private final CanvasBoardService canvasBoardService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/calls/{callId}/boards/{boardId}/draw")
	public void handleDrawEvent(@DestinationVariable Long callId,
			@DestinationVariable Long boardId,
			@Payload CanvasDrawEventDto event,
			Principal principal) {
		String username = resolvePrincipalName(principal);
		canvasBoardService.handleDrawEvent(callId, boardId, event, username);
	}

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName() : null;
		String destination = (String) message.getHeaders().get("simpDestination");
		HttpStatus status = getStatusFromException(ex);
		if (status.is4xxClientError()) {
			log.warn("Canvas WS rejected message: user='{}', destination='{}', status={}, message='{}'",
					username, destination, status.value(), ex.getMessage());
		} else {
			log.error("Canvas WS error: user='{}', destination='{}', status={}, message='{}'",
					username, destination, status.value(), ex.getMessage(), ex);
		}

		if (username != null) {
			ChatErrorMessageResponse response = new ChatErrorMessageResponse();
			response.setMessage(ex.getMessage());
			response.setStatus(status.value());
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
