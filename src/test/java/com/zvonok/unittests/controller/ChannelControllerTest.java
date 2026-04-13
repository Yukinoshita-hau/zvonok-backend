package com.zvonok.unittests.controller;

import com.zvonok.controller.ChannelController;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.GlobalExceptionHandler;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.security.CustomAuthenticationEntryPoint;
import com.zvonok.security.JwtAuthenticationFilter;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ChannelFolderService;
import com.zvonok.service.ChannelService;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChannelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ChannelControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChannelService channelService;

	@MockitoBean
	private ChannelFolderService channelFolderService;

	@MockitoBean
	private PermissionService permissionService;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private ServerService serverService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

	@BeforeEach
	void setUp() {
		UserPrincipal principal = new UserPrincipal("alice", "token");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
		when(serverService.getServer(1L)).thenReturn(null);
		when(channelFolderService.getChannelFolderForServer(1L, 2L)).thenReturn(null);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("getChannel - returns channel when channel-level access is allowed")
	void getChannelShouldReturnChannelWhenUserCanViewIt() throws Exception {
		Channel channel = new Channel();
		channel.setId(7L);
		channel.setName("general");
		when(channelService.getChannel("alice", 2L, 7L)).thenReturn(channel);

		mockMvc.perform(get("/server/1/channel-folders/2/channels/7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7L))
				.andExpect(jsonPath("$.name").value("general"));

		verify(permissionService, never()).canUserViewFolder(anyLong(), anyLong());
	}

	@Test
	@DisplayName("getChannel - returns 403 when channel-level access is denied")
	void getChannelShouldReturnForbiddenWhenChannelAccessDenied() throws Exception {
		when(channelService.getChannel("alice", 2L, 7L)).thenThrow(
				new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage()));

		mockMvc.perform(get("/server/1/channel-folders/2/channels/7"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage()))
				.andExpect(jsonPath("$.status").value(403));

		verify(permissionService, never()).canUserViewFolder(anyLong(), anyLong());
	}

	@Test
	@DisplayName("getChannel - returns 404 when channel does not belong to folder from route")
	void getChannelShouldReturnNotFoundWhenChannelDoesNotBelongToFolder() throws Exception {
		when(channelService.getChannel("alice", 2L, 7L)).thenThrow(
				new ChannelNotFoundException(
						HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));

		mockMvc.perform(get("/server/1/channel-folders/2/channels/7"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(
						HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()))
				.andExpect(jsonPath("$.status").value(404));
	}
}
