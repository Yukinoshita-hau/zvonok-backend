package com.zvonok.handler;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import com.zvonok.exception.AuthenticatedPrincipalRequiredException;
import com.zvonok.exception.InvalidJwtException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.security.dto.UserPrincipal;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

	private final JwtTokenProvider jwtTokenProvider;

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

		return message;
	}

}
