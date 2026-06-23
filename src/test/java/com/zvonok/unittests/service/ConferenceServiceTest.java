package com.zvonok.unittests.service;

import com.zvonok.exception.ConferenceEndException;
import com.zvonok.exception.ConferenceNotFoundException;
import com.zvonok.exception.OnlyHostCanEndConferenceException;
import com.zvonok.model.Conference;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.ConferenceStatus;
import com.zvonok.repository.ConferenceRepository;
import com.zvonok.service.ConferenceService;
import com.zvonok.service.LiveKitRoomAdminService;
import com.zvonok.service.LiveKitTokenService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.ConferenceCreateResponse;
import com.zvonok.service.dto.ConferenceJoinResponse;
import com.zvonok.service.dto.LiveKitTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenceServiceTest {

	@Mock
	private ConferenceRepository conferenceRepository;
	@Mock
	private UserService userService;
	@Mock
	private LiveKitRoomAdminService liveKitRoomAdminService;
	@Mock
	private LiveKitTokenService liveKitTokenService;

	@InjectMocks
	private ConferenceService conferenceService;

	private User host;
	private User guest;

	@BeforeEach
	void setUp() {
		host = user(1L, "host", "Host");
		guest = user(2L, "guest", "Guest");

		ReflectionTestUtils.setField(conferenceService, "publicUrl", "https://zvonok.example");
		ReflectionTestUtils.setField(conferenceService, "livekitServerUrl", "wss://livekit.example");
	}

	@Test
	void createConference_shouldCreateLiveKitRoomAndReturnTokenPayload() {
		when(userService.getUser(host.getUsername())).thenReturn(host);
		when(conferenceRepository.existsByCode(any())).thenReturn(false);
		when(conferenceRepository.save(any(Conference.class))).thenAnswer(invocation -> {
			Conference conference = invocation.getArgument(0);
			conference.setId(10L);
			return conference;
		});
		when(liveKitTokenService.generateCallToken(any(), eq(host.getUsername()),
				eq(host.getDisplayName()), eq(null))).thenReturn(token("host-token"));

		ConferenceCreateResponse response = conferenceService.createConference(host.getUsername());

		assertEquals(10L, response.getConferenceId());
		assertNotNull(response.getCode());
		assertEquals("https://zvonok.example/conference/" + response.getCode(), response.getJoinUrl());
		assertEquals("conf-" + response.getCode(), response.getLivekitRoomName());
		assertEquals("wss://livekit.example", response.getServerUrl());
		assertEquals("host-token", response.getToken());
		verify(liveKitRoomAdminService).ensureRoomReady("conf-" + response.getCode());
	}

	@Test
	void joinConference_shouldReturnLiveKitTokenForActiveConference() {
		Conference conference = conference("abc12345", host, ConferenceStatus.ACTIVE);
		conference.setId(11L);

		when(userService.getUser(guest.getUsername())).thenReturn(guest);
		when(conferenceRepository.findByCode("abc12345")).thenReturn(Optional.of(conference));
		when(liveKitTokenService.generateCallToken("conf-abc12345", guest.getUsername(),
				guest.getDisplayName(), null)).thenReturn(token("guest-token"));

		ConferenceJoinResponse response = conferenceService.joinConference("abc12345", guest.getUsername());

		assertEquals(11L, response.getConferenceId());
		assertEquals("abc12345", response.getCode());
		assertEquals("conf-abc12345", response.getLivekitRoomName());
		assertEquals("wss://livekit.example", response.getServerUrl());
		assertEquals("guest-token", response.getToken());
	}

	@Test
	void joinConference_shouldRejectEndedConference() {
		when(userService.getUser(guest.getUsername())).thenReturn(guest);
		when(conferenceRepository.findByCode("abc12345"))
				.thenReturn(Optional.of(conference("abc12345", host, ConferenceStatus.ENDED)));

		assertThrows(ConferenceEndException.class,
				() -> conferenceService.joinConference("abc12345", guest.getUsername()));
		verify(liveKitTokenService, never()).generateCallToken(any(), any(), any(), any());
	}

	@Test
	void joinConference_shouldThrowWhenConferenceNotFound() {
		when(userService.getUser(guest.getUsername())).thenReturn(guest);
		when(conferenceRepository.findByCode("missing")).thenReturn(Optional.empty());

		assertThrows(ConferenceNotFoundException.class,
				() -> conferenceService.joinConference("missing", guest.getUsername()));
	}

	@Test
	void endConference_shouldAllowHostAndDeleteLiveKitRoom() {
		Conference conference = conference("abc12345", host, ConferenceStatus.ACTIVE);

		when(userService.getUser(host.getUsername())).thenReturn(host);
		when(conferenceRepository.findByCode("abc12345")).thenReturn(Optional.of(conference));

		conferenceService.endConference("abc12345", host.getUsername());

		assertEquals(ConferenceStatus.ENDED, conference.getStatus());
		assertNotNull(conference.getEndedAt());
		verify(conferenceRepository).save(conference);
		verify(liveKitRoomAdminService).deleteRoom("conf-abc12345");
	}

	@Test
	void endConference_shouldRejectNonHost() {
		when(userService.getUser(guest.getUsername())).thenReturn(guest);
		when(conferenceRepository.findByCode("abc12345"))
				.thenReturn(Optional.of(conference("abc12345", host, ConferenceStatus.ACTIVE)));

		assertThrows(OnlyHostCanEndConferenceException.class,
				() -> conferenceService.endConference("abc12345", guest.getUsername()));
		verify(liveKitRoomAdminService, never()).deleteRoom(any());
	}

	private User user(Long id, String username, String displayName) {
		User user = new User();
		user.setId(id);
		user.setUsername(username);
		user.setDisplayName(displayName);
		return user;
	}

	private Conference conference(String code, User host, ConferenceStatus status) {
		return Conference.builder()
				.id(20L)
				.code(code)
				.livekitRoomName("conf-" + code)
				.host(host)
				.status(status)
				.build();
	}

	private LiveKitTokenResponse token(String value) {
		LiveKitTokenResponse response = new LiveKitTokenResponse();
		response.setServerUrl("wss://livekit.example");
		response.setParticipantToken(value);
		return response;
	}
}
