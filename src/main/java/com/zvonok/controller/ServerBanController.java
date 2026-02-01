package com.zvonok.controller;

import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.ServerBanApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.exception.CannotBanServerOwnerException;
import com.zvonok.exception.CannotBanYourselfException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.model.Server;
import com.zvonok.model.ServerBan;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerBanService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.request.CreateServerBanRequest;
import com.zvonok.service.dto.response.ServerBanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST-эндпоинты для управления банами пользователей на сервере.
 */
@Tag(name = "Контроллер банов сервера",
		description = "Эндпоинты для просмотра активных банов, выдачи бана и снятия бана на сервере. "
				+ "При бане действуют бизнес-правила: нельзя забанить себя и владельца сервера.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/server/{serverId}/bans")
@RequiredArgsConstructor
public class ServerBanController {

	private final ServerBanService serverBanService;
	private final PermissionService permissionService;
	private final ServerService serverService;
	private final UserService userService;

	@Operation(summary = "Список банов сервера",
			description = "Возвращает активные баны на сервере. " + "Сервер должен существовать.")
	@SecuredApiResponses
	@ApiResponse403(message = CommonApiDescriptions.NOT_ENOUGH_RIGHTS)
	@ApiResponse404(message = ServerApiDescriptions.SERVER_NOT_FOUND)
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = ServerBanApiDescriptions.SERVER_BAN_GET_LIST_SUCCESS),
		@ApiResponse(responseCode = "403", description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS),
		@ApiResponse(responseCode = "404", description = ServerApiDescriptions.SERVER_NOT_FOUND),
	})
	@GetMapping
	public ResponseEntity<List<ServerBanResponse>> getServerBans(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ensureServerExists(serverId);
		ensureCanBanMembers(userId, serverId);

		List<ServerBan> bans = serverBanService.getActiveBans(serverId);
		List<ServerBanResponse> responses =
				bans.stream().map(this::mapToResponse).collect(Collectors.toList());
		return ResponseEntity.ok(responses);
	}

	@Operation(summary = "Забанить пользователя",
			description = "Создаёт бан пользователя на сервере с причиной и опциональной датой окончания. "
					+ "Сервер должен существовать. "
					+ "Нельзя забанить самого себя и владельца сервера.")
	@SecuredApiResponses
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = ServerBanApiDescriptions.SERVER_BAN_USER_SUCCES),
		@ApiResponse(responseCode = "400", description = CommonApiDescriptions.VALIDATION_FAILED),
		@ApiResponse(responseCode = "403", description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS),
		@ApiResponse(responseCode = "404", description = ServerApiDescriptions.SERVER_NOT_FOUND),
	})
	@PostMapping
	public ResponseEntity<ServerBanResponse> banUser(@PathVariable Long serverId,
			@Valid @RequestBody CreateServerBanRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		Server server = ensureServerExists(serverId);
		ensureCanBanMembers(userId, serverId);

		if (userId.equals(request.getTargetUserId())) {
			throw new CannotBanYourselfException(
					BusinessRuleMessage.BUSINESS_CANNOT_BAN_SELF_MESSAGE.getMessage());
		}

		if (server.getOwner().getId().equals(request.getTargetUserId())) {
			throw new CannotBanServerOwnerException(
					BusinessRuleMessage.BUSINESS_CANNOT_BAN_SERVER_OWNER_MESSAGE.getMessage());
		}

		ServerBan ban = serverBanService.banUser(serverId, request.getTargetUserId(), userId,
				request.getReason(), request.getExpiresAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(ban));
	}

	@Operation(summary = "Разбанить пользователя",
			description = "Снимает активный бан с пользователя targetUserId на сервере. "
					+ "Сервер должен существовать.")
	@SecuredApiResponses
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = ServerBanApiDescriptions.SERVER_UNBAN_USER_SUCCES),
		@ApiResponse(responseCode = "403", description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS),
		@ApiResponse(responseCode = "404", description = ServerApiDescriptions.SERVER_NOT_FOUND),
	})
	@DeleteMapping("/{targetUserId}")
	public ResponseEntity<Void> unbanUser(@PathVariable Long serverId,
			@PathVariable Long targetUserId, @AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);
		ensureServerExists(serverId);
		ensureCanBanMembers(userId, serverId);

		serverBanService.unbanUser(serverId, targetUserId, userId);
		return ResponseEntity.noContent().build();
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}

	private Server ensureServerExists(Long serverId) {
		return serverService.getServer(serverId);
	}

	private void ensureCanBanMembers(Long userId, Long serverId) {
		if (!permissionService.canBanMembers(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private ServerBanResponse mapToResponse(ServerBan ban) {
		return ServerBanResponse.builder().userId(ban.getUser().getId())
				.username(ban.getUser().getUsername()).reason(ban.getReason())
				.createdAt(ban.getCreatedAt()).expiresAt(ban.getExpiresAt())
				.bannedById(ban.getBannedBy().getId())
				.bannedByUsername(ban.getBannedBy().getUsername()).build();
	}
}

