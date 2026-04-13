package com.zvonok.controller;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.MessageReadStatusService;
import com.zvonok.service.MessageService;
import com.zvonok.service.dto.MessageReadStatusContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@MessageMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

	private final MessageService messageService;
	private final MessageReadStatusService readStatusService;

	@MessageMapping("/private/{receiverUsername}")
	public void sendPrivateMessage(@DestinationVariable String receiverUsername,
			Principal principal, @Payload String content) {
		String sender = resolvePrincipalName(principal);
		validateContent(content);

		messageService.sendPrivateMessage(sender, receiverUsername, content);
	}

	@MessageMapping("/send/{roomId}")
	public void sendMessage(@DestinationVariable Long roomId, Principal principal,
			@Payload String content) {
		String sender = resolvePrincipalName(principal);
		validateContent(content);

		messageService.sendMessage(sender, roomId, content);
	}

	@MessageMapping("/channel/{channelId}")
	public void sendChannelMessage(@DestinationVariable Long channelId, Principal principal,
			@Payload String content) {
		String sender = resolvePrincipalName(principal);
		validateContent(content);

		messageService.sendChannelMessage(sender, channelId, content);
	}

	@MessageMapping("/edit/{messageId}")
	public void editMessage(@DestinationVariable Long messageId, Principal principal,
			@Payload String newContent) {
		String sender = resolvePrincipalName(principal);
		validateContent(newContent);

		messageService.editMessage(messageId, sender, newContent);
	}

	@MessageMapping("/delete/{messageId}")
	public void deleteMessage(@DestinationVariable Long messageId, Principal principal) {
		String username = resolvePrincipalName(principal);

		messageService.deleteMessage(messageId, username);
	}

	@MessageMapping("/read/{messageId}")
	public void handleRead(@DestinationVariable Long messageId,
			Principal principal) {
		String sender = resolvePrincipalName(principal);
		readStatusService.markMessageAsRead(messageId, sender);
	}

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName() : null;
		log.error("WebSocket exception for user '{}': {}", username, ex.getMessage(), ex);
		messageService.sendErrorMessage(username, ex.getMessage(), getStatusFromException(ex));
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

	private void validateContent(String content) {
		if (content == null || content.trim().isEmpty()) {
			throw new IllegalArgumentException("Message content must not be empty");
		}
	}
}
