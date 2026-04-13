package com.zvonok.config;

import com.zvonok.security.CustomAuthenticationEntryPoint;
import com.zvonok.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.cors(cors -> cors.configurationSource(request -> {
					CorsConfiguration config = new CorsConfiguration();
					config.setAllowedOrigins(List.of("http://localhost:5173"));
					config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
					config.setAllowedHeaders(List.of("*"));
					config.setAllowCredentials(true);
					config.setMaxAge(3600L);
					return config;
				}))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(
						session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(
						ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))
				.addFilterBefore(jwtAuthenticationFilter,
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/login", "/auth/register", "/s3/**",
								"/auth/refresh", "/health", "/swagger-ui.html", "/swagger-ui/**",
								"/v3/api-docs/**", "/ws/**", "/ws-raw/**", "/ws-raw")
						.permitAll()
						.anyRequest().authenticated())
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
