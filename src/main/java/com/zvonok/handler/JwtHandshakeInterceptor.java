package com.zvonok.handler;

import com.zvonok.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtTokenProvider jwtTokenProvider;


	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

		String token = extractToken(request);

		if (token != null && !token.isEmpty() && jwtTokenProvider.isValidToken(token)) {
			String username = jwtTokenProvider.getUsername(token);
			attributes.put("username", username);
			attributes.put("token", token);
			return true;
		}

		response.setStatusCode(HttpStatus.UNAUTHORIZED);

		if (token == null || token.isEmpty()) {
			response.getHeaders().set("X-WS-Error", "TOKEN_MISSING");
			return false;
		}

		if (token != null && !jwtTokenProvider.isValidToken(token)) {
			response.getHeaders().set("X-WS-Error", "TOKEN_INVALID");
			return false;
		}
		
		return false;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {

		if (exception != null) {
			log.error("WebSocket handshake failed", exception);
		}
	}

	private String extractToken(ServerHttpRequest request) {
		// Способ 1: Из query параметра ?token=xxx
		URI uri = request.getURI();
		String query = uri.getQuery();
		if (query != null) {
			String[] params = query.split("&");
			for (String param : params) {
				if (param.startsWith("token=")) {
					if (!param.substring(6).isEmpty()) {
						return param.substring(6);
					}
				}
			}
		}

		// Способ 2: Из заголовка Authorization
		String authHeader = request.getHeaders().getFirst("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			if (!authHeader.substring(7).isEmpty()) {
				return authHeader.substring(7);
			}
		}

		// Способ 3: Из кастомного заголовка X-Auth-Token или там какой придумаем (на случай если
		// Authorization недоступен)
		String tokenHeader = request.getHeaders().getFirst("X-Auth-Token");
		if (tokenHeader != null && !tokenHeader.isEmpty()) {
			return tokenHeader;
		}

		return null;
	}
}
