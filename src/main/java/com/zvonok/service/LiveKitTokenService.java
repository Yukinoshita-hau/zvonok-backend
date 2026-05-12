package com.zvonok.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.zvonok.exception.LiveKitTokenGenerateException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.service.dto.LiveKitTokenResponse;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitTokenService {
	@Value("${livekit.api-key}")
	private String apiKey;

	@Value("${livekit.api-secret}")
	private String apiSecret;

	@Value("${livekit.server-url}")
	private String serverUrl;

	@Value("${livekit.token-ttl-minutes:10}")
	private int tokenTtlMinutes;

	@Deprecated
	public LiveKitTokenResponse generateToken(String roomName, String identity,
			String displayName) {
		return generateCallToken(roomName, identity, displayName, null);
	}

	public LiveKitTokenResponse generateCallToken(String roomName, String identity,
			String displayName, Long callId) {
		try {
			AccessToken token = new AccessToken(apiKey, apiSecret);
			token.setIdentity(identity);
			token.setName(displayName);
			token.setTtl(tokenTtlMinutes * 60L);

			token.addGrants(new RoomJoin(true));
			token.addGrants(new RoomName(roomName));
			token.addGrants(new CanPublish(true));
			token.addGrants(new CanSubscribe(true));
			token.addGrants(new CanPublishData(true));

			String jwt = token.toJwt();

			LiveKitTokenResponse response = new LiveKitTokenResponse();
			response.setServerUrl(serverUrl);
			response.setParticipantToken(jwt);
			response.setCallId(callId);
			response.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));

			return response;
		} catch (Exception e) {
			log.error("Failed to generate LiveKit token, room={}, identity={}, callId={}", roomName, identity, callId, e);		

			throw new LiveKitTokenGenerateException(
					HttpResponseMessage.HTTP_LIVEKIT_TOKEN_GENERATE_ERROR_RESPONSE_MESSAGE
							.getMessage());

		}
	}
}
