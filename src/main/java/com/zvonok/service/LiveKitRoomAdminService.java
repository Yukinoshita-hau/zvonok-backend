package com.zvonok.service;

import com.zvonok.exception.LiveKitRoomNotFoundException;
import com.zvonok.exception.LiveKitRoomSyncException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class LiveKitRoomAdminService {

	private static final int ROOM_READY_ATTEMPTS = 3;
	private static final Duration ROOM_READY_DELAY = Duration.ofMillis(150);

	private final RoomServiceClient roomServiceClient;

	public LiveKitRoomAdminService(@Value("${livekit.api-key}") String apiKey,
			@Value("${livekit.api-secret}") String apiSecret,
			@Value("${livekit.api-url}") String livekitUrl) {
		this.roomServiceClient = RoomServiceClient.createClient(livekitUrl, apiKey, apiSecret);
	}

	public LivekitModels.Room ensureRoomReady(String roomName) {
		Optional<LivekitModels.Room> existingRoom = getRoom(roomName);

		if (existingRoom.isPresent()) {
			return existingRoom.get();
		}

		try {
			LivekitModels.Room createdRoom = createRoom(roomName);

			if (createdRoom != null && roomName.equals(createdRoom.getName())) {
				return createdRoom;
			}

			return waitUntilRoomVisible(roomName);
		} catch (LiveKitRoomSyncException e) {
			if (isAlreadyExistsMessage(e.getMessage())) {
				return waitUntilRoomVisible(roomName);
			}

			throw e;
		}
	}

	public LivekitModels.Room requireExistingRoom(String roomName) {
		return getRoom(roomName).orElseThrow(() -> new LiveKitRoomNotFoundException(
				HttpResponseMessage.HTTP_LIVEKIT_ROOM_NOT_FOUND_RESPONSE_MESSAGE.getMessage() + ": " + roomName));
	}

	public Optional<LivekitModels.Room> getRoom(String roomName) {
		try {
			Response<List<LivekitModels.Room>> response =
					roomServiceClient.listRooms(List.of(roomName)).execute();

			if (!response.isSuccessful()) {
				String errorBody = readErrorBody(response);
				throw temporaryError("Failed to list LiveKit room. status=" + response.code()
						+ ", error=" + errorBody, roomName);
			}

			List<LivekitModels.Room> rooms = response.body();

			if (rooms == null || rooms.isEmpty()) {
				return Optional.empty();
			}

			return Optional.of(rooms.get(0));
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public LivekitModels.Room createRoom(String roomName) {
		try {
			Response<LivekitModels.Room> response =
					roomServiceClient.createRoom(roomName).execute();

			if (!response.isSuccessful()) {
				String errorBody = readErrorBody(response);

				if (isAlreadyExistsMessage(errorBody)) {
					log.info("LiveKit room already exists: room={}", roomName);
					return waitUntilRoomVisible(roomName);
				}

				throw temporaryError("Failed to create LiveKit room. status=" + response.code()
						+ ", error=" + errorBody, roomName);
			}

			LivekitModels.Room room = response.body();

			if (room == null) {
				throw temporaryError("LiveKit createRoom returned empty body", roomName);
			}

			log.info("LiveKit room created: room={}", roomName);
			return room;
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public boolean deleteRoom(String roomName) {
		try {
			Response<Void> response = roomServiceClient.deleteRoom(roomName).execute();

			if (!response.isSuccessful()) {
				String errorBody = readErrorBody(response);

				if (isRoomNotFoundMessage(errorBody)) {
					log.info("LiveKit room already deleted: room={}", roomName);
					return false;
				}

				throw temporaryError("Failed to delete LiveKit room. status=" + response.code()
						+ ", error=" + errorBody, roomName);
			}

			return true;
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public List<LivekitModels.ParticipantInfo> listParticipants(String roomName) {
		try {
			Response<List<LivekitModels.ParticipantInfo>> response =
					roomServiceClient.listParticipants(roomName).execute();

			if (!response.isSuccessful()) {
				String errorBody = readErrorBody(response);

				if (isRoomNotFoundMessage(errorBody)) {
					return Collections.emptyList();
				}

				throw temporaryError("Failed to list LiveKit participants. status="
						+ response.code() + ", error=" + errorBody, roomName);
			}

			List<LivekitModels.ParticipantInfo> participants = response.body();

			if (participants == null) {
				return Collections.emptyList();
			}

			return participants;
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public int countParticipants(String roomName) {
		return listParticipants(roomName).size();
	}

	public boolean roomExists(String roomName) {
		return getRoom(roomName).isPresent();
	}

	private LivekitModels.Room waitUntilRoomVisible(String roomName) {
		for (int attempt = 1; attempt <= ROOM_READY_ATTEMPTS; attempt++) {
			Optional<LivekitModels.Room> room = getRoom(roomName);

			if (room.isPresent()) {
				return room.get();
			}

			sleepBeforeRetry(roomName, attempt);
		}

		throw temporaryError("LiveKit room was created but is not visible after retries", roomName);
	}

	private void sleepBeforeRetry(String roomName, int attempt) {
		try {
			Thread.sleep(ROOM_READY_DELAY.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw temporaryError("Interrupted while waiting for LiveKit room. attempt=" + attempt,
					roomName);
		}
	}

	private String readErrorBody(Response<?> response) {
		ResponseBody errorBody = response.errorBody();

		if (errorBody == null) {
			return "";
		}

		try {
			return errorBody.string();
		} catch (IOException e) {
			return "Unable to read error body: " + e.getMessage();
		}
	}

	private boolean isAlreadyExistsMessage(String message) {
		if (message == null) {
			return false;
		}

		String lower = message.toLowerCase(Locale.ROOT);

		return lower.contains("already exists") || lower.contains("already_exists")
				|| lower.contains("room already exists");
	}

	private boolean isRoomNotFoundMessage(String message) {
		if (message == null) {
			return false;
		}

		String lower = message.toLowerCase(Locale.ROOT);

		return lower.contains("not found") || lower.contains("not_found")
				|| lower.contains("room does not exist") || lower.contains("could not find object");
	}

	private LiveKitRoomSyncException temporaryError(String message, String roomName) {
		log.warn("LiveKit room sync error: {} room={}", message, roomName);
		return new LiveKitRoomSyncException(message + ": " + roomName);
	}
}
