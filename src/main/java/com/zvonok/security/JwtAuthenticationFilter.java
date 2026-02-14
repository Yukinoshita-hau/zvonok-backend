package com.zvonok.security;

import com.zvonok.logging.LogTimingUtils;
import com.zvonok.security.dto.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final List<String> banWords = new ArrayList<>(
			Arrays.asList("password", "pass", "pwd", "token", "access_token", "refresh_token", "auth_token",
					"authorization", "secret", "client_secret", "api_key", "key"));

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		long durationStart = System.currentTimeMillis();

		String method = request.getMethod();
		String path = request.getRequestURI();

		StringBuilder safeQuery = new StringBuilder();

		for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
			String name = parameter.getKey();
			String[] values = parameter.getValue();
			boolean first = true;

			safeQuery.append(name).append("=");

			if (banWords.contains(name.toLowerCase())) {
				safeQuery.append("[***]");
				continue;
			}

			safeQuery.append("[");

			for (String value : values) {
				if (first) {
					safeQuery.append(value);
					first = false;
				} else {
					safeQuery.append(",").append(" ").append(value);
				}
			}
			safeQuery.append("]");
		}

		String jwt = getJwtFromRequest(request);

		if (jwt != null && jwtTokenProvider.isValidToken(jwt)) {
			String username = jwtTokenProvider.getUsername(jwt);
			UserPrincipal principal = new UserPrincipal(username, jwt);

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
					null, Collections.emptyList());

			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		filterChain.doFilter(request, response);
		int status = response.getStatus();
		log.info("HTTP {} {}?{} status={} duration={}ms",
				method, path, safeQuery, status, LogTimingUtils.calculateDurationDifference(durationStart));
	}

	private String getJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}
}
