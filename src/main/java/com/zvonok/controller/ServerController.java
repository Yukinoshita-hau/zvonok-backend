package com.zvonok.controller;

import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.request.CreateServerRequest;
import com.zvonok.service.dto.request.UpdateServerRequest;
import com.zvonok.service.dto.request.UpdateServerMemberNicknameRequest;
import com.zvonok.service.dto.response.ServerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.zvonok.service.dto.response.ServerMemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Серверный контроллер",
		description = "Контроллер отвечаюший за управление серверов и смежным функционалом")
@RestController
@RequestMapping("/server")
@RequiredArgsConstructor
public class ServerController {

	private final ServerService serverService;
	private final UserService userService;

	@Operation(summary = "Создаёт сервер",
			description = "Создание серверана на основе введённых данных")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = ServerApiDescriptions.SERVER_CREATE_SUCCESS),
			@ApiResponse(responseCode = "400", description = CommonApiDescriptions.VALIDATION_FAILED)

	})
	@PostMapping("/create")
	public ResponseEntity<ServerResponse> createServer(
			@Valid @RequestBody CreateServerRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ServerResponse response = serverService.createServer(request, userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Выдаёт список серверов пользовытеля",
			description = "Выдаёт список серверов пользователя")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_GET_MY_SUCCESS),})
	@GetMapping("/my")
	public ResponseEntity<List<ServerResponse>> getMyServers(
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		List<ServerResponse> servers = serverService.getUserServers(userId);
		return ResponseEntity.ok(servers);
	}

	@Operation(summary = "Получение данных сервера",
			description = "Возвращает сервер по идентификатору")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_GET_SUCCESS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND)})
	@GetMapping("/{serverId}")
	public ResponseEntity<ServerResponse> getServer(@PathVariable @Parameter(
			description = "Идентификатор сервера", example = "1", required = true) Long serverId) {
		// Long userId = getCurrentUserId(principal);

		// Проверяем доступ к серверу
		// serverService.hasAccessToServer(userId, serverId);

		ServerResponse server = serverService.getServerResponse(serverId);
		return ResponseEntity.ok(server);
	}

	@Operation(summary = "Присоединение по коду приглашения",
			description = "Присоединяет пользователя по коду приглашения")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses()
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_JOIN_BY_INVITE_CODE_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND)})
	@GetMapping("/join/{inviteCode}")
	public ResponseEntity<ServerResponse> joinServer(@PathVariable String inviteCode,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ServerResponse response = serverService.joinServerByInviteCode(inviteCode, userId);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Обновленние данных сервера",
			description = "Обновляет данные сервера если имееться достаточно прав доступа")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_UPDATE_SUCCESS),
			@ApiResponse(responseCode = "400",
					description = CommonApiDescriptions.VALIDATION_FAILED),
			@ApiResponse(responseCode = "403",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND),})
	@PutMapping("/{serverId}")
	public ResponseEntity<ServerResponse> updateServer(@PathVariable Long serverId,
			@Valid @RequestBody UpdateServerRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ServerResponse response = serverService.updateServer(serverId, request, userId);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Регенерация кода приглашения",
			description = "Регенерирует код приглашения заменяя придыдущий")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses()
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_REGENERATE_INVITE_CODE_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),})
	@GetMapping("/{serverId}/regenerate-invite")
	public ResponseEntity<Map<String, String>> regenerateInviteCode(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		String newInviteCode = serverService.regenerateInviteCode(serverId, userId);
		return ResponseEntity.ok(Map.of("inviteCode", newInviteCode));
	}

	@Operation(summary = "Выходит из сервера", description = "Производит выход из сервера")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses()
	@ApiResponses(value = {@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_LEAVE_SUCCESS)})
	@GetMapping("/{serverId}/leave")
	public ResponseEntity<Void> leaveServer(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		serverService.leaveServer(serverId, userId);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Выдаёт список всех участников сервера",
			description = "Выдаёт список участников сервера с учётм того что вы являетесь участиком этого сервера")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_GET_MEMBERS_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND)})
	@GetMapping("/{serverId}/members")
	public ResponseEntity<List<ServerMemberResponse>> getServerMembers(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		List<ServerMemberResponse> members = serverService.getServerMembers(serverId, userId);
		return ResponseEntity.ok(members);
	}

	@Operation(summary = "выгоняет пользователя", description = "выгоняет пользователь из сервера")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = ServerApiDescriptions.SERVER_KICK_MEMBER_SUCCESS),
			@ApiResponse(responseCode = "401",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404", description = ServerApiDescriptions.SERVER_NOT_FOUND),
			@ApiResponse(responseCode = "409",
					description = ServerApiDescriptions.SERVER_MEMBER_ALREADY_KICKED)})
	@DeleteMapping("/{serverId}/members/{targetUserId}")
	public ResponseEntity<Void> kickMember(@PathVariable Long serverId,
			@PathVariable Long targetUserId, @AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		serverService.kickMember(serverId, targetUserId, userId);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Изменяет имя на сервере",
			description = "Изменение имени участника на сервере")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_UPDATE_MEMBER_NICKNAME_SUCCESS),
		@ApiResponse(responseCode = "400", description = CommonApiDescriptions.VALIDATION_FAILED),
		@ApiResponse(responseCode = "404", description = ServerApiDescriptions.SERVER_OR_MEMBER_NOT_FOUND)
	})
	@PatchMapping("/{serverId}/members/{targetUserId}/nickname")
	public ResponseEntity<ServerMemberResponse> updateMemberNickname(@PathVariable Long serverId,
			@PathVariable Long targetUserId,
			@Valid @RequestBody UpdateServerMemberNicknameRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ServerMemberResponse response = serverService.updateMemberNickname(serverId, targetUserId,
				request.getNickname(), userId);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Удаляет сервер", description = "Удаление данных сервера")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = ServerApiDescriptions.SERVER_DELETE_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = ServerApiDescriptions.SERVER_NOT_ENOUGH_RIGHTS),})
	@DeleteMapping("/{serverId}")
	public ResponseEntity<Void> deleteServer(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		serverService.deleteServer(serverId, userId);
		return ResponseEntity.noContent().build();
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}
}
