package com.zvonok.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.zvonok.exception.LiveKitRoomSyncException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LiveKitRoomAdminService {

	private final RoomServiceClient roomServiceClient;

	public LiveKitRoomAdminService(
		@Value("${livekit.api-key}") String apiKey,
		@Value("${livekit.api-secret}") String apiSecret,
		@Value("${livekit.api-url}") String livekitUrl
			) {
		this.roomServiceClient = RoomServiceClient.createClient(livekitUrl, apiKey, apiSecret);
	}

	public Optional<LivekitModels.Room> getRoom(String roomName) {
		try {
			List<LivekitModels.Room> rooms = roomServiceClient.listRooms(List.of(roomName)).execute().body();
			if (rooms.isEmpty()) {
				return Optional.empty();
			}
			return Optional.ofNullable(rooms.getFirst());
		} catch (IOException e) {
            throw temporaryError(e.getMessage(), roomName);
        }
    }

	public boolean deleteRoom(String roomName) {
		try {
			roomServiceClient.deleteRoom(roomName).execute().body();
			return true;
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public List<LivekitModels.ParticipantInfo> listParticipants(String roomName) {
		try {
			return roomServiceClient.listParticipants(roomName).execute().body();
		} catch (IOException e) {
			throw temporaryError(e.getMessage(), roomName);
		}
	}

	public int countParticipants(String roomName) {
		return listParticipants(roomName).size();
	}

	public boolean roomExist(String roomName) {
		return getRoom(roomName).isPresent();
	}

	private LiveKitRoomSyncException temporaryError(String message, String roomName) {
		log.warn("Livekit temporary error: {} room={}", message, roomName);
		return new LiveKitRoomSyncException(message + ": " + roomName);
	}

	private boolean isRoomNotFound(ExecutionException e) {
		String message = e.getCause() != null ? e.getCause().getMessage(): e.getMessage();
		if (message == null) {
			return false;
		}
		String lower = message.toLowerCase();
		return lower.contains("not found") || lower.contains("room does not exist");
	}
}
