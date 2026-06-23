package com.zvonok.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zvonok.exception.ConferenceEndException;
import com.zvonok.exception.ConferenceNotFoundException;
import com.zvonok.exception.OnlyHostCanEndConferenceException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Conference;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.ConferenceStatus;
import com.zvonok.repository.ConferenceRepository;
import com.zvonok.service.dto.ConferenceCreateResponse;
import com.zvonok.service.dto.ConferenceJoinResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ConferenceService {

	private final ConferenceRepository conferenceRepository;
	private final UserService userService;
	private final LiveKitRoomAdminService liveKitRoomAdminService;
	private final LiveKitTokenService liveKitTokenService;

	@Value("${app.public-url}")
	private String publicUrl;

	@Value("${livekit.server-url}")
	private String livekitServerUrl;

	@Transactional
	public ConferenceCreateResponse createConference(String username) {
		User host = userService.getUser(username);

		String code = generateUniqueCode();
		String livekitRoomName = "conf-" + code;

		liveKitRoomAdminService.ensureRoomReady(livekitRoomName);

		Conference conference = Conference.builder().code(code).livekitRoomName(livekitRoomName)
				.host(host).status(ConferenceStatus.ACTIVE).createdAt(LocalDateTime.now()).build();

		conferenceRepository.save(conference);

		String token = liveKitTokenService
				.generateCallToken(livekitRoomName, host.getUsername(), host.getDisplayName(), null)
				.getParticipantToken();

		return ConferenceCreateResponse.builder().conferenceId(conference.getId()).code(code)
				.joinUrl(publicUrl + "/conference/" + code).livekitRoomName(livekitRoomName)
				.serverUrl(livekitServerUrl).token(token).build();
	}

	@Transactional(readOnly = true)
	public ConferenceJoinResponse joinConference(String code, String username) {
		User user = userService.getUser(username);

		Conference conference = conferenceRepository.findByCode(code)
				.orElseThrow(() -> new ConferenceNotFoundException(
						HttpResponseMessage.HTTP_CONFERENCE_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		if (conference.getStatus() != ConferenceStatus.ACTIVE) {
			throw new ConferenceEndException(
					HttpResponseMessage.HTTP_CONFERENCE_END_RESPONSE_MESSAGE.getMessage());
		}

		String token = liveKitTokenService.generateCallToken(conference.getLivekitRoomName(),
				user.getUsername(), user.getDisplayName(), null).getParticipantToken();

		return ConferenceJoinResponse.builder().conferenceId(conference.getId())
				.code(conference.getCode()).livekitRoomName(conference.getLivekitRoomName())
				.serverUrl(livekitServerUrl).token(token).build();
	}

	@Transactional
	public void endConference(String code, String username) {
		User user = userService.getUser(username);

		Conference conference = conferenceRepository.findByCode(code)
				.orElseThrow(() -> new ConferenceNotFoundException(
						HttpResponseMessage.HTTP_CONFERENCE_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		if (!conference.getHost().getId().equals(user.getId())) {
			throw new OnlyHostCanEndConferenceException(
					HttpResponseMessage.HTTP_ONLY_HOST_CAN_END_CONFERENCE_RESPONSE_MESSAGE
							.getMessage());
		}

		if (conference.getStatus() != ConferenceStatus.ACTIVE) {
			throw new ConferenceEndException(
					HttpResponseMessage.HTTP_CONFERENCE_END_RESPONSE_MESSAGE.getMessage());
		}

		conference.setStatus(ConferenceStatus.ENDED);
		conference.setEndedAt(LocalDateTime.now());

		conferenceRepository.save(conference);

		liveKitRoomAdminService.deleteRoom(conference.getLivekitRoomName());
	}

	private String generateUniqueCode() {
		String code;

		do {
			code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		} while (conferenceRepository.existsByCode(code));

		return code;
	}
}
