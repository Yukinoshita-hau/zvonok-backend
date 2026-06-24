package com.zvonok.handler;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception.InvalidJwtException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.dto.UserPrincipal;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

	private static final Pattern CANVAS_TOPIC_PATTERN =
			Pattern.compile("^/topic/calls/(\\d+)/boards(?:/\\d+)?$");
	private static final Set<CallParticipantStatus> CANVAS_SUBSCRIBE_ALLOWED_STATUSES =
			Set.of(CallParticipantStatus.ACCEPTED, CallParticipantStatus.JOINED);

	private final JwtTokenProvider jwtTokenProvider;
	private final CallParticipantRepository callParticipantRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
		Principal principal = accessor.getUser();

		if (accessor.getCommand() == null) {
			return message;
		}

		switch (accessor.getCommand()) {
			case CONNECT, DISCONNECT:
				return message;
			default:
				if (principal == null) {
					throw new AuthenticatedPrincipalRequiredException(
							HttpResponseMessage.HTTP_AUTH_PRINCEPAL_REQUIRED_RESPONSE_MESSAGE
									.getMessage());
				}

				if (principal instanceof UserPrincipal userPrincipal) {
					if (!jwtTokenProvider.isValidToken(userPrincipal.getToken())) {
						throw new InvalidJwtException(
								HttpResponseMessage.HTTP_INVALID_JWT_RESPONSE_MESSAGE.getMessage());
					}
				}
		}

		if (accessor.getCommand() == StompCommand.SUBSCRIBE && principal != null) {
			validateCanvasSubscription(accessor, principal.getName());
		}

		return message;
	}

	private void validateCanvasSubscription(StompHeaderAccessor accessor, String username) {
		String destination = accessor.getDestination();
		if (destination == null) {
			return;
		}

		Matcher matcher = CANVAS_TOPIC_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return;
		}

		Long callId = Long.parseLong(matcher.group(1));
		boolean allowed = callParticipantRepository
				.existsByCallSessionIdAndUserUsernameAndStatusIn(callId, username,
						CANVAS_SUBSCRIBE_ALLOWED_STATUSES);
		if (!allowed) {
			throw new InsufficientPermissionsException("User is not active call participant");
		}
	}

}
