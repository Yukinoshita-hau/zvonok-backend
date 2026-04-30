package com.zvonok.controller;

import com.zvonok.controller.dto.ActiveCallResponse;
import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.controller.dto.RoomResponse;
import com.zvonok.controller.dto.ShortMessageWrapped;
import com.zvonok.documentation.RoomApiDescriptions;
import com.zvonok.documentation.UserApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.service.dto.CreateGroupDto;
import com.zvonok.service.dto.UpdateRoomDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.zvonok.model.Room;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.RoomService;
import com.zvonok.service.CallSessionService;
import com.zvonok.service.MessageService;
import com.zvonok.service.RoomReadStateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Контроллер комнат",
		description = "Эндпоинты для работы с комнатами (получение, создание групповой, обновление, удаление) "
				+ "и для получения сообщений приватного диалога.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomService roomService;
	private final MessageService messageService;
	private final RoomReadStateService roomReadStateService;
	private final CallSessionService callSessionService;

	@Operation(summary = "Получить все комнаты пользователя",
			description = "Возвращает все комнаты пользователя")
	@GetMapping("/all")
	public ResponseEntity<List<RoomResponse>> getAllUserRooms(
			@AuthenticationPrincipal UserPrincipal principal) {
		String username = principal.getUsername();
		return ResponseEntity.ok(roomService.getUserRoomsWithUnread(username));
	}

	@Operation(summary = "Получить комнату по id",
			description = "Возвращает комнату по её идентификатору.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = RoomApiDescriptions.ROOM_GET_SUCCESS)
	@ApiResponse404(description = RoomApiDescriptions.ROOM_NOT_FOUND)
	@GetMapping("/{roomId}")
	public ResponseEntity<Room> getRoomByID(@PathVariable Long roomId,
			@AuthenticationPrincipal UserPrincipal principal) {
		String username = principal.getName();
		return ResponseEntity.ok(roomService.getRoom(roomId, username));
	}

	@Operation(summary = "Создать групповую комнату",
			description = "Создаёт групповую комнату от имени текущего пользователя с указанным названием и списком участников. "
					+ "Возвращает созданную комнату.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = RoomApiDescriptions.ROOM_GET_SUCCESS)
	@ApiResponse400
	@ApiResponse404(description = UserApiDescriptions.USER_NOT_FOUND)
	@PostMapping("/createGroup")
	public ResponseEntity<Room> createGroupRoom(@Valid @RequestBody CreateGroupDto groupDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createGroupRoom(
				principal.getName(), groupDto.getRoomName(), groupDto.getRoomMemberUsernames()));
	}

	@Operation(summary = "Обновить комнату",
			description = "Обновляет параметры комнаты (например, название) по id от имени текущего пользователя. "
					+ "Возвращает обновлённую комнату.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = RoomApiDescriptions.ROOM_UPDATE_SUCCESS)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = RoomApiDescriptions.ROOM_NOT_FOUND)
	@PutMapping("/{id}")
	public ResponseEntity<Void> updateRoom(@PathVariable Long id,
			@Valid @RequestBody UpdateRoomDto roomDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		roomService.updateRoom(id, principal.getName(), roomDto.getName(), null, null, null);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Удалить комнату",
			description = "Удаляет комнату по id от имени текущего пользователя.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204", description = RoomApiDescriptions.ROOM_DELETE_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = RoomApiDescriptions.ROOM_NOT_FOUND)
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteRoom(@PathVariable Long id,
			@AuthenticationPrincipal UserPrincipal principal) {
		roomService.deleteRoom(id, principal.getName());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Получить приватные сообщения",
			description = "Возвращает список сообщений приватного диалога между текущим пользователем и пользователем friendId.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = RoomApiDescriptions.ROOM_GET_PRIVATE_MESSAGES_SUCCESS)
	@ApiResponse404(description = UserApiDescriptions.USER_NOT_FOUND)
	@GetMapping("/private/{userId}/messages")
	public ResponseEntity<List<MessageResponse>> getPrivateMessages(@PathVariable Long userId,
			@AuthenticationPrincipal UserPrincipal principal) {
		List<MessageResponse> messages =
				messageService.getPrivateMessages(principal.getUsername(), userId);
		return ResponseEntity.ok(messages);
	}

	@GetMapping("/{roomId}/messages")
	public ResponseEntity<List<ShortMessageWrapped>> getRoomMessages(@PathVariable Long roomId,
			@RequestParam(required = false) Long beforeMessageId,
			@RequestParam(defaultValue = "15") int limit,
			@AuthenticationPrincipal UserPrincipal principal) {
		List<ShortMessageWrapped> messages = messageService.getRoomMessages(principal.getUsername(),
				roomId, beforeMessageId, limit);

		return ResponseEntity.ok(messages);
	}

	@PostMapping("/{roomId}/read")
	public ResponseEntity<Void> markRoomRead(@PathVariable Long roomId,
			@AuthenticationPrincipal UserPrincipal principal) {
		roomReadStateService.markRoomAsRead(principal.getUsername(), roomId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{roomId}/active-call")
	public ResponseEntity<ActiveCallResponse> getActiveCall(@PathVariable Long roomId,
			@AuthenticationPrincipal UserPrincipal principal) {
		ActiveCallResponse response = callSessionService.findActiveCallResponse(roomId, principal.getUsername());
		if (response == null) {
			return ResponseEntity.noContent().build();
		} else {
			return ResponseEntity.ok(response);
		}
	}
}
