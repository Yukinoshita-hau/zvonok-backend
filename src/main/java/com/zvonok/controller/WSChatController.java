package com.zvonok.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zvonok.controller.dto.WebSocketMessageRequest;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.MessageReadStatusService;
import com.zvonok.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@MessageMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class WSChatController {

	private final MessageService messageService;
	private final MessageReadStatusService readStatusService;

	@MessageMapping("/send/{roomId}")
	public void sendMessage(@DestinationVariable Long roomId, Principal principal,
			@Payload JsonNode payload) {
		String sender = resolvePrincipalName(principal);
		WebSocketMessageRequest request = parseMessageRequest(payload);
		validateContent(request.getContent());

		messageService.sendMessage(sender, roomId, request.getContent(),
				request.getReplyToMessageId());
	}

	@MessageMapping("/channel/{channelId}")
	public void sendChannelMessage(@DestinationVariable Long channelId, Principal principal,
			@Payload JsonNode payload) {
		String sender = resolvePrincipalName(principal);
		WebSocketMessageRequest request = parseMessageRequest(payload);
		validateContent(request.getContent());

		messageService.sendChannelMessage(sender, channelId, request.getContent(),
				request.getReplyToMessageId());
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

	private WebSocketMessageRequest parseMessageRequest(JsonNode payload) {
		if (payload == null || payload.isNull()) {
			return new WebSocketMessageRequest(null, null);
		}

		if (payload.isTextual()) {
			return new WebSocketMessageRequest(payload.asText(), null);
		}

		String content = payload.has("content") && !payload.get("content").isNull()
				? payload.get("content").asText()
				: null;

		Long replyToMessageId = extractNullableLong(payload, "replyToMessageId");
		if (replyToMessageId == null) {
			replyToMessageId = extractNullableLong(payload, "parentMessageId");
		}

		return new WebSocketMessageRequest(content, replyToMessageId);
	}

	private Long extractNullableLong(JsonNode payload, String fieldName) {
		if (!payload.has(fieldName) || payload.get(fieldName).isNull()) {
			return null;
		}

		JsonNode node = payload.get(fieldName);
		if (node.isNumber()) {
			return node.longValue();
		}

		if (node.isTextual()) {
			String raw = node.asText().trim();
			if (raw.isEmpty()) {
				return null;
			}
			try {
				return Long.parseLong(raw);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(fieldName + " must be a valid number");
			}
		}

		throw new IllegalArgumentException(fieldName + " must be a valid number");
	}
}
