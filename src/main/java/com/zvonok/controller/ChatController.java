package com.zvonok.controller;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception_handler.annotation.ApiException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.MessageService;
import lombok.RequiredArgsConstructor;
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
public class ChatController {

    private final MessageService messageService;

    @MessageMapping("/private/{receiverUsername}")
    public MessageResponse sendPrivateMessage(@DestinationVariable String receiverUsername,
                                              Principal principal,
                                              @Payload String content) {
        String sender = resolvePrincipalName(principal);
        validateContent(content);

        return messageService.sendPrivateMessage(sender, receiverUsername, content);
    }

    @MessageMapping("/group/{roomId}")
    public MessageResponse sendGroupMessage(@DestinationVariable Long roomId,
                                            Principal principal,
                                            @Payload String content) {
        String sender = resolvePrincipalName(principal);
        validateContent(content);

        return messageService.sendGroupMessage(sender, roomId, content);
    }

    @MessageMapping("/channel/{channelId}")
    public ChannelMessageResponse sendChannelMessage(@DestinationVariable Long channelId,
                                                     Principal principal,
                                                     @Payload String content) {
        String sender = resolvePrincipalName(principal);
        validateContent(content);

        return messageService.sendChannelMessage(sender, channelId, content);
    }

	@MessageExceptionHandler()
	public void exceptionMessage(Exception ex, Principal principal, Message<?> message) {
		String username = principal != null ? principal.getName(): null;
		messageService.sendErrorMessage(username, ex.getMessage(), getStatusFromException(ex));
	}

    private String resolvePrincipalName(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AuthenticatedPrincipalRequiredException(
                    BusinessRuleMessage.BUSINESS_AUTHENTICATED_PRINCIPAL_REQUIRED_MESSAGE.getMessage());
        }
        return principal.getName();
    }

	private HttpStatus getStatusFromException(Exception ex) {
		ApiException apiException = ex.getClass().getAnnotation(ApiException.class);
		return apiException != null ? apiException.status(): HttpStatus.INTERNAL_SERVER_ERROR;  
	}

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content must not be empty");
        }
    }
}
