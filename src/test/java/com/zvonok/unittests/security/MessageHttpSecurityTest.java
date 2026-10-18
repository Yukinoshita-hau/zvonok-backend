package com.zvonok.unittests.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import com.zvonok.config.SecurityConfig;
import com.zvonok.controller.MessageController;
import com.zvonok.exception_handler.GlobalExceptionHandler;
import com.zvonok.model.Message;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.MessageReadStatusRepository;
import com.zvonok.security.CustomAuthenticationEntryPoint;
import com.zvonok.security.JwtAuthenticationFilter;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production HTTP SecurityConfig, JWT filter, controller, DTO mapping and real access policies. */
class MessageHttpSecurityTest {

	AnnotationConfigWebApplicationContext context;
	MockMvc mvc;
	WebSocketAclSecurityTest fixture;
	Message message;
	String aliceToken;
	String bobToken;

	@BeforeEach void setup() {
		context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("security.allowedOrigins", "http://localhost", "app.jwt.ExpirationMs", "60000",
				"app.jwt.secret", java.util.Base64.getEncoder().encodeToString(new byte[64]))));
		context.register(Config.class); context.refresh();
		fixture = context.getBean(WebSocketAclSecurityTest.class);
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		aliceToken = "Bearer " + fixture.principal.getToken();
		bobToken = "Bearer " + fixture.tokens.generateToken("bob", 2L);
		var alice = fixture.users.getUser("alice"); alice.setEmail("private@example.com"); alice.setPassword("secret-hash");
		var bob = new com.zvonok.model.User(); bob.setId(2L); bob.setUsername("bob");
		when(fixture.users.getUser("bob")).thenReturn(bob);
		message = new Message(); message.setId(100L); message.setRoom(fixture.room); message.setSender(alice); message.setContent("hello");
		when(fixture.messages.findById(100L)).thenReturn(Optional.of(message));
	}

	@AfterEach void close() { context.close(); }

	@ParameterizedTest
	@ValueSource(strings = {"/servers/create", "/servers/my", "/servers/1", "/servers/join/old-code",
			"/servers/1/regenerate-invite", "/servers/1/leave", "/servers/1/members",
			"/server/1/roles/all", "/server/1/bans", "/server/1/members/1/roles/1",
			"/server/1/channel-folders", "/servers/1/channel-folders/1/channels/1",
			"/servers/1/channel-folders/1/channels/1/messages", "/servers/discover"})
	void retiredEndpointsHaveNoHttpHandler(String path) throws Exception {
		for (var method : List.of(org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.POST,
				org.springframework.http.HttpMethod.PUT, org.springframework.http.HttpMethod.DELETE)) {
			mvc.perform(request(method, path).header("Authorization", aliceToken)).andExpect(status().isNotFound());
		}
	}

	@Test void anonymousRequestRequiresAuthentication() throws Exception {
		mvc.perform(get("/messages/100")).andExpect(status().isUnauthorized());
	}
	@Test void authorizedMessageReturnsOnlyDtoFields() throws Exception {
		mvc.perform(get("/messages/100").header("Authorization", aliceToken)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(100)).andExpect(jsonPath("$.room.id").value(10))
				.andExpect(jsonPath("$.sender.username").value("alice"))
				.andExpect(jsonPath("$.sender.password").doesNotExist()).andExpect(jsonPath("$.sender.email").doesNotExist())
				.andExpect(jsonPath("$.room.members").doesNotExist());
	}
	@Test void strangerGetsSame404AsMissingMessage() throws Exception {
		String denied = mvc.perform(get("/messages/100").header("Authorization", bobToken)).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		String missing = mvc.perform(get("/messages/999").header("Authorization", bobToken)).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		assertEquals(missing, denied);
	}
	@Test void removedMemberGets404() throws Exception {
		fixture.room.setMembers(List.of());
		mvc.perform(get("/messages/100").header("Authorization", aliceToken)).andExpect(status().isNotFound());
	}

	@Test void deletedMessageGets404() throws Exception {
		message.setDeletedAt(LocalDateTime.now());
		mvc.perform(get("/messages/100").header("Authorization", aliceToken)).andExpect(status().isNotFound());
	}
	@Test void strangerCannotReadReadersViaHttp() throws Exception {
		mvc.perform(post("/messages/readers").header("Authorization", bobToken).contentType("application/json")
				.content("{\"messageIds\":[100]}")).andExpect(status().isNotFound());
	}
	@Test void emptyReadersBatchIsBadRequest() throws Exception {
		mvc.perform(post("/messages/readers").header("Authorization", aliceToken).contentType("application/json")
				.content("{\"messageIds\":[]}")).andExpect(status().isBadRequest());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	@Import({SecurityConfig.class, MessageController.class, GlobalExceptionHandler.class})
	static class Config {
		@Bean WebSocketAclSecurityTest fixture() { var f = new WebSocketAclSecurityTest(); f.setup(); return f; }
		@Bean JwtTokenProvider tokens(WebSocketAclSecurityTest f) { return f.tokens; }
		@Bean JwtAuthenticationFilter jwtFilter(JwtTokenProvider tokens) { return new JwtAuthenticationFilter(tokens); }
		@Bean ObjectMapper objectMapper() { return Jackson2ObjectMapperBuilder.json().build(); }
		@Bean CustomAuthenticationEntryPoint entry(ObjectMapper mapper) { return new CustomAuthenticationEntryPoint(mapper); }
		@Bean MessageReadStatusService statuses(WebSocketAclSecurityTest f) {
			return new MessageReadStatusService(mock(MessageReadStatusRepository.class), mock(SimpMessagingTemplate.class), f.messageAccess, f.users);
		}
		@Bean MessageService messages(WebSocketAclSecurityTest f) {
			return new MessageService(f.messages, f.messageAccess, mock(SimpMessagingTemplate.class), mock(RoomService.class),
					f.users, mock(MessageAttachmentRepository.class), mock(S3Service.class), mock(RoomReadStateService.class), mock(PlatformTransactionManager.class));
		}
	}
}
