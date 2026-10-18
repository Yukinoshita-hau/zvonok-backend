### src/main/java/com/zvonok/controller/ChannelController.java
```java
package com.zvonok.controller;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.documentation.ChannelApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ChannelFolderService;
import com.zvonok.service.ChannelService;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.CreateChannelDto;
import com.zvonok.service.dto.Permission;
import com.zvonok.service.dto.UpdateChannelDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-эндпоинты для управления каналами внутри конкретной папки сервера. Для операций записи
 * требуется право {@code MANAGE_CHANNELS}.
 */
@Tag(name = "Контроллер управления каналами сервера",
		description = "Эндпоинты для чтения и управления каналами внутри папки каналов сервера.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/servers/{serverId}/channel-folders/{folderId}/channels")
@RequiredArgsConstructor
public class ChannelController {

	private final ChannelService channelService;
	private final ChannelFolderService channelFolderService;
	private final PermissionService permissionService;
	private final UserService userService;
	private final ServerService serverService;

	@Operation(summary = "Список каналов в папке",
			description = "Возвращает список каналов, отсортированный внутри папки каналов. "
					+ "Сервер должен существовать, папка должна принадлежать серверу.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = ChannelApiDescriptions.CHANNEL_GET_SUCCESS)
	@ApiResponse403()
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@GetMapping
	public ResponseEntity<List<Channel>> getChannels(@PathVariable Long serverId,
			@PathVariable Long folderId, @AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);
		ensureCanViewFolder(userId, folderId);

		List<Channel> channels = channelService.getChannelsOrdered(folderId);
		return ResponseEntity.ok(channels);
	}

	@Operation(summary = "Получить канал по id",
			description = "Возврат канала по его идентификатору")
	@SecuredApiResponses
	@GetMapping("/{channelId}")
	public Channel getChannel(@PathVariable Long serverId, @PathVariable Long folderId,
			@PathVariable Long channelId, @AuthenticationPrincipal UserPrincipal principal) {
		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);

		return channelService.getChannel(principal.getUsername(), folderId, channelId);
	}

	@Operation(summary = "Создать канал в папке",
			description = "Создаёт новый канал внутри указанной папки каналов. "
					+ "Сервер должен существовать, папка должна принадлежать серверу;")
	@SecuredApiResponses
	@ApiResponse400()
	@ApiResponse403()
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@ApiResponse(responseCode = "201", description = ChannelApiDescriptions.CHANNEl_CREATE_SUCCESS)
	@PostMapping("/create")
	public ResponseEntity<Channel> createChannel(@PathVariable Long serverId,
			@PathVariable Long folderId, @Valid @RequestBody CreateChannelDto createChannelDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);
		ensureCanManageChannels(userId, serverId);

		createChannelDto.setFolderId(folderId);
		Channel channel = channelService.createChannel(createChannelDto, principal.getUsername());
		return ResponseEntity.status(HttpStatus.CREATED).body(channel);
	}

	@Operation(summary = "Обновить канал",
			description = "Обновляет параметры канала по channelId в рамках указанной папки. "
					+ "Сервер должен существовать, папка должна принадлежать серверу;")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = ChannelApiDescriptions.CHANNEL_UPDATE_SUCCESS)
	@ApiResponse400()
	@ApiResponse403()
	@ApiResponse404(description = ChannelApiDescriptions.CHANNEL_NOT_FOUND)
	@PutMapping("/{channelId}")
	public ResponseEntity<Channel> updateChannel(@PathVariable Long serverId,
			@PathVariable Long folderId, @PathVariable Long channelId,
			@Valid @RequestBody UpdateChannelDto updateChannelDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);
		ensureCanManageChannels(userId, serverId);
		channelService.getChannel(folderId, channelId);

		Channel updated = channelService.updateChannel(channelId, updateChannelDto);
		return ResponseEntity.ok(updated);
	}

	@Operation(summary = "Удалить канал",
			description = "Удаляет канал по channelId из указанной папки. "
					+ "Сервер должен существовать, папка должна принадлежать серверу;")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204", description = ChannelApiDescriptions.CHANNEL_DELETE_SUCCESS)
	@ApiResponse403()
	@ApiResponse404(description = ChannelApiDescriptions.CHANNEL_NOT_FOUND)
	@DeleteMapping("/{channelId}")
	public ResponseEntity<Void> deleteChannel(@PathVariable Long serverId,
			@PathVariable Long folderId, @PathVariable Long channelId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);
		ensureCanManageChannels(userId, serverId);
		channelService.getChannel(folderId, channelId);

		channelService.deleteChannel(channelId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{channelId}/messages")
	public ResponseEntity<List<ChannelMessageResponse>> getChannelMessages(
			@PathVariable Long serverId, @PathVariable Long folderId,
			@PathVariable Long channelId, @RequestParam(required = false) Long beforeMessageId,
			@RequestParam(defaultValue = "15") int limit,
			@AuthenticationPrincipal UserPrincipal principal) {
		ensureServerExists(serverId);
		ensureFolderBelongsToServer(serverId, folderId);

		List<ChannelMessageResponse> messages = channelService.getChannelMessage(
				principal.getUsername(), folderId, channelId, beforeMessageId, limit);
		return ResponseEntity.ok(messages);
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}

	private void ensureServerExists(Long serverId) {
		serverService.getServer(serverId);
	}

	private void ensureFolderBelongsToServer(Long serverId, Long folderId) {
		channelFolderService.getChannelFolderForServer(serverId, folderId);
	}

	private void ensureCanViewFolder(Long userId, Long folderId) {
		if (!permissionService.canUserViewFolder(userId, folderId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void ensureCanManageChannels(Long userId, Long serverId) {
		if (!permissionService.hasPermissionInServer(userId, serverId,
				Permission.MANAGE_CHANNELS)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}
}

```

### src/main/java/com/zvonok/controller/ChannelFolderController.java
```java
package com.zvonok.controller;

import com.zvonok.documentation.ChannelFolderApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.ChannelFolderService;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.CreateChannelFolderDto;
import com.zvonok.service.dto.Permission;
import com.zvonok.service.dto.UpdateChannelFolderDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-эндпоинты для управления папками каналов внутри сервера. Все операции, кроме чтения, требуют
 * права {@code MANAGE_CHANNELS}.
 */
@Tag(name = "Контроллер управления папками сервера")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/server/{serverId}/channel-folders")
@RequiredArgsConstructor
public class ChannelFolderController {

	private final ChannelFolderService channelFolderService;
	private final PermissionService permissionService;
	private final UserService userService;
	private final ServerService serverService;

	@Operation(summary = "Список папок каналов сервера",
			description = "Возвращает активные папки каналов на сервере; доступно только участникам сервера.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ChannelFolderApiDescriptions.CHANNEL_FOLDER_GET_ALL_FOLDER_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@GetMapping
	public ResponseEntity<List<ChannelFolder>> getChannelFolders(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureIsServerMember(userId, serverId);

		List<ChannelFolder> folders = channelFolderService.getActiveChannelFolders(serverId, principal.getUsername());
		return ResponseEntity.ok(folders);
	}

	@Operation(summary = "Создать папку каналов",
			description = "Создаёт новую папку каналов на сервере.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "201",
			description = ChannelFolderApiDescriptions.CHANNEL_FOLDER_CREATE_FOLDER_SUCCESS)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@PostMapping
	public ResponseEntity<ChannelFolder> createChannelFolder(@PathVariable Long serverId,
			@Valid @RequestBody CreateChannelFolderDto createChannelFolderDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageChannels(userId, serverId);

		createChannelFolderDto.setServerId(serverId);
		ChannelFolder folder = channelFolderService.createChannelFolder(createChannelFolderDto);
		return ResponseEntity.status(HttpStatus.CREATED).body(folder);
	}

	@Operation(summary = "Обновить папку каналов",
			description = "Обновляет параметры папки каналов по folderId внутри сервера.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ChannelFolderApiDescriptions.CHANNEL_FOLDER_UPDATE_FOLDER_SUCCESS)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@PutMapping("/{folderId}")
	public ResponseEntity<ChannelFolder> updateChannelFolder(@PathVariable Long serverId,
			@PathVariable Long folderId,
			@Valid @RequestBody UpdateChannelFolderDto updateChannelFolderDto,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageChannels(userId, serverId);
		channelFolderService.getChannelFolderForServer(serverId, folderId);

		ChannelFolder updated =
				channelFolderService.updateChannelFolder(folderId, updateChannelFolderDto);
		return ResponseEntity.ok(updated);
	}

	@Operation(summary = "Удалить папку каналов",
			description = "Удаляет папку каналов по folderId внутри сервера.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204",
			description = ChannelFolderApiDescriptions.CHANNEL_FOLDER_DELETE_FOLDER_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@DeleteMapping("/{folderId}")
	public ResponseEntity<Void> deleteChannelFolder(@PathVariable Long serverId,
			@PathVariable Long folderId, @AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageChannels(userId, serverId);
		channelFolderService.getChannelFolderForServer(serverId, folderId);

		channelFolderService.deleteChannelFolder(folderId);
		return ResponseEntity.noContent().build();
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}

	private void ensureServerExists(Long serverId) {
		serverService.getServer(serverId);
	}

	private void ensureIsServerMember(Long userId, Long serverId) {
		if (!permissionService.isServerMember(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void ensureCanManageChannels(Long userId, Long serverId) {
		if (!permissionService.hasPermissionInServer(userId, serverId,
				Permission.MANAGE_CHANNELS)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}
}


```

### src/main/java/com/zvonok/controller/ServerBanController.java
```java
package com.zvonok.controller;

import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.ServerBanApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
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
	@ApiResponse(responseCode = "200",
			description = ServerBanApiDescriptions.SERVER_BAN_GET_LIST_SUCCESS)
	@ApiResponse403(description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS)
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200",
			description = ServerBanApiDescriptions.SERVER_BAN_USER_SUCCES)
	@ApiResponse400
	@ApiResponse403(description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS)
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200",
			description = ServerBanApiDescriptions.SERVER_UNBAN_USER_SUCCES)
	@ApiResponse403(description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS)
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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


```

### src/main/java/com/zvonok/controller/ServerController.java
```java
package com.zvonok.controller;

import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.ApiResponse409;
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
@RequestMapping("/servers")
@RequiredArgsConstructor
public class ServerController {

	private final ServerService serverService;
	private final UserService userService;

	@Operation(summary = "Создаёт сервер",
			description = "Создание серверана на основе введённых данных")
	@SecurityRequirement(name = "JWT")
	@SecuredApiResponses
	@ApiResponse(responseCode = "201", description = ServerApiDescriptions.SERVER_CREATE_SUCCESS)
	@ApiResponse400
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
	@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_GET_MY_SUCCESS)
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
	@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_GET_SUCCESS)
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_JOIN_BY_INVITE_CODE_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_UPDATE_SUCCESS)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_REGENERATE_INVITE_CODE_SUCCESS)
	@ApiResponse403
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
	@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_LEAVE_SUCCESS)
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
	@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_GET_MEMBERS_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
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
	@ApiResponse(responseCode = "204",
			description = ServerApiDescriptions.SERVER_KICK_MEMBER_SUCCESS)
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@ApiResponse409(description = ServerApiDescriptions.SERVER_MEMBER_ALREADY_KICKED)
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
	@ApiResponse(responseCode = "200",
			description = ServerApiDescriptions.SERVER_UPDATE_MEMBER_NICKNAME_SUCCESS)
	@ApiResponse400
	@ApiResponse404(description = ServerApiDescriptions.SERVER_OR_MEMBER_NOT_FOUND)
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
	@ApiResponse(responseCode = "200", description = ServerApiDescriptions.SERVER_DELETE_SUCCESS)
	@ApiResponse403
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

```

### src/main/java/com/zvonok/controller/ServerMemberRoleController.java
```java
package com.zvonok.controller;

import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.ServerMemberRoleApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.ServerMember;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerMemberRoleService;
import com.zvonok.service.ServerMemberService;
import com.zvonok.service.ServerRoleService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST-эндпоинты для назначения и снятия ролей у участника сервера.
 */
@Tag(name = "Контроллер ролей участника сервера",
		description = "Эндпоинты для назначения и снятия ролей у участника сервера. "
				+ "Tакже проверяется существование сервера, "
				+ "принадлежность участника серверу и существование роли на сервере.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/server/{serverId}/members/{memberId}/roles")
@RequiredArgsConstructor
public class ServerMemberRoleController {

	private final ServerMemberRoleService serverMemberRoleService;
	private final ServerMemberService serverMemberService;
	private final ServerRoleService serverRoleService;
	private final ServerService serverService;
	private final PermissionService permissionService;
	private final UserService userService;

	@Operation(summary = "Назначить роль участнику",
			description = "Назначает роль roleId участнику memberId на сервере serverId. "
					+ "Сервер должен существовать; участник должен принадлежать этому серверу; "
					+ "роль должна существовать на сервере.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ServerMemberRoleApiDescriptions.SERVER_MEMBER_ROLE_ASSIGN_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@PostMapping("/{roleId}")
	public ResponseEntity<Void> assignRoleToMember(@PathVariable Long serverId,
			@PathVariable Long memberId, @PathVariable Long roleId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageRoles(userId, serverId);
		getServerMemberForServer(serverId, memberId);
		serverRoleService.getServerRoleForServer(serverId, roleId);

		serverMemberRoleService.createServerMemberRole(memberId, roleId, userId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Снять роль с участника",
			description = "Снимает роль roleId с участника memberId на сервере serverId. "
					+ "Сервер должен существовать; участник должен принадлежать этому серверу; "
					+ "роль должна существовать на сервере.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204",
			description = ServerMemberRoleApiDescriptions.SERVER_MEMBER_ROLE_REMOVE_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@DeleteMapping("/{roleId}")
	public ResponseEntity<Void> removeRoleFromMember(@PathVariable Long serverId,
			@PathVariable Long memberId, @PathVariable Long roleId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageRoles(userId, serverId);
		ServerMember member = getServerMemberForServer(serverId, memberId);
		serverRoleService.getServerRoleForServer(serverId, roleId);

		serverMemberRoleService.removeRoleFromMember(member.getId(), roleId);
		return ResponseEntity.noContent().build();
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}

	private void ensureServerExists(@NotNull Long serverId) {
		serverService.getServer(serverId);
	}

	private ServerMember getServerMemberForServer(Long serverId, Long memberId) {
		ServerMember member = serverMemberService.getServerMember(memberId);
		if (!member.getServer().getId().equals(serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
		return member;
	}

	private void ensureCanManageRoles(Long userId, Long serverId) {
		if (!permissionService.canManageRoles(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}
}


```

### src/main/java/com/zvonok/controller/ServerRoleController.java
```java
package com.zvonok.controller;

import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.ServerRoleApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Server;
import com.zvonok.model.ServerRole;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.PermissionService;
import com.zvonok.service.ServerRoleService;
import com.zvonok.service.ServerService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.CreateServerRoleDto;
import com.zvonok.service.dto.UpdateServerRoleDto;
import com.zvonok.service.dto.request.CreateServerRoleRequest;
import com.zvonok.service.dto.request.UpdateServerRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-эндпоинты управления ролями сервера. Требует права {@code MANAGE_ROLES} для операций записи.
 */

@Tag(name = "Контроллер управления ролями сервера",
		description = "Контроллер отвечающий за управление ролями сервера")

@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/server/{serverId}/roles")
@RequiredArgsConstructor
public class ServerRoleController {

	private final ServerRoleService serverRoleService;
	private final ServerService serverService;
	private final PermissionService permissionService;
	private final UserService userService;

	@Operation(summary = "Список ролей сервера",
			description = "Возвращает активные роли сервера; доступно только участникам сервера")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ServerRoleApiDescriptions.SERVER_ROLE_GET_ALL_ROLES_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@GetMapping("/all")
	public ResponseEntity<List<ServerRole>> getServerRoles(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureIsServerMember(userId, serverId);

		List<ServerRole> roles = serverRoleService.getActiveServerRoles(serverId);
		return ResponseEntity.ok(roles);
	}

	@Operation(summary = "Создать роль сервера", description = "Создаёт новую роль на сервере.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ServerRoleApiDescriptions.SERVER_ROLE_CREATE_ROLE_SUCCESS)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@PostMapping("/create")
	public ResponseEntity<ServerRole> createServerRole(@PathVariable Long serverId,
			@Valid @RequestBody CreateServerRoleRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageRoles(userId, serverId);

		Server server = serverService.getServer(serverId);

		CreateServerRoleDto dto = new CreateServerRoleDto();
		dto.setName(request.getName());
		dto.setColor(request.getColor());
		dto.setPosition(request.getPosition());
		dto.setServerPermissions(request.getServerPermissions());
		dto.setMentionable(request.isMentionable());
		dto.setEveryone(false);
		dto.setServer(server);

		ServerRole role = serverRoleService.createServerRole(dto);
		return ResponseEntity.status(HttpStatus.CREATED).body(role);
	}

	@Operation(summary = "Обновить роль сервера", description = "Создаёт новую роль на сервере.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = ServerRoleApiDescriptions.SERVER_ROLE_UPDATE_SUCCES)
	@ApiResponse400
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@PutMapping("/{roleId}")
	public ResponseEntity<ServerRole> updateServerRole(@PathVariable Long serverId,
			@PathVariable Long roleId, @Valid @RequestBody UpdateServerRoleRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageRoles(userId, serverId);
		serverRoleService.getServerRoleForServer(serverId, roleId);

		UpdateServerRoleDto dto = new UpdateServerRoleDto();
		dto.setName(request.getName());
		dto.setColor(request.getColor());
		dto.setPosition(request.getPosition());
		dto.setServerPermissions(request.getServerPermissions());
		dto.setMentionable(request.getMentionable());
		dto.setActive(request.getActive());

		ServerRole updated = serverRoleService.updateServerRole(roleId, dto);
		return ResponseEntity.ok(updated);
	}

	@Operation(summary = "Удалить роль сервера", description = "Удаляет роль сервера.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204",
			description = ServerRoleApiDescriptions.SERVER_ROLE_DELETE_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = ServerApiDescriptions.SERVER_NOT_FOUND)
	@DeleteMapping("/{roleId}")
	public ResponseEntity<Void> deleteServerRole(@PathVariable Long serverId,
			@PathVariable Long roleId, @AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureCanManageRoles(userId, serverId);
		serverRoleService.getServerRoleForServer(serverId, roleId);

		serverRoleService.deleteServerRole(roleId);
		return ResponseEntity.noContent().build();
	}

	private Long getCurrentUserId(UserPrincipal principal) {
		User user = userService.getUser(principal.getUsername());
		return user.getId();
	}

	private void ensureServerExists(Long serverId) {
		serverService.getServer(serverId);
	}

	private void ensureIsServerMember(Long userId, Long serverId) {
		if (!permissionService.isServerMember(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void ensureCanManageRoles(Long userId, Long serverId) {
		if (!permissionService.canManageRoles(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}
}


```

### src/main/java/com/zvonok/documentation/ChannelApiDescriptions.java
```java
package com.zvonok.documentation;

public final class ChannelApiDescriptions {
	// 2xx Success
	public static final String CHANNEL_GET_SUCCESS = "Успешное получение каналов";	

	public static final String CHANNEl_CREATE_SUCCESS = "Успешное создание сервера";	

	public static final String CHANNEL_DELETE_SUCCESS = "Успешное удаление сервера";

	public static final String CHANNEL_UPDATE_SUCCESS = "Успешное обновление сервера";
	// 4xx Client Errors
	public static final String CHANNEL_NOT_FOUND = "Канал не найден";

	private ChannelApiDescriptions() {
		throw new AssertionError("No instances");
	}
}

```

### src/main/java/com/zvonok/documentation/ChannelFolderApiDescriptions.java
```java
package com.zvonok.documentation;

public final class ChannelFolderApiDescriptions {
	// 2xx Success

	public static final String CHANNEL_FOLDER_GET_ALL_FOLDER_SUCCESS =
			"Список папок каналов сервера успешно получен.";

	public static final String CHANNEL_FOLDER_CREATE_FOLDER_SUCCESS =
			"Папка каналов успешно создана.";

	public static final String CHANNEL_FOLDER_UPDATE_FOLDER_SUCCESS =
			"Папка каналов успешно обновлена.";

	public static final String CHANNEL_FOLDER_DELETE_FOLDER_SUCCESS =
			"Папка каналов успешно удалена.";

	// 4xx Client Errors

	private ChannelFolderApiDescriptions() {
		throw new AssertionError("No instances");
	}
}

```

### src/main/java/com/zvonok/documentation/ServerApiDescriptions.java
```java
package com.zvonok.documentation;

public final class ServerApiDescriptions {
	// 2xx Success
	public static final String SERVER_GET_SUCCESS = "Сервер успешно получен";

	public static final String SERVER_UPDATE_SUCCESS = "Сервер успешно обновлён";

	public static final String SERVER_DELETE_SUCCESS = "Сервер успешно удалён";

	public static final String SERVER_REGENERATE_INVITE_CODE_SUCCESS = "Код приглашения успешно обновлён";

	public static final String SERVER_LEAVE_SUCCESS = "Пользователь успешно покинул сервер";

	public static final String SERVER_JOIN_BY_INVITE_CODE_SUCCESS = "Пользователь успешно присоединён к серверу";

	public static final String SERVER_CREATE_SUCCESS = "Сервер успешно создан";

	public static final String SERVER_GET_MEMBERS_SUCCESS = "Список участников сервера успешно получен";

	public static final String SERVER_GET_MY_SUCCESS = "Список серверов пользователя успешно получен"; 

	public static final String SERVER_KICK_MEMBER_SUCCESS = "Участник успешно исключён с сервера";

	public static final String SERVER_UPDATE_MEMBER_NICKNAME_SUCCESS = "Никнейм участника успешно обновлён";

	// 4xx Client Errors
	public static final String SERVER_NOT_FOUND = "Сервер не найден";

	public static final String SERVER_OR_MEMBER_NOT_FOUND = "Сервер или участник не найдены";

	public static final String SERVER_MEMBER_ALREADY_KICKED = "Участник уже не является членом сервера";
}

```

### src/main/java/com/zvonok/documentation/ServerBanApiDescriptions.java
```java
package com.zvonok.documentation;

public final class ServerBanApiDescriptions {
	// 2xx Success
	public static final String SERVER_BAN_GET_LIST_SUCCESS = "Успешное получение списка заблокированных пользователей";	

	public static final String SERVER_BAN_USER_SUCCES = "Успешная блокировка пользователя";	

	public static final String SERVER_UNBAN_USER_SUCCES = "Успешная разблокировака пользователя";	
	// 4xx Client Errors


	private ServerBanApiDescriptions() {
		throw new AssertionError("No instances");
	}
}

```

### src/main/java/com/zvonok/documentation/ServerMemberRoleApiDescriptions.java
```java

package com.zvonok.documentation;

public final class ServerMemberRoleApiDescriptions {
	// 2xx Success
	public static final String SERVER_MEMBER_ROLE_ASSIGN_SUCCESS = "Успешное назначение роли";	

	public static final String SERVER_MEMBER_ROLE_REMOVE_SUCCESS = "Успешное cнятие роли";	
	// 4xx Client Errors

	private ServerMemberRoleApiDescriptions() {
		throw new AssertionError("No instances");
	}
}

```

### src/main/java/com/zvonok/documentation/ServerRoleApiDescriptions.java
```java
package com.zvonok.documentation;

public final class ServerRoleApiDescriptions {

	// 2xx Success
	public static final String SERVER_ROLE_GET_ALL_ROLES_SUCCESS = "Успешное получение всех ролей";	

	public static final String SERVER_ROLE_CREATE_ROLE_SUCCESS = "Успешное создание роли";	

	public static final String SERVER_ROLE_UPDATE_SUCCES = "Успешное обновление роли";	

	public static final String SERVER_ROLE_DELETE_SUCCESS = "Успешное удаление роли";	

	// 4xx Client Errors

	private ServerRoleApiDescriptions() {
		throw new AssertionError("No instances");
	}
}

```

### src/main/java/com/zvonok/exception/CannotBanServerOwnerException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class CannotBanServerOwnerException extends RuntimeException {
    public CannotBanServerOwnerException(String message) {
        super(message);
    }
}



```

### src/main/java/com/zvonok/exception/CannotKickServerOwnerException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class CannotKickServerOwnerException extends RuntimeException {
    public CannotKickServerOwnerException(String message) {
        super(message);
    }
}



```

### src/main/java/com/zvonok/exception/ChannelFolderNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ChannelFolderNotFoundException extends RuntimeException {
    public ChannelFolderNotFoundException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/exception/ChannelNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ChannelNotFoundException extends RuntimeException {
    public ChannelNotFoundException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/exception/InvalidServerMaxMemberException.java
```java
package com.zvonok.exception;

import org.springframework.http.HttpStatus;

import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class InvalidServerMaxMemberException extends RuntimeException { 
    public InvalidServerMaxMemberException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/exception/InvalidServerNameException.java
```java
package com.zvonok.exception;

import org.springframework.http.HttpStatus;

import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class InvalidServerNameException extends RuntimeException {
    public InvalidServerNameException(String message) {
        super(message);
    }    
}
```

### src/main/java/com/zvonok/exception/OwnerCanNotLeaveServerException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class OwnerCanNotLeaveServerException extends RuntimeException {
  public OwnerCanNotLeaveServerException(String message) {
    super(message);
  }
}

```

### src/main/java/com/zvonok/exception/ServerBanNotFoundException.java
```java
package com.zvonok.exception;

import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerBanNotFoundException extends RuntimeException {
    public ServerBanNotFoundException(String message) {
        super(message);
    }
}


```

### src/main/java/com/zvonok/exception/ServerMemberAlreadyWasKicked.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.CONFLICT)
public class ServerMemberAlreadyWasKicked extends RuntimeException {
    public ServerMemberAlreadyWasKicked(String message) {
        super(message);
    }
}



```

### src/main/java/com/zvonok/exception/ServerMemberLimitReachedException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.CONFLICT)
public class ServerMemberLimitReachedException extends RuntimeException {
    public ServerMemberLimitReachedException(String message) {
        super(message);
    }
}



```

### src/main/java/com/zvonok/exception/ServerMemberNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerMemberNotFoundException extends RuntimeException {
    public ServerMemberNotFoundException(String message) {
        super(message);
    }
}
```

### src/main/java/com/zvonok/exception/ServerMemberRoleNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerMemberRoleNotFoundException extends RuntimeException {
    public ServerMemberRoleNotFoundException(String message) {
        super(message);
    }
}
```

### src/main/java/com/zvonok/exception/ServerNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerNotFoundException extends RuntimeException {
    public ServerNotFoundException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/exception/ServerRoleNotFoundException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerRoleNotFoundException extends RuntimeException {
    public ServerRoleNotFoundException(String message) {
        super(message);
    }
}
```

### src/main/java/com/zvonok/exception/YouAlreadyMemberThisServerException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.CONFLICT)
public class YouAlreadyMemberThisServerException extends RuntimeException {
    public YouAlreadyMemberThisServerException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/exception/YouNotMemberThisServerException.java
```java
package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class YouNotMemberThisServerException extends RuntimeException {
    public YouNotMemberThisServerException(String message) {
        super(message);
    }
}

```

### src/main/java/com/zvonok/model/Channel.java
```java
package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.model.enumeration.ChannelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "channel_folder_id", nullable = false) @JsonBackReference
    private ChannelFolder folder;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    private ChannelType type = ChannelType.TEXT;

    private Integer userLimit = 100; // лимит участников (для голосовых каналов)
    private Integer slowModeSeconds; // задержка в секундах
    private Boolean isActive = true;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "channel")
    @JsonIgnore
    private List<ChannelPermissionOverride> permissionOverrides;

    private Integer position = 0;
    private String topic;
    private Boolean nsfw = false; // контент, который может быть неприемлем в общественных или рабочих условиях
}

```

### src/main/java/com/zvonok/model/ChannelFolder.java
```java
package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "channel_folder")
public class ChannelFolder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@ManyToOne
	@JoinColumn(name = "server_id", nullable = false)
	@JsonBackReference
	private Server server;

	@OneToMany(mappedBy = "folder",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonManagedReference
	private List<Channel> channels;

	@OneToMany(mappedBy = "folder",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<FolderPermissionOverride> permissionOverrides;

	private Integer position = 0; // Порядок отображения папок
	private Boolean collapsed = false; // Свернута ли папка по умолчанию
	private Boolean isActive = true;
	private LocalDateTime createdAt;
}

```

### src/main/java/com/zvonok/model/ChannelPermissionOverride.java
```java
package com.zvonok.model;

import com.zvonok.exception.PermissionOverrideTargetMissingException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.service.dto.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "channel_permission_override")
public class ChannelPermissionOverride {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne @JoinColumn(name = "role_id")
    private ServerRole role;

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Long allowedPermissions = Permission.NOTHING.getValue();

    @Column(nullable = false)
    private Long deniedPermissions = Permission.NOTHING.getValue();

    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void validateOverride() {
        // Должен быть либо role, либо user, но не оба
        if ((role == null && user == null) || (role != null && user != null)) {
            throw new PermissionOverrideTargetMissingException(
                    BusinessRuleMessage.BUSINESS_PERMISSION_OVERRIDE_TARGET_REQUIRED_MESSAGE.getMessage());
        }
    }
}

```

### src/main/java/com/zvonok/model/FolderPermissionOverride.java
```java
package com.zvonok.model;

import com.zvonok.exception.RedefinitionException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.service.dto.Permission;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "folder_permission_override")
public class FolderPermissionOverride {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "folder_id", nullable = false)
    private ChannelFolder folder;

    @ManyToOne @JoinColumn(name = "role_id")
    private ServerRole role;

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Long allowedPermissions = Permission.NOTHING.getValue(); // Что разрешено в этой папке

    @Column(nullable = false)
    private Long deniedPermissions = Permission.NOTHING.getValue(); // Что запрещено в этой папке

    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void validateOverride() {
        if ((role == null && user == null) || (role != null && user != null)) {
            throw new RedefinitionException(HttpResponseMessage.HTTP_REDEFINITION_RESPONSE_MESSAGE.getMessage());
        }
    }
}

```

### src/main/java/com/zvonok/model/Server.java
```java
package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server")
public class Server {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 15)
	private String invitedCode;

	@Column(nullable = false)
	private Integer maxMember = 10000;

	private Boolean isActive = true;

	private LocalDateTime createdAt;

	private String bannerUrl;

	private boolean isPrivate = false;

	@ManyToOne
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonIgnore
	private List<ServerMember> members;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonManagedReference
	private List<ChannelFolder> channelFolders;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonIgnore
	private List<ServerRole> roles;
}

```

### src/main/java/com/zvonok/model/ServerBan.java
```java
package com.zvonok.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "server_ban")
public class ServerBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "server_id")
    private Server server;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "banned_by")
    private User bannedBy;

    @Column(length = 255)
    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private Boolean active = true;

    private LocalDateTime unbannedAt;

    @ManyToOne
    @JoinColumn(name = "unbanned_by")
    private User unbannedBy;
}


```

### src/main/java/com/zvonok/model/ServerMember.java
```java
package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.service.dto.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server_member")
public class ServerMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "server_id", nullable = false)
	private Server server;

	@OneToMany(mappedBy = "member",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ServerMemberRole> memberRoles = new ArrayList<>();

	private Long personalPermissions = Permission.NOTHING.getValue();

	@Column(length = 32)
	private String nickname;

	private Boolean isActive = true;

	private LocalDateTime joinedAt;

	private LocalDateTime leftAt;
}

```

### src/main/java/com/zvonok/model/ServerMemberRole.java
```java
package com.zvonok.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server_member_role")
public class ServerMemberRole {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "member_id", nullable = false)
    private ServerMember member;

    @ManyToOne @JoinColumn(name = "role_id", nullable = false)
    private ServerRole role;

    private LocalDateTime assignedAt;

    @ManyToOne @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;
}

```

### src/main/java/com/zvonok/model/ServerRole.java
```java
package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.service.dto.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server_role")
public class ServerRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(length = 7)
	private String color = "#ffffff";

	@Column(nullable = false)
	private Integer position = 0; // Иерархия ролей

	@Column(nullable = false)
	private Long serverPermissions = Permission.NOTHING.getValue();

	private Boolean mentionable = true;
	private Boolean isEveryone = false; // Роль @everyone
	private Boolean isActive = true;
	private LocalDateTime createdAt;

	@ManyToOne
	@JsonBackReference
	@JoinColumn(name = "server_id", nullable = false)
	private Server server;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ServerMemberRole> memberRoles;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<FolderPermissionOverride> folderOverrides;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ChannelPermissionOverride> channelOverrides;
}

```

### src/main/java/com/zvonok/repository/ChannelFolderRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ChannelFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelFolderRepository extends JpaRepository<ChannelFolder, Long> {
    List<ChannelFolder> findByServerIdAndIsActiveTrueOrderByPosition(Long serverId);
    Optional<ChannelFolder> findByIdAndServerId(Long folderId, Long serverId);
}
```

### src/main/java/com/zvonok/repository/ChannelPermissionOverrideRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ChannelPermissionOverride;
import com.zvonok.model.ServerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelPermissionOverrideRepository extends JpaRepository<ChannelPermissionOverride, Long> {
    Optional<ChannelPermissionOverride> findByChannelIdAndUserId(Long channelId, Long userId);

    Optional<ChannelPermissionOverride> findByChannelIdAndRoleId(Long channelId, Long roleId);

    // Поиск всех переопределений ролей в канале
    @Query("""
        SELECT cpo FROM ChannelPermissionOverride cpo 
        WHERE cpo.channel.id = :channelId 
        AND cpo.role IN :roles
        """)
    List<ChannelPermissionOverride> findByChannelIdAndRoleIn(@Param("channelId") Long channelId,
                                                             @Param("roles") List<ServerRole> roles);

    List<ChannelPermissionOverride> findByChannelId(Long channelId);

    void deleteByRoleId(Long roleId);

    void deleteByChannelIdAndUserId(Long channelId, Long userId);

}
```

### src/main/java/com/zvonok/repository/ChannelRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findById(long id);
    List<Channel> findByFolderIdAndIsActiveTrue(Long folderId);
    List<Channel> findByFolderIdOrderByPosition(Long folderId);
    long countByFolderIdAndIsActiveTrue(Long folderId);
    Optional<Channel> findByIdAndFolderId(Long channelId, Long folderId);
}

```

### src/main/java/com/zvonok/repository/FolderPermissionOverrideRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.FolderPermissionOverride;
import com.zvonok.model.ServerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderPermissionOverrideRepository extends JpaRepository<FolderPermissionOverride, Long> {
    Optional<FolderPermissionOverride> findByFolderIdAndUserId(Long folderId, Long userId);

    Optional<FolderPermissionOverride> findByFolderIdAndRoleId(Long folderId, Long roleId);

    // Поиск всех переопределений ролей в папке
    @Query("""
        SELECT fpo FROM FolderPermissionOverride fpo 
        WHERE fpo.folder.id = :folderId 
        AND fpo.role IN :roles
        """)
    List<FolderPermissionOverride> findByFolderIdAndRoleIn(@Param("folderId") Long folderId,
                                                           @Param("roles") List<ServerRole> roles);

    List<FolderPermissionOverride> findByFolderId(Long folderId);

    void deleteByRoleId(Long roleId);

    void deleteByFolderIdAndUserId(Long folderId, Long userId);
}
```

### src/main/java/com/zvonok/repository/ServerBanRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ServerBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerBanRepository extends JpaRepository<ServerBan, Long> {
    Optional<ServerBan> findByServerIdAndUserIdAndActiveTrue(Long serverId, Long userId);
    List<ServerBan> findByServerIdAndActiveTrue(Long serverId);
}


```

### src/main/java/com/zvonok/repository/ServerMemberRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ServerMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    Optional<ServerMember> findByUserIdAndServerId(Long userId, Long serverId);
    Optional<ServerMember> findByUserIdAndServerIdAndIsActiveTrue(Long userId, Long serverId);
    Optional<ServerMember> findByUserIdAndServerIdAndIsActiveFalse(Long userId, Long serverId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN Channel c ON c.folder.server.id = sm.server.id 
        WHERE sm.user.id = :userId 
        AND c.id = :channelId 
        AND sm.isActive = true
        """)
    Optional<ServerMember> findByUserIdAndChannelId(@Param("userId") Long userId, @Param("channelId") Long channelId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN ChannelFolder cf ON cf.server.id = sm.server.id 
        WHERE sm.user.id = :userId 
        AND cf.id = :folderId 
        AND sm.isActive = true
        """)
    Optional<ServerMember> findByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN sm.memberRoles mr 
        WHERE mr.role.id = :roleId 
        AND sm.isActive = true
        """)
    List<ServerMember> findByRoleId(@Param("roleId") Long roleId);
    @Query("""
        SELECT COUNT(mr) > 0 FROM ServerMemberRole mr 
        WHERE mr.member.id = :memberId 
        AND mr.role.id = :roleId
        """)
    boolean hasRole(@Param("memberId") Long memberId, @Param("roleId") Long roleId);
    long countByServerIdAndIsActiveTrue(Long serverId);

    List<ServerMember> findByServerIdAndIsActiveTrue(Long serverId);

    @Query("""
        SELECT COUNT(s) > 0 FROM Server s 
        WHERE s.owner.id = :userId 
        AND s.id = :serverId
        """)
    boolean isServerOwner(@Param("userId") Long userId, @Param("serverId") Long serverId);
}

```

### src/main/java/com/zvonok/repository/ServerMemberRoleRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ServerMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerMemberRoleRepository extends JpaRepository<ServerMemberRole, Long> {
    Optional<ServerMemberRole> findByMemberIdAndRoleId(Long memberId, Long roleId);
    List<ServerMemberRole> findByMemberId(Long memberId);
}
```

### src/main/java/com/zvonok/repository/ServerRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {
    boolean existsByInvitedCode(String invitedCode);
    Optional<Server> findByInvitedCode(String invitedCode);
    // Серверы пользователя
    @Query("""
        SELECT s FROM Server s 
        JOIN ServerMember sm ON sm.server.id = s.id 
        WHERE sm.user.id = :userId 
        AND sm.isActive = true 
        AND s.isActive = true
        ORDER BY sm.joinedAt DESC
        """)
    List<Server> findServersByUserId(@Param("userId") Long userId);

    // Проверка владельца сервера
    @Query("""
        SELECT COUNT(s) > 0 FROM Server s 
        WHERE s.owner.id = :userId 
        AND s.id = :serverId
        """)
    boolean isServerOwner(@Param("userId") Long userId, @Param("serverId") Long serverId);
}
```

### src/main/java/com/zvonok/repository/ServerRoleRepository.java
```java
package com.zvonok.repository;

import com.zvonok.model.ServerRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerRoleRepository extends JpaRepository<ServerRole, Long> {
    Optional<ServerRole> findByServerIdAndIsEveryoneTrue(Long serverId);
    List<ServerRole> findByServerIdAndIsActiveTrueOrderByPositionDesc(Long serverId);
    Optional<ServerRole> findByIdAndServerId(Long roleId, Long serverId);
}
```

### src/main/java/com/zvonok/service/ChannelFolderService.java
```java
package com.zvonok.service;

import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.Server;
import com.zvonok.model.User;
import com.zvonok.repository.ChannelFolderRepository;
import com.zvonok.service.dto.CreateChannelFolderDto;
import com.zvonok.service.dto.UpdateChannelFolderDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing channel folders within servers. Сервис для управления папками каналов в
 * серверах.
 */
@Service
public class ChannelFolderService {

	private final ChannelFolderRepository folderRepository;
	private final ServerService serverService;
	private final PermissionService permissionService;
	private final UserService userService;

	public ChannelFolderService(ChannelFolderRepository folderRepository,
			@Lazy ServerService serverService, @Lazy PermissionService permissionService,
			UserService userService) {
		this.folderRepository = folderRepository;
		this.serverService = serverService;
		this.permissionService = permissionService;
		this.userService = userService;
	}

	public ChannelFolder getChannelFolder(Long id, String username) {
		User user = userService.getUser(username);
		ChannelFolder channelFolder = getChannelFolder(id);

		if (!permissionService.canUserViewFolder(user.getId(), channelFolder.getId())) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		return channelFolder;
	}

	private ChannelFolder getChannelFolder(Long id) {
		return folderRepository.findById(id).orElseThrow(() -> new ChannelNotFoundException(
				HttpResponseMessage.HTTP_CHANNEL_FOLDER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public List<ChannelFolder> getActiveChannelFolders(Long serverId, String username) {
		User user = userService.getUser(username);
		List<ChannelFolder> channelFolders =
				folderRepository.findByServerIdAndIsActiveTrueOrderByPosition(serverId);

		List<ChannelFolder> response = channelFolders.stream().filter(cf -> permissionService.canUserViewFolder(user.getId(), cf.getId())).toList();
		return response;
	}

	public ChannelFolder createChannelFolder(CreateChannelFolderDto createChannelFolderDto) {
		// Проверяем существование сервера через ServerService
		Server server = serverService.getServer(createChannelFolderDto.getServerId());

		ChannelFolder folder = new ChannelFolder();
		folder.setName(createChannelFolderDto.getName());
		folder.setServer(server);
		folder.setPosition(createChannelFolderDto.getPosition());
		folder.setCreatedAt(LocalDateTime.now());

		return folderRepository.save(folder);
	}

	public ChannelFolder getChannelFolderForServer(Long serverId, Long folderId) {
		return folderRepository.findByIdAndServerId(folderId, serverId)
				.orElseThrow(() -> new ChannelNotFoundException(
						HttpResponseMessage.HTTP_CHANNEL_FOLDER_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));
	}

	@Transactional
	public ChannelFolder updateChannelFolder(Long folderId,
			UpdateChannelFolderDto updateChannelFolderDto) {
		ChannelFolder folder = getChannelFolder(folderId);

		if (updateChannelFolderDto.getName() != null) {
			folder.setName(updateChannelFolderDto.getName());
		}
		if (updateChannelFolderDto.getPosition() != null) {
			folder.setPosition(updateChannelFolderDto.getPosition());
		}
		if (updateChannelFolderDto.getCollapsed() != null) {
			folder.setCollapsed(updateChannelFolderDto.getCollapsed());
		}
		if (updateChannelFolderDto.getActive() != null) {
			folder.setIsActive(updateChannelFolderDto.getActive());
		}

		return folderRepository.save(folder);
	}

	@Transactional
	public void deleteChannelFolder(Long folderId) {
		ChannelFolder folder = getChannelFolder(folderId);
		folder.setIsActive(false);
		if (folder.getChannels() != null) {
			folder.getChannels().forEach(channel -> channel.setIsActive(false));
		}
		folderRepository.save(folder);
	}
}

```

### src/main/java/com/zvonok/service/ChannelService.java
```java
package com.zvonok.service;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.controller.dto.MessageAttachmentDto;
import com.zvonok.controller.dto.ReplyPreviewDto;
import com.zvonok.controller.dto.SenderDto;
import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.Message;
import com.zvonok.model.MessageAttachment;
import com.zvonok.model.User;
import com.zvonok.repository.ChannelRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.dto.CreateChannelDto;
import com.zvonok.service.dto.EventType;
import com.zvonok.service.dto.UpdateChannelDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing channels within channel folders. Сервис для управления каналами в папках
 * каналов.
 */
@Service
public class ChannelService {

	private final ChannelRepository channelRepository;
	private final MessageRepository messageRepository;
	private final ChannelFolderService channelFolderService;
	private final UserService userService;
	private final PermissionService permissionService;

	public ChannelService(ChannelRepository channelRepository, MessageRepository messageRepository,
			@Lazy ChannelFolderService channelFolderService, UserService userService,
			@Lazy PermissionService permissionService) {
		this.channelRepository = channelRepository;
		this.messageRepository = messageRepository;
		this.channelFolderService = channelFolderService;
		this.userService = userService;
		this.permissionService = permissionService;
	}

	public Channel getChannel(String username, Long folderId, Long channelId) {
		User user = userService.getUser(username);
		Channel channel = getChannel(folderId, channelId);

		if (!permissionService.canUserViewChannel(user.getId(), channel.getId())) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		return channel;
	}

	public Channel getChannelByIdInternal(Long id) {
		return channelRepository.findById(id).orElseThrow(() -> new ChannelNotFoundException(
				HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public Channel getChannel(Long folderId, Long channelId) {
		return channelRepository.findByIdAndFolderId(channelId, folderId)
				.orElseThrow(() -> new ChannelNotFoundException(
						HttpResponseMessage.HTTP_CHANNEL_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public List<Channel> getChannelsOrdered(Long folderId) {
		return channelRepository.findByFolderIdOrderByPosition(folderId).stream()
				.filter(Channel::getIsActive).collect(Collectors.toList());
	}

	public List<Channel> getChannelsByFolderId(Long folderId) {
		return channelRepository.findByFolderIdAndIsActiveTrue(folderId);
	}

	public Channel createChannel(CreateChannelDto createChannelDto, String username) {
		ChannelFolder folder =
				channelFolderService.getChannelFolder(createChannelDto.getFolderId(), username);

		Channel channel = new Channel();
		channel.setName(createChannelDto.getName());
		channel.setFolder(folder);
		channel.setType(createChannelDto.getType());
		channel.setPosition(createChannelDto.getPosition());
		channel.setTopic(createChannelDto.getTopic());
		channel.setCreatedAt(LocalDateTime.now());
		channel.setUserLimit(createChannelDto.getUserLimit());

		return channelRepository.save(channel);
	}

	public Channel updateChannel(Long channelId, UpdateChannelDto updateChannelDto) {
		Channel channel = getChannelByIdInternal(channelId);

		if (updateChannelDto.getName() != null) {
			channel.setName(updateChannelDto.getName());
		}
		if (updateChannelDto.getPosition() != null) {
			channel.setPosition(updateChannelDto.getPosition());
		}
		if (updateChannelDto.getUserLimit() != null) {
			channel.setUserLimit(updateChannelDto.getUserLimit());
		}
		if (updateChannelDto.getSlowModeSeconds() != null) {
			channel.setSlowModeSeconds(updateChannelDto.getSlowModeSeconds());
		}
		if (updateChannelDto.getTopic() != null) {
			channel.setTopic(updateChannelDto.getTopic());
		}
		if (updateChannelDto.getNsfw() != null) {
			channel.setNsfw(updateChannelDto.getNsfw());
		}
		if (updateChannelDto.getActive() != null) {
			channel.setIsActive(updateChannelDto.getActive());
		}

		return channelRepository.save(channel);
	}

	public void deleteChannel(Long channelId) {
		Channel channel = getChannelByIdInternal(channelId);
		channel.setIsActive(false);
		channelRepository.save(channel);
	}

	public List<ChannelMessageResponse> getChannelMessage(String username, Long folderId,
			Long channelId, Long beforeMessageId, int limit) {
		Channel channel = getChannel(username, folderId, channelId);
		PageRequest pageRequest = PageRequest.of(0, limit);
		Page<Message> page;

		if (beforeMessageId == null) {
			page = messageRepository.findByChannelIdAndDeletedAtIsNullOrderBySentAtDesc(channelId,
					pageRequest);
		} else {
			page = messageRepository.findByChannelIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
					channelId, beforeMessageId, pageRequest);
		}

		Map<Long, ReplyPreviewDto> replyPreviewByParentId =
				buildReplyPreviewByParentId(page.getContent());

		return page.getContent().stream().sorted(Comparator.comparing(Message::getId))
				.map(message -> mapToChannelMessageResponse(message, channel,
						replyPreviewByParentId.get(message.getReplyToMessageId())))
				.toList();
	}

	private ChannelMessageResponse mapToChannelMessageResponse(Message message, Channel channel,
			ReplyPreviewDto replyPreview) {
		SenderDto sender = new SenderDto(message.getSender().getId(),
				message.getSender().getUsername(), message.getSender().getDisplayName(),
				message.getSender().getAvatarUrl(), message.getSender().getStatus());

		ChannelMessageResponse response = new ChannelMessageResponse();
		response.setId(message.getId());
		response.setContent(message.getContent());
		response.setSender(sender);
		response.setSentAt(message.getSentAt());
		response.setType(message.getType());
		response.setChannelId(channel.getId());
		response.setEventType(EventType.MESSAGE);
		response.setReplyToMessageId(message.getReplyToMessageId());
		response.setReplyPreview(replyPreview);
		response.setEditedAt(message.getEditedAt());
		response.setAttachments(mapAttachments(message));
		return response;
	}

	private List<MessageAttachmentDto> mapAttachments(Message message) {
		if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
			return List.of();
		}
		return message.getAttachments().stream()
				.map(this::toAttachmentDto)
				.toList();
	}

	private MessageAttachmentDto toAttachmentDto(MessageAttachment attachment) {
		String downloadUrl = "/api/message-attachments/" + attachment.getId() + "/download";
		return new MessageAttachmentDto(attachment.getId(), attachment.getType(), downloadUrl,
				downloadUrl, attachment.getOriginalFileName(), attachment.getContentType(),
				attachment.getSizeBytes(), attachment.getWidth(), attachment.getHeight(),
				attachment.getDurationMs(), attachment.getWaveformJson());
	}

	private Map<Long, ReplyPreviewDto> buildReplyPreviewByParentId(List<Message> messages) {
		Set<Long> replyToIds = new HashSet<>();
		for (Message message : messages) {
			if (message.getReplyToMessageId() != null) {
				replyToIds.add(message.getReplyToMessageId());
			}
		}

		if (replyToIds.isEmpty()) {
			return Map.of();
		}

		Map<Long, Message> parentById = new HashMap<>();
		for (Message parent : messageRepository.findAllById(replyToIds)) {
			parentById.put(parent.getId(), parent);
		}

		Map<Long, ReplyPreviewDto> previewByParentId = new HashMap<>();
		for (Long replyToId : replyToIds) {
			Message parent = parentById.get(replyToId);
			if (parent == null) {
				previewByParentId.put(replyToId,
						new ReplyPreviewDto(replyToId, null, null, null, null, null, true));
				continue;
			}

			boolean deleted = parent.isDeleted();
			String snippet = deleted ? null : buildSnippet(parent.getContent());
			previewByParentId.put(replyToId,
					new ReplyPreviewDto(parent.getId(), parent.getSender().getId(),
							parent.getSender().getUsername(), parent.getSender().getDisplayName(),
							snippet, parent.getType(), deleted));
		}

		return previewByParentId;
	}

	private String buildSnippet(String content) {
		if (content == null) {
			return null;
		}

		String trimmed = content.trim();
		int maxLength = 120;
		if (trimmed.length() <= maxLength) {
			return trimmed;
		}

		return trimmed.substring(0, maxLength) + "...";
	}
}


```

### src/main/java/com/zvonok/service/ServerBanService.java
```java
package com.zvonok.service;

import com.zvonok.exception.ServerBanNotFoundException;
import com.zvonok.exception.ServerMemberNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Server;
import com.zvonok.model.ServerBan;
import com.zvonok.model.ServerMember;
import com.zvonok.model.User;
import com.zvonok.repository.ServerBanRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServerBanService {

    private final ServerBanRepository serverBanRepository;
    private final ServerService serverService;
    private final UserService userService;
    private final ServerMemberService serverMemberService;

    public ServerBanService(
            ServerBanRepository serverBanRepository,
            @Lazy ServerService serverService,
            UserService userService,
            ServerMemberService serverMemberService) {
        this.serverBanRepository = serverBanRepository;
        this.serverService = serverService;
        this.userService = userService;
        this.serverMemberService = serverMemberService;
    }

    @Transactional
    public boolean isUserBanned(Long serverId, Long userId) {
        Optional<ServerBan> optionalBan = serverBanRepository.findByServerIdAndUserIdAndActiveTrue(serverId, userId);
        if (optionalBan.isEmpty()) {
            return false;
        }

        ServerBan ban = optionalBan.get();
        if (ban.getExpiresAt() != null && ban.getExpiresAt().isBefore(LocalDateTime.now())) {
            // бан истек - снимаем
            deactivateBan(ban, null);
            return false;
        }

        return true;
    }

    @Transactional
    public List<ServerBan> getActiveBans(Long serverId) {
        List<ServerBan> bans = serverBanRepository.findByServerIdAndActiveTrue(serverId);
        return bans.stream()
                .filter(ban -> {
                    if (ban.getExpiresAt() != null && ban.getExpiresAt().isBefore(LocalDateTime.now())) {
                        deactivateBan(ban, null);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ServerBan banUser(Long serverId, Long targetUserId, Long actorUserId, String reason, LocalDateTime expiresAt) {
        Server server = serverService.getServer(serverId);
        User targetUser = userService.getUser(targetUserId);
        User actor = userService.getUser(actorUserId);

        Optional<ServerBan> existingBanOpt = serverBanRepository.findByServerIdAndUserIdAndActiveTrue(serverId, targetUserId);
        ServerBan ban = existingBanOpt.orElseGet(ServerBan::new);

        ban.setServer(server);
        ban.setUser(targetUser);
        ban.setBannedBy(actor);
        ban.setReason(reason);
        ban.setCreatedAt(LocalDateTime.now());
        ban.setExpiresAt(expiresAt);
        ban.setActive(true);
        ban.setUnbannedAt(null);
        ban.setUnbannedBy(null);

        ServerBan savedBan = serverBanRepository.save(ban);

        // деактивируем участника, если он есть
        try {
            ServerMember member = serverMemberService.getServerMember(targetUserId, serverId);
            member.setIsActive(false);
            member.setLeftAt(LocalDateTime.now());
            serverMemberService.updateServerMember(member);
        } catch (ServerMemberNotFoundException ignored) {
            // Пользователь мог не быть участником - пропускаем
        }

        return savedBan;
    }

    @Transactional
    public void unbanUser(Long serverId, Long targetUserId, Long actorUserId) {
        ServerBan ban = serverBanRepository.findByServerIdAndUserIdAndActiveTrue(serverId, targetUserId)
                .orElseThrow(() -> new ServerBanNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_BAN_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));

        User actor = userService.getUser(actorUserId);
        deactivateBan(ban, actor);
    }

    private void deactivateBan(ServerBan ban, User unbannedBy) {
        ban.setActive(false);
        ban.setUnbannedAt(LocalDateTime.now());
        ban.setUnbannedBy(unbannedBy);
        serverBanRepository.save(ban);
    }
}


```

### src/main/java/com/zvonok/service/ServerMemberRoleService.java
```java
package com.zvonok.service;

import com.zvonok.exception.ServerMemberRoleNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.ServerMember;
import com.zvonok.model.ServerMemberRole;
import com.zvonok.model.ServerRole;
import com.zvonok.repository.ServerMemberRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing relationships between server members and roles.
 * Сервис для управления связями между участниками сервера и ролями.
 */
@RequiredArgsConstructor
@Service
public class ServerMemberRoleService {

    private final ServerMemberRoleRepository serverMemberRoleRepository;
    private final ServerMemberService serverMemberService;
    private final ServerRoleService serverRoleService;
    private final UserService userService;

    public ServerMemberRole getServerMemberRole(Long serverMemberRoleId) {
        return serverMemberRoleRepository.findById(serverMemberRoleId)
                .orElseThrow(() -> new ServerMemberRoleNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_ROLE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public ServerMemberRole createServerMemberRole(Long serverMemberId, Long serverRoleId, Long assignedById) {
        ServerMember member = serverMemberService.getServerMember(serverMemberId);
        ServerRole role = serverRoleService.getServerRole(serverRoleId);

        if (hasRoleAssigned(member.getId(), role.getId())) {
            return serverMemberRoleRepository.findByMemberIdAndRoleId(member.getId(), role.getId()).get();
        }

        ServerMemberRole serverMemberRole = new ServerMemberRole();
        serverMemberRole.setMember(member);
        serverMemberRole.setRole(role);
        serverMemberRole.setAssignedAt(LocalDateTime.now());
        serverMemberRole.setAssignedBy(userService.getUser(assignedById));

        ServerMemberRole saved = serverMemberRoleRepository.save(serverMemberRole);
        member.getMemberRoles().add(saved);
        return saved;
    }

    public ServerMemberRole createServerMemberRole(ServerMember member, ServerRole role, Long assignedById) {
        if (hasRoleAssigned(member.getId(), role.getId())) {
            return serverMemberRoleRepository.findByMemberIdAndRoleId(member.getId(), role.getId()).get();
        }

        ServerMemberRole serverMemberRole = new ServerMemberRole();
        serverMemberRole.setMember(member);
        serverMemberRole.setRole(role);
        serverMemberRole.setAssignedAt(LocalDateTime.now());
        serverMemberRole.setAssignedBy(userService.getUser(assignedById));

        ServerMemberRole saved = serverMemberRoleRepository.save(serverMemberRole);
        member.getMemberRoles().add(saved);
        return saved;
    }

    @Transactional
    public void removeRoleFromMember(Long memberId, Long roleId) {
        ServerMemberRole memberRole = serverMemberRoleRepository.findByMemberIdAndRoleId(memberId, roleId)
                .orElseThrow(() -> new ServerMemberRoleNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_ROLE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
        memberRole.getMember().getMemberRoles().removeIf(mr -> mr.getId().equals(memberRole.getId()));
        serverMemberRoleRepository.delete(memberRole);
    }

    public List<Long> getMemberRoleIds(Long memberId) {
        return serverMemberRoleRepository.findByMemberId(memberId).stream()
                .map(memberRole -> memberRole.getRole().getId())
                .collect(Collectors.toList());
    }

    public boolean hasRoleAssigned(Long memberId, Long roleId) {
        return serverMemberRoleRepository.findByMemberIdAndRoleId(memberId, roleId).isPresent();
    }
}

```

### src/main/java/com/zvonok/service/ServerMemberService.java
```java
package com.zvonok.service;

import com.zvonok.exception.ServerMemberNotFoundException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Server;
import com.zvonok.model.ServerMember;
import com.zvonok.model.User;
import com.zvonok.repository.ServerMemberRepository;
import com.zvonok.service.dto.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing server members and member-related operations.
 * Сервис для управления участниками сервера и операциями, связанными с участниками.
 */
@RequiredArgsConstructor
@Service
public class ServerMemberService {

    private final ServerMemberRepository serverMemberRepository;
    private final UserService userService;

    public ServerMember getServerMember(Long id) {
        return serverMemberRepository.findById(id)
                .orElseThrow(() -> new ServerMemberNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public ServerMember getServerMember(Long userId, Long serverId) {
        return serverMemberRepository.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new ServerMemberNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public ServerMember findServerMemberOrNull(Long userId, Long serverId) {
        return serverMemberRepository.findByUserIdAndServerId(userId, serverId).orElse(null);
    }

	public ServerMember getNotActiveServerMember(Long userId, Long serverId) {
        return serverMemberRepository.findByUserIdAndServerIdAndIsActiveFalse(userId, serverId)
                .orElseThrow(() -> new ServerMemberNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}
	
    public ServerMember getActiveServerMember(Long userId, Long serverId) {
        return serverMemberRepository.findByUserIdAndServerIdAndIsActiveTrue(userId, serverId)
                .orElseThrow(() -> new ServerMemberNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_MEMBER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public ServerMember updateServerMember(ServerMember serverMember) {
        return serverMemberRepository.save(serverMember);
    }

    public ServerMember createServerMember(Server server, Long userId) {
        User user = userService.getUser(userId);

        ServerMember member = new ServerMember();
        member.setUser(user);
        member.setServer(server);
        member.setPersonalPermissions(Permission.NOTHING.getValue());
        member.setJoinedAt(LocalDateTime.now());

        return serverMemberRepository.save(member);
    }

    public ServerMember createServerMember(Server server, User user) {
        ServerMember member = new ServerMember();
        member.setUser(user);
        member.setServer(server);
        member.setPersonalPermissions(Permission.NOTHING.getValue());
        member.setJoinedAt(LocalDateTime.now());

        return serverMemberRepository.save(member);
    }

    public long countServerMembers(Long serverId) {
        return serverMemberRepository.countByServerIdAndIsActiveTrue(serverId);
    }

    public List<ServerMember> getAllActiveMember(Long serverId) {
        return serverMemberRepository.findByServerIdAndIsActiveTrue(serverId);
    }

    public ServerMember updateNickname(Long memberId, String nickname) {
        ServerMember member = getServerMember(memberId);
        member.setNickname(nickname);
        return serverMemberRepository.save(member);
    }
}

```

### src/main/java/com/zvonok/service/ServerRoleService.java
```java
package com.zvonok.service;

import com.zvonok.exception.CannotDeleteEveryoneRoleException;
import com.zvonok.exception.CannotDisableEveryoneRoleException;
import com.zvonok.exception.ServerRoleNotFoundException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.ServerRole;
import com.zvonok.repository.ServerRoleRepository;
import com.zvonok.service.dto.CreateServerRoleDto;
import com.zvonok.service.dto.UpdateServerRoleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing server roles and role-related operations.
 * Сервис для управления ролями сервера и операциями, связанными с ролями.
 */
@RequiredArgsConstructor
@Service
public class ServerRoleService {

    private final ServerRoleRepository serverRoleRepository;

    public ServerRole getServerRole(Long id) {
        return serverRoleRepository.findById(id)
                .orElseThrow(() -> new ServerRoleNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_ROLE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public ServerRole createServerRole(CreateServerRoleDto createServerRoleDto) {
        ServerRole role = new ServerRole();
        role.setName(createServerRoleDto.getName());
        role.setColor(createServerRoleDto.getColor());
        role.setPosition(createServerRoleDto.getPosition());
        role.setServerPermissions(createServerRoleDto.getServerPermissions());
        role.setIsEveryone(createServerRoleDto.isEveryone());
        role.setMentionable(createServerRoleDto.isMentionable());
        role.setServer(createServerRoleDto.getServer());
        role.setCreatedAt(LocalDateTime.now());

        return serverRoleRepository.save(role);
    }

    public ServerRole getServerRoleWithIsEveryoneTrue(Long serverId) {
        return serverRoleRepository.findByServerIdAndIsEveryoneTrue(serverId)
                .orElseThrow(() -> new ServerRoleNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_ROLE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public List<ServerRole> getActiveServerRoles(Long serverId) {
        return serverRoleRepository.findByServerIdAndIsActiveTrueOrderByPositionDesc(serverId);
    }

    public ServerRole getServerRoleForServer(Long serverId, Long roleId) {
        return serverRoleRepository.findByIdAndServerId(roleId, serverId)
                .orElseThrow(() -> new ServerRoleNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_ROLE_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    @Transactional
    public ServerRole updateServerRole(Long roleId, UpdateServerRoleDto updateServerRoleDto) {
        ServerRole role = getServerRole(roleId);

        if (updateServerRoleDto.getName() != null) {
            role.setName(updateServerRoleDto.getName());
        }
        if (updateServerRoleDto.getColor() != null) {
            role.setColor(updateServerRoleDto.getColor());
        }
        if (updateServerRoleDto.getPosition() != null) {
            role.setPosition(updateServerRoleDto.getPosition());
        }
        if (updateServerRoleDto.getServerPermissions() != null) {
            role.setServerPermissions(updateServerRoleDto.getServerPermissions());
        }
        if (updateServerRoleDto.getMentionable() != null) {
            role.setMentionable(updateServerRoleDto.getMentionable());
        }
        if (updateServerRoleDto.getActive() != null) {
            if (Boolean.FALSE.equals(updateServerRoleDto.getActive()) && role.getIsEveryone()) {
                throw new CannotDisableEveryoneRoleException(
                        BusinessRuleMessage.BUSINESS_CANNOT_DISABLE_EVERYONE_ROLE_MESSAGE.getMessage());
            }
            role.setIsActive(updateServerRoleDto.getActive());
        }

        return serverRoleRepository.save(role);
    }

    @Transactional
    public void deleteServerRole(Long roleId) {
        ServerRole role = getServerRole(roleId);
        if (role.getIsEveryone()) {
            throw new CannotDeleteEveryoneRoleException(
                    BusinessRuleMessage.BUSINESS_CANNOT_DELETE_EVERYONE_ROLE_MESSAGE.getMessage());
        }
        role.setIsActive(false);
        serverRoleRepository.save(role);
    }
}

```

### src/main/java/com/zvonok/service/ServerService.java
```java
package com.zvonok.service;

import com.zvonok.exception.*;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.logging.LogEvent;
import com.zvonok.logging.LogEventConstants;
import com.zvonok.logging.LogTimingUtils;
import com.zvonok.model.*;
import com.zvonok.model.enumeration.ChannelType;
import com.zvonok.repository.*;
import com.zvonok.service.dto.CreateChannelDto;
import com.zvonok.service.dto.CreateChannelFolderDto;
import com.zvonok.service.dto.CreateServerRoleDto;
import com.zvonok.service.dto.Permission;
import com.zvonok.service.dto.request.CreateServerRequest;
import com.zvonok.service.dto.request.UpdateServerRequest;
import com.zvonok.service.dto.response.ServerResponse;
import lombok.extern.slf4j.Slf4j;
import com.zvonok.service.dto.response.ServerMemberResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing servers, server members, and server-related operations.
 * Сервис для
 * управления серверами, участниками серверов и операциями, связанными с
 * серверами.
 */
@Service
@Slf4j
public class ServerService {

	private final ServerRepository serverRepository;
	private final UserService userService;
	private final InviteCodeService inviteCodeService;
	private final PermissionService permissionService;
	private final ServerMemberService serverMemberService;
	private final ServerMemberRoleService serverMemberRoleService;
	private final ServerRoleService serverRoleService;
	private final ChannelService channelService;
	private final ChannelFolderService channelFolderService;
	private final ServerBanService serverBanService;

	public ServerService(ServerRepository serverRepository, UserService userService,
			InviteCodeService inviteCodeService, PermissionService permissionService,
			ServerMemberService serverMemberService,
			ServerMemberRoleService serverMemberRoleService, ServerRoleService serverRoleService,
			@Lazy ChannelService channelService, @Lazy ChannelFolderService channelFolderService,
			@Lazy ServerBanService serverBanService) {
		this.serverRepository = serverRepository;
		this.userService = userService;
		this.inviteCodeService = inviteCodeService;
		this.permissionService = permissionService;
		this.serverMemberService = serverMemberService;
		this.serverMemberRoleService = serverMemberRoleService;
		this.serverRoleService = serverRoleService;
		this.channelService = channelService;
		this.channelFolderService = channelFolderService;
		this.serverBanService = serverBanService;
	}

	@Transactional
	public ServerResponse createServer(CreateServerRequest request, Long ownerId) {
		long durationStart = System.currentTimeMillis();

		Server savedServer = null;

		try {

			validateCreateServerRequest(request);
			System.out.println(request.isPrivate());
			User owner = userService.getUser(ownerId);

			// Создаем сервер
			Server server = new Server();
			server.setName(request.getName());
			server.setInvitedCode(inviteCodeService.generateUniqueInviteCode());
			server.setOwner(owner);
			server.setMaxMember(request.getMaxMembers() != null ? request.getMaxMembers() : 1000);
			server.setCreatedAt(LocalDateTime.now());
			server.setPrivate(request.isPrivate());

			savedServer = serverRepository.save(server);

			// Создаем роли по умолчанию
			createEveryoneRole(savedServer);
			ServerRole ownerRole = createOwnerRole(savedServer);

			// Добавляем владельца как участника
			ServerMember ownerMember = addOwnerAsMember(savedServer, owner);
			assignRoleToMember(ownerMember, ownerRole, ownerId);

			// Создаем дефолтную папку и каналы
			ChannelFolder defaultFolder = createDefaultFolder(savedServer.getId());
			createDefaultChannels(defaultFolder.getId(), owner.getUsername());

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_CREATE_SERVER_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.serverId(savedServer.getId()).userId(ownerId).build());
		} catch (InvalidServerNameException | InvalidServerMaxMemberException
				| ServerNotFoundException | ChannelNotFoundException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_CREATE_SERVER_ACTION, durationStart, ownerId, savedServer,
					false);
			throw e;
		} catch (Exception e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_CREATE_SERVER_ACTION, durationStart, ownerId, savedServer,
					true);
			throw e;
		}

		return mapToResponse(savedServer);
	}

	public Server getServer(Long serverId) {
		return serverRepository.findById(serverId).orElseThrow(() -> new ServerNotFoundException(
				HttpResponseMessage.HTTP_SERVER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public List<ServerResponse> getUserServers(Long userId) {
		List<Server> servers = serverRepository.findServersByUserId(userId);
		return servers.stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	public ServerResponse getServerResponse(Long serverId) {
		Server server = getServer(serverId);
		return mapToResponse(server);
	}

	@Transactional
	public ServerResponse joinServerByInviteCode(String inviteCode, Long userId) {
		long durationStart = System.currentTimeMillis();

		Server server = null;
		try {
			server = serverRepository.findByInvitedCode(inviteCode)
					.orElseThrow(() -> new ServerNotFoundException(
							HttpResponseMessage.HTTP_SERVER_NOT_FOUND_RESPONSE_MESSAGE
									.getMessage()));

			if (!server.getIsActive()) {
				throw new ServerNotFoundException(
						HttpResponseMessage.HTTP_SERVER_NOT_ACTIVE_RESPONSE_MESSAGE.getMessage());
			}

			User user = userService.getUser(userId);

			if (serverBanService.isUserBanned(server.getId(), userId)) {
				throw new UserBannedException(
						HttpResponseMessage.HTTP_USER_BANNED_RESPONSE_MESSAGE.getMessage());
			}

			// Проверяем не является ли уже участником
			ServerMember member = serverMemberService.findServerMemberOrNull(userId, server.getId());
			if (member != null) {
				if (!member.getIsActive()) {
					member.setIsActive(true);
					serverMemberService.updateServerMember(member);
				} else {
					throw new YouAlreadyMemberThisServerException(
							HttpResponseMessage.HTTP_YOU_ALREADY_MEMBER_THIS_SERVER_RESPONSE_MESSAGE
									.getMessage());
				}
			}

			// Проверяем лимит участников
			long memberCount = serverMemberService.countServerMembers(server.getId());
			if (memberCount >= server.getMaxMember()) {
				throw new ServerMemberLimitReachedException(
						BusinessRuleMessage.BUSINESS_SERVER_MEMBER_LIMIT_REACHED_MESSAGE
								.getMessage());
			}

			// Добавляем как участника
			ServerMember newMember = addUserAsMember(server, user);

			// Назначаем роль @everyone
			ServerRole everyoneRole = serverRoleService.getServerRoleWithIsEveryoneTrue(server.getId());
			assignRoleToMember(newMember, everyoneRole, userId);

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.serverId(server.getId()).userId(userId).build());

		} catch (ServerNotFoundException | UserBannedException
				| ServerMemberLimitReachedException | ServerRoleNotFoundException
				| YouAlreadyMemberThisServerException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION, durationStart, userId, server,
					false);
			throw e;
		} catch (Exception e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION, durationStart, userId, server,
					true);
			throw e;
		}

		return mapToResponse(server);
	}

	@Transactional
	public ServerResponse updateServer(Long serverId, UpdateServerRequest request, Long userId) {
		Server updatedServer = null;
		long durationStart = System.currentTimeMillis();

		try {
			Server server = getServer(serverId);

			// Проверяем права на управление сервером
			if (!canManageServer(userId, serverId)) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			if (request.getName() != null) {
				server.setName(request.getName());
			}

			if (request.getMaxMembers() != null) {
				server.setMaxMember(request.getMaxMembers());
			}

			updatedServer = serverRepository.save(server);

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_UPDATE_SERVER_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(userId)
					.build());
		} catch (ServerNotFoundException | InsufficientPermissionsException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_UPDATE_SERVER_ACTION, durationStart, userId, updatedServer,
					false);
			throw e;
		} catch (Exception e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_UPDATE_SERVER_ACTION, durationStart, userId, updatedServer,
					true);
			throw e;
		}
		return mapToResponse(updatedServer);
	}

	@Transactional
	public void deleteServer(Long serverId, Long userId) {
		long durationStart = System.currentTimeMillis();

		try {
			Server server = getServer(serverId);

			// Проверяем, что пользователь является владельцем
			if (!server.getOwner().getId().equals(userId)) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			// Удаляем сервер
			serverRepository.delete(server);

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_DELETE_SERVER_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(userId)
					.build());
		} catch (ServerNotFoundException | InsufficientPermissionsException e) {
			log.warn("{}", LogEvent.buildFailedEvent(LogEventConstants.EVENT_DELETE_SERVER_ACTION, e.getMessage(),
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(userId));
			throw e;
		} catch (Exception e) {
			log.error("{}", LogEvent.buildFailedEvent(LogEventConstants.EVENT_DELETE_SERVER_ACTION, e.getMessage(),
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(userId));
			throw e;
		}
	}

	@Transactional
	public String regenerateInviteCode(Long serverId, Long userId) {
		Server server = getServer(serverId);

		if (!canManageServer(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		String newInviteCode = inviteCodeService.generateUniqueInviteCode();
		server.setInvitedCode(newInviteCode);
		serverRepository.save(server);

		return newInviteCode;
	}

	@Transactional
	public void leaveServer(Long serverId, Long userId) {
		long durationStart = System.currentTimeMillis();

		try {
			ServerMember member = serverMemberService.getActiveServerMember(userId, serverId);

			Server server = getServer(serverId);

			// Владелец не может покинуть сервер
			if (server.getOwner().getId().equals(userId)) {
				throw new OwnerCanNotLeaveServerException(
						HttpResponseMessage.HTTP_OWNER_CAN_NOT_LEAVE_SERVER_RESPONSE_MESSAGE
								.getMessage());
			}

			// ServerMember member = serverMemberService.getServerMember(userId, serverId);

			member.setIsActive(false);
			member.setLeftAt(LocalDateTime.now());
			serverMemberService.updateServerMember(member);

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_LEAVE_SERVER_ACION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.serverId(serverId).userId(userId).build());
		} catch (ServerMemberNotFoundException | OwnerCanNotLeaveServerException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_LEAVE_SERVER_ACION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(userId).build());
			throw e;
		} catch (Exception e) {
			log.error("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_LEAVE_SERVER_ACION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(userId).build());
			throw e;
		}
	}

	public List<ServerMemberResponse> getServerMembers(Long serverId, Long userId) {
		getServer(serverId);

		// Проверяем может ли пользователь видеть участников
		if (!canViewMembers(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		List<ServerMember> members = serverMemberService.getAllActiveMember(serverId);
		return members.stream().map(this::mapToMemberResponse).collect(Collectors.toList());
	}

	@Transactional
	public void kickMember(Long serverId, Long targetUserId, Long kickerUserId) {
		long durationStart = System.currentTimeMillis();

		try {
			Server server = getServer(serverId);

			// Проверяем права на исключение
			if (!canKickMembers(kickerUserId, serverId)) {
				throw new InsufficientPermissionsException(
						HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
								.getMessage());
			}

			// Нельзя исключить владельца
			if (server.getOwner().getId().equals(targetUserId)) {
				throw new CannotKickServerOwnerException(
						BusinessRuleMessage.BUSINESS_CANNOT_KICK_SERVER_OWNER_MESSAGE.getMessage());
			}

			// Нельзя исключить самого себя
			if (kickerUserId.equals(targetUserId)) {
				throw new CannotKickYourselfException(
						BusinessRuleMessage.BUSINESS_CANNOT_KICK_SELF_MESSAGE.getMessage());
			}

			ServerMember targetMember = serverMemberService.getServerMember(targetUserId, serverId);

			if (!targetMember.getIsActive()) {
				throw new ServerMemberAlreadyWasKicked(
						HttpResponseMessage.HTTP_SERVER_MEMBER_ALREADY_WAS_KICKED_RESPONSE_MESSAGE
								.getMessage());
			}

			targetMember.setIsActive(false);
			targetMember.setLeftAt(LocalDateTime.now());
			serverMemberService.updateServerMember(targetMember);

			log.info("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION,
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(targetUserId)
					.build());
		} catch (ServerNotFoundException | InsufficientPermissionsException
				| CannotKickServerOwnerException | CannotKickYourselfException
				| ServerMemberNotFoundException | ServerMemberAlreadyWasKicked e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (Exception e) {
			log.error("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		}
	}

	@Transactional
	public ServerMemberResponse updateMemberNickname(Long serverId, Long targetUserId,
			String nickname, Long actorUserId) {
		// Убедимся, что сервер существует
		getServer(serverId);

		// Получаем участников
		ServerMember actorMember = serverMemberService.getServerMember(actorUserId, serverId);
		ServerMember targetMember = serverMemberService.getServerMember(targetUserId, serverId);

		boolean isSelfUpdate = targetMember.getUser().getId().equals(actorMember.getUser().getId());
		if (!isSelfUpdate && !permissionService.hasPermissionInServer(actorUserId, serverId,
				Permission.MANAGE_SERVER)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}

		String processedNickname = StringUtils.hasText(nickname) ? nickname.trim() : null;
		targetMember.setNickname(processedNickname);
		serverMemberService.updateServerMember(targetMember);

		return mapToMemberResponse(targetMember);
	}

	public boolean hasAccessToServer(Long userId, Long serverId) {
		return permissionService.isServerMember(userId, serverId);
	}

	public void hasAccessToServerAndThrowExceptionIfFalse(Long userId, Long serverId) {
		if (!hasAccessToServer(userId, serverId)) {
			throw new InsufficientPermissionsException(
					HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	// ===== PRIVATE HELPER METHODS =====

	private void validateCreateServerRequest(CreateServerRequest request) {
		if (request.getName().length() < 5 || request.getName().length() > 100) {
			throw new InvalidServerNameException(
					HttpResponseMessage.HTTP_SERVER_NAME_NOT_VALID_RESPONSE_MESSAGE.getMessage());
		}

		if (request.getMaxMembers() != null
				&& (request.getMaxMembers() < 10 || request.getMaxMembers() > 10000)) {
			throw new InvalidServerMaxMemberException(
					HttpResponseMessage.HTTP_SERVER_MAX_MEMBERS_NOT_VALID_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private ServerRole createEveryoneRole(Server server) {
		CreateServerRoleDto createDto = new CreateServerRoleDto();
		createDto.setName("everyone");
		createDto.setColor("#ffffff");
		createDto.setPosition(0);
		createDto.setServerPermissions(Permission.VIEW_CHANNEL.getValue()
				| Permission.SEND_MESSAGES.getValue() | Permission.READ_MESSAGE_HISTORY.getValue());
		createDto.setEveryone(true);
		createDto.setMentionable(false);
		createDto.setServer(server);

		return serverRoleService.createServerRole(createDto);
	}

	private ServerRole createOwnerRole(Server server) {
		CreateServerRoleDto createDto = new CreateServerRoleDto();
		createDto.setName("Owner");
		createDto.setColor("#ff0000");
		createDto.setPosition(1000);
		createDto.setServerPermissions(Permission.ADMINISTRATOR.getValue());
		createDto.setEveryone(false);
		createDto.setMentionable(true);
		createDto.setServer(server);

		return serverRoleService.createServerRole(createDto);
	}

	private ServerMember addOwnerAsMember(Server server, User owner) {
		return serverMemberService.createServerMember(server, owner);
	}

	private ServerMember addUserAsMember(Server server, User user) {
		return serverMemberService.createServerMember(server, user);
	}

	private void assignRoleToMember(ServerMember member, ServerRole role, Long assignedById) {
		serverMemberRoleService.createServerMemberRole(member, role, assignedById);
	}

	private ChannelFolder createDefaultFolder(Long serverId) {
		CreateChannelFolderDto folder = new CreateChannelFolderDto();
		folder.setName("Основные каналы");
		folder.setServerId(serverId);
		folder.setPosition(0);

		return channelFolderService.createChannelFolder(folder);
	}

	private void createDefaultChannels(Long folderId, String username) {
		// Общий канал
		CreateChannelDto generalChannel = new CreateChannelDto();
		generalChannel.setName("общий");
		generalChannel.setFolderId(folderId);
		generalChannel.setType(ChannelType.TEXT);
		generalChannel.setPosition(0);
		generalChannel.setUserLimit(10000);
		generalChannel.setTopic("Добро пожаловать на сервер!");

		// Голосовой канал
		CreateChannelDto voiceChannel = new CreateChannelDto();
		voiceChannel.setName("Голосовой канал");
		voiceChannel.setFolderId(folderId);
		voiceChannel.setType(ChannelType.VOICE);
		voiceChannel.setPosition(1);
		voiceChannel.setUserLimit(15);

		channelService.createChannel(generalChannel, username);
		channelService.createChannel(voiceChannel, username);
	}

	private void buildServerFailedLog(Exception e, String action, long durationStart, Long userId, Server server,
			boolean isErrorLog) {
		if (isErrorLog) {
			if (server == null) {
				log.error("{}", LogEvent
						.buildFailedEvent(action, e.getMessage(),
								LogTimingUtils.calculateDurationDifference(durationStart))
						.userId(userId).build());
			} else {
				log.error("{}", LogEvent
						.buildFailedEvent(action, e.getMessage(),
								LogTimingUtils.calculateDurationDifference(durationStart))
						.serverId(server.getId()).userId(userId).build());
			}

		} else {
			if (server == null) {
				log.warn("{}", LogEvent
						.buildFailedEvent(action, e.getMessage(),
								LogTimingUtils.calculateDurationDifference(durationStart))
						.userId(userId).build());
			} else {
				log.warn("{}", LogEvent
						.buildFailedEvent(action, e.getMessage(),
								LogTimingUtils.calculateDurationDifference(durationStart))
						.serverId(server.getId()).userId(userId).build());
			}
		}
	}

	// ===== PERMISSION CHECKS =====

	private boolean canManageServer(Long userId, Long serverId) {
		return permissionService.hasPermissionInServer(userId, serverId, Permission.MANAGE_SERVER)
				|| serverRepository.isServerOwner(userId, serverId);
	}

	private boolean canViewMembers(Long userId, Long serverId) {
		ServerMember member = serverMemberService.findServerMemberOrNull(userId, serverId);
		return member != null && member.getIsActive();
	}

	private boolean canKickMembers(Long userId, Long serverId) {
		return permissionService.hasPermissionInServer(userId, serverId, Permission.KICK_MEMBERS)
				|| serverRepository.isServerOwner(userId, serverId);
	}

	// ===== MAPPING METHODS =====

	public ServerResponse mapToResponse(Server server) {
		return ServerResponse.builder().id(server.getId()).name(server.getName())
				.inviteCode(server.getInvitedCode()).maxMembers(server.getMaxMember())
				.memberCount(serverMemberService.countServerMembers(server.getId()))
				.ownerId(server.getOwner().getId()).ownerName(server.getOwner().getUsername())
				.createdAt(server.getCreatedAt()).channelFolders(server.getChannelFolders())
				.bannerUrl(server.getBannerUrl()).isPrivate(server.isPrivate())
				.build();
	}

	private ServerMemberResponse mapToMemberResponse(ServerMember member) {
		List<String> roleNames = member.getMemberRoles().stream().map(mr -> mr.getRole().getName())
				.collect(Collectors.toList());

		return ServerMemberResponse.builder().userId(member.getUser().getId())
				.username(member.getUser().getUsername()).avatarUrl(member.getUser().getAvatarUrl())
				.nickname(member.getNickname()).joinedAt(member.getJoinedAt()).roles(roleNames)
				.isOwner(member.getServer().getOwner().getId().equals(member.getUser().getId()))
				.build();
	}
}

```

### src/main/java/com/zvonok/service/dto/CreateChannelDto.java
```java
package com.zvonok.service.dto;

import com.zvonok.model.enumeration.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "CreateChannelDto", description = "Запрос на создание канала в папке каналов.")
public class CreateChannelDto {

	@Schema(description = "Название канала.", example = "general", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(min = 3, max = 100, message = "Name must be between 3 and 100")
	private String name;

	@Schema(description = "ID папки каналов, в которой создаётся канал.", example = "15",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long folderId;

	@Schema(description = "Тип канала.", example = "TEXT", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private ChannelType type;

	@Schema(description = "Позиция канала в списке (чем меньше — тем выше).", example = "0",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull
	private Integer position;

	@Schema(description = "Лимит участников (актуально для голосовых каналов). По умолчанию 10.",
			example = "100", defaultValue = "100", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
@NotNull()
	private Integer userLimit = 100;

	@Schema(description = "Тема канала (короткое описание).", example = "Обсуждение задач")
	@NotBlank
	@Size(max = 2000, message = "topic length must be not more than 2000")
	private String topic;
}

```

### src/main/java/com/zvonok/service/dto/CreateChannelFolderDto.java
```java
package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "CreateChannelFolderDto",
		description = "Запрос на создание папки каналов на сервере.")
public class CreateChannelFolderDto {

	@Schema(description = "Название папки каналов.", example = "Текстовые каналы", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	private String name;

	@Schema(description = "ID сервера, в котором создаётся папка.", example = "42",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long serverId;

	@Schema(description = "Позиция папки в списке (чем меньше — тем выше). Если не передано, используется 0.",
			example = "0", defaultValue = "0", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Integer position;
}

```

### src/main/java/com/zvonok/service/dto/CreateServerMemberDto.java
```java
package com.zvonok.service.dto;

import com.zvonok.model.Server;
import com.zvonok.model.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateServerMemberDto {

    private User user;
    private Server server;
    private Permission PersonalPermission;
}

```

### src/main/java/com/zvonok/service/dto/CreateServerRoleDto.java
```java
package com.zvonok.service.dto;

import com.zvonok.model.Server;
import lombok.Data;

@Data
public class CreateServerRoleDto {

    private String name;
    private String color;
    private Integer position;
    private Long serverPermissions;
    private boolean isEveryone;
    private boolean mentionable;
    private Server server;
}

```

### src/main/java/com/zvonok/service/dto/UpdateChannelDto.java
```java
package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

@Data
@Schema(name = "UpdateChannelDto",
		description = "Данные для обновления канала. Передаются только изменяемые поля.")
public class UpdateChannelDto {
	@Schema(description = "Название канала.", example = "general", maxLength = 100, nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private String name;

	@Schema(description = "Позиция канала в списке (чем меньше — тем выше).", example = "0",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer position;

	@Schema(description = "Лимит участников.", example = "10", nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer userLimit;

	@Schema(description = "Slow mode: задержка между сообщениями в секундах.", example = "5",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Integer slowModeSeconds;

	@Schema(description = "Тема канала (короткое описание).", example = "Обсуждение релизов",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private String topic;

	@Schema(description = "NSFW-флаг (контент 18+).", example = "false", nullable = true,
			requiredMode = RequiredMode.NOT_REQUIRED)
	private Boolean nsfw;

	@Schema(description = "Активен ли канал (не скрыт/не архивирован).", example = "true",
			nullable = true, requiredMode = RequiredMode.NOT_REQUIRED)
	private Boolean active;
}


```

### src/main/java/com/zvonok/service/dto/UpdateChannelFolderDto.java
```java
package com.zvonok.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "UpdateChannelFolderDto",
		description = "Данные для обновления папки каналов. Все поля необязательны.")
public class UpdateChannelFolderDto {
	@Schema(description = "Название папки.", example = "Общее", nullable = true,
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String name;

	@Schema(description = "Позиция папки в списке (чем меньше — тем выше).", example = "0",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Integer position;

	@Schema(description = "Свернута ли папка у пользователя по умолчанию.", example = "false",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Boolean collapsed;

	@Schema(description = "Активна ли папка (не скрыта/не архивирована).", example = "true",
			nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private Boolean active;
}


```

### src/main/java/com/zvonok/service/dto/UpdateServerRoleDto.java
```java
package com.zvonok.service.dto;

import lombok.Data;

@Data
public class UpdateServerRoleDto {
    private String name;
    private String color;
    private Integer position;
    private Long serverPermissions;
    private Boolean mentionable;
    private Boolean active;
}


```

### src/main/java/com/zvonok/service/dto/request/CreateServerBanRequest.java
```java
package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(name = "CreateServerBanRequest", description = "Запрос на бан пользователя на сервере.")
public class CreateServerBanRequest {

	@Schema(description = "ID пользователя, которого нужно забанить.", example = "123",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long targetUserId;

	@Schema(description = "Причина бана (до 255 символов).", example = "Спам в чатах",
			maxLength = 255)
	@NotBlank
	@Size(max = 255, message = "reason length must be not more than 255 characters")
	private String reason;

	@Schema(description = "Дата и время окончания бана. Если не указано — бан бессрочный.",
			example = "2026-02-10T12:00:00", nullable = true)
	@Future(message = "The end date of the ban must be in the future")
	private LocalDateTime expiresAt;
}


```

### src/main/java/com/zvonok/service/dto/request/CreateServerRequest.java
```java
package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "CreateServerRequest", description = "Запрос на создание сервера.")
@Data
public class CreateServerRequest {
	@Schema(description = "Название сервера (5–100 символов).", example = "Zvonok Dev",
			minLength = 5, maxLength = 100, nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "Server name cannot be empty")
	@Size(min = 5, max = 100, message = "Name must be between 5 and 100 characters")
	private String name;

	@Schema(description = "Максимальное количество участников (10–10000).", example = "1000",
			minimum = "10", maximum = "10000", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "MaxMembers cannot be empty")
	@Min(value = 10, message = "maxMembers must be at least 10")
	@Max(10000)
	private Integer maxMembers;

	private boolean isPrivate; 
}

```

### src/main/java/com/zvonok/service/dto/request/CreateServerRoleRequest.java
```java
package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(name = "CreateServerRoleRequest", description = "Запрос на создание роли на сервере.")
@Data
public class CreateServerRoleRequest {
	@Schema(description = "Название роли (до 50 символов).", example = "moderator", maxLength = 50,
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50)
	private String name;

	@Schema(description = "Цвет роли в HEX формате #RRGGBB.", example = "#FFFFFF",
			pattern = "^#([A-Fa-f0-9]{6})$", defaultValue = "#FFFFFF", nullable = false,
			requiredMode = Schema.RequiredMode.REQUIRED)
	@Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a hex value like #FFFFFF")
	@NotBlank
	@Size(min = 7, max = 7, message = "color must be 7 characters long")
	private String color = "#FFFFFF";

	@Schema(description = "Позиция роли (чем больше — тем выше в списке).", example = "0",
			defaultValue = "0", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Integer position = 0;

	@Schema(description = "Битовая маска прав роли (permissions bitset).", example = "1024",
			nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private Long serverPermissions;

	@Schema(description = "Можно ли упоминать роль (ping).", example = "true",
			defaultValue = "true", nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private boolean mentionable = true;
}


```

### src/main/java/com/zvonok/service/dto/request/UpdateServerMemberNicknameRequest.java
```java
package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "UpdateServerMemberNicknameRequest",
		description = "Запрос на обновление никнейма участника на сервере.")
public class UpdateServerMemberNicknameRequest {

	@Schema(description = "Никнейм участника (до 50 символов).", example = "muratik",
			maxLength = 50, nullable = false, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50, message = "Nickname must be at most 50 characters")
	private String nickname;
}


```

### src/main/java/com/zvonok/service/dto/request/UpdateServerRequest.java
```java
package com.zvonok.service.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Schema(name = "UpdateServerRequest",
		description = "Данные для обновления настроек сервера. Поля опциональные: передаются только изменяемые.")
@Data
public class UpdateServerRequest {
	@Schema(description = "Название сервера (до 100 символов).", example = "Zvonok Dev",
			maxLength = 100, nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Size(max = 100, message = "Название сервера не может превышать 100 символов")
	private String name;

	@Schema(description = "Лимит участников сервера (от 10 до 10000).", example = "250",
			minimum = "10", maximum = "10000", nullable = true,
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Min(value = 10, message = "Минимальное количество участников: 10")
	@Max(value = 10000, message = "Максимальное количество участников: 10000")
	private Integer maxMembers;
}


```

### src/main/java/com/zvonok/service/dto/request/UpdateServerRoleRequest.java
```java
package com.zvonok.service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "UpdateServerRoleRequest",
		description = "Данные для обновления роли на сервере. Обычно передаются только изменяемые поля.")
public class UpdateServerRoleRequest {
	@Schema(description = "Название роли (до 50 символов).", example = "moderator", maxLength = 50)
	@Size(max = 50)
	private String name;

	@Schema(description = "Цвет роли в HEX формате #RRGGBB.", example = "#FFAA00",
			pattern = "^#([A-Fa-f0-9]{6})$")
	@Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a hex value like #FFFFFF")
	private String color;

	@Schema(description = "Позиция роли (чем больше — тем выше в списке).", minimum = "0",
			example = "10")
	private Integer position;

	@Schema(description = "Битовая маска прав роли (permissions bitset).", example = "1024")
	private Long serverPermissions;

	@Schema(description = "Можно ли упоминать роль (ping).", example = "true")
	private Boolean mentionable;

	@Schema(description = "Активна ли роль (не скрыта/не отключена).", example = "true")
	private Boolean active;
}


```

### src/main/java/com/zvonok/service/dto/response/ServerBanResponse.java
```java
package com.zvonok.service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(name = "ServerBanResponse", description = "Информация о бане пользователя на сервере.")
public class ServerBanResponse {

	@Schema(description = "ID забаненного пользователя.", example = "123",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long userId;

	@Schema(description = "Username забаненного пользователя.", example = "spammer42",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String username;

	@Schema(description = "Причина бана (может отсутствовать).", example = "Спам",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String reason;

	@Schema(description = "Дата и время, когда бан был выдан (ISO-8601).",
			example = "2026-02-01T17:10:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime createdAt;

	@Schema(description = "Дата и время окончания бана. Если null — бан бессрочный.",
			example = "2026-02-10T12:00:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime expiresAt;

	@Schema(description = "ID пользователя/модератора, который выдал бан.", example = "7",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long bannedById;

	@Schema(description = "Username пользователя/модератора, который выдал бан.", example = "admin",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String bannedByUsername;
}


```

### src/main/java/com/zvonok/service/dto/response/ServerMemberResponse.java
```java
package com.zvonok.service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(name = "ServerMemberResponse", description = "Ответ с данными участника сервера.")
public class ServerMemberResponse {

	@Schema(description = "ID пользователя.", example = "42",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long userId;

	@Schema(description = "Username пользователя.", example = "murat",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String username;

	@Schema(description = "URL аватара пользователя.",
			example = "https://cdn.zvonok.app/avatars/42.png",
			accessMode = Schema.AccessMode.READ_ONLY)
	private String avatarUrl;

	@Schema(description = "Никнейм пользователя на сервере (может отсутствовать).",
			example = "muratik", accessMode = Schema.AccessMode.READ_ONLY)
	private String nickname;

	@Schema(description = "Дата и время вступления на сервер (ISO-8601).",
			example = "2026-01-20T12:00:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime joinedAt;

	@Schema(description = "Список ролей участника.", example = "[\"user\", \"moderator\"]",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private List<String> roles;

	@Schema(description = "Является ли пользователь владельцем сервера.", example = "false",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Boolean isOwner;
}

```

### src/main/java/com/zvonok/service/dto/response/ServerResponse.java
```java
package com.zvonok.service.dto.response;

import com.zvonok.model.ChannelFolder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "ServerResponse",
		description = "Ответ с данными сервера (гильдии) и его структурой каналов.")
@Data
@Builder
public class ServerResponse {
	@Schema(description = "ID сервера.", example = "42", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Long id;

	@Schema(description = "Название сервера.", example = "Zvonok Dev",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Schema(description = "Инвайт-код сервера (используется для присоединения).",
			example = "Cmh6teSYDmmCKJH", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String inviteCode;

	@Schema(description = "Максимальное количество участников сервера.", example = "1000",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer maxMembers;

	@Schema(description = "Текущее количество участников.", example = "128",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long memberCount;

	@Schema(description = "ID владельца сервера.", example = "7",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long ownerId;

	@Schema(description = "Ник/имя владельца сервера.", example = "Igor",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String ownerName;

	@Schema(description = "Дата и время создания сервера.", example = "2026-02-01T12:34:56",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime createdAt;

	@Schema(description = "Список папок каналов на сервере.",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private List<ChannelFolder> channelFolders;

	private String bannerUrl;

	private boolean isPrivate;
}


```

### src/main/java/com/zvonok/model/enumeration/ChannelType.java
```java
package com.zvonok.model.enumeration;

public enum ChannelType {
    TEXT,
    VOICE,
    ANNOUNCEMENT
}
```

### src/main/java/com/zvonok/controller/dto/ChannelMessageResponse.java
```java
package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.MessageType;
import com.zvonok.service.dto.EventType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChannelMessageResponse {
    private Long id;
    private String content;
	private SenderDto sender;
    private LocalDateTime sentAt;
    private MessageType type;
    private Long channelId;
    private EventType eventType;
    private Long replyToMessageId;
	private ReplyPreviewDto replyPreview;
	private LocalDateTime editedAt;
	private List<MessageAttachmentDto> attachments;
}

```

### src/main/java/com/zvonok/service/PermissionService.java
```java
package com.zvonok.service;

import com.zvonok.logging.LogEvent;
import com.zvonok.logging.LogEventConstants;
import com.zvonok.logging.LogTimingUtils;
import com.zvonok.model.*;
import com.zvonok.repository.ChannelPermissionOverrideRepository;
import com.zvonok.repository.FolderPermissionOverrideRepository;
import com.zvonok.repository.ServerMemberRepository;
import com.zvonok.service.dto.Permission;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing and checking user permissions in servers, folders, and
 * channels.
 * Implements a hierarchical permission system with role-based access control
 * and permission overrides.
 *
 * Сервис для управления и проверки прав пользователей в серверах, папках и
 * каналах.
 * Реализует иерархическую систему разрешений с контролем доступа на основе
 * ролей и переопределениями прав.
 */
@Service
@Slf4j
public class PermissionService {

	private final ServerMemberRepository memberRepository;
	private final ChannelPermissionOverrideRepository channelOverrideRepository;
	private final FolderPermissionOverrideRepository folderOverrideRepository;
	private final ChannelService channelService;

	public PermissionService(
			ServerMemberRepository memberRepository,
			ChannelPermissionOverrideRepository channelOverrideRepository,
			FolderPermissionOverrideRepository folderOverrideRepository,
			@Lazy ChannelService channelService) {
		this.memberRepository = memberRepository;
		this.channelOverrideRepository = channelOverrideRepository;
		this.folderOverrideRepository = folderOverrideRepository;
		this.channelService = channelService;
	}

	public boolean canUserViewFolder(Long userId, Long folderId) {
		return hasPermissionInFolder(userId, folderId, Permission.VIEW_CHANNEL);
	}

	public boolean canUserViewChannel(Long userId, Long channelId) {
		Channel channel = channelService.getChannelByIdInternal(channelId);
		return Boolean.TRUE.equals(channel.getIsActive())
				&& hasPermissionInChannel(userId, channelId, Permission.VIEW_CHANNEL);
	}

	public boolean canUserSendMessages(Long userId, Long channelId) {
		return hasPermissionInChannel(userId, channelId, Permission.SEND_MESSAGES);
	}

	public boolean hasPermissionInServer(Long userId, Long serverId, Permission permission) {
		long durationStart = System.currentTimeMillis();
		Optional<ServerMember> optionalMember = memberRepository.findByUserIdAndServerId(userId, serverId);
		if (optionalMember.isEmpty() || !optionalMember.get().getIsActive()) {
			return false;
		}

		ServerMember member = optionalMember.get();
		List<ServerRole> userRoles = getUserRoles(member);

		log.debug("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_CHECK_SERVER_PERMISSION_ACTION,
				LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId).serverId(serverId)
				.permission(permission.getValue()));

		// Админы могут все
		if (hasAdminRole(userRoles)) {
			return true;
		}

		if (!calculateServerPermissions(member, userRoles, permission)) {
			log.warn("{}",
					LogEvent.buildFailedEvent(LogEventConstants.EVENT_CHECK_SERVER_PERMISSION_ACTION,
							"User denied access to server",
							LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId).serverId(serverId)
							.permission(permission.getValue()));
			return false;
		} else {
			return true;
		}

	}

	public boolean hasPermissionInChannel(Long userId, Long channelId, Permission permission) {
		long durationStart = System.currentTimeMillis();
		Optional<ServerMember> optionalMember = memberRepository.findByUserIdAndChannelId(userId, channelId);
		if (optionalMember.isEmpty() || !optionalMember.get().getIsActive()) {
			return false;
		}

		ServerMember member = optionalMember.get();
		List<ServerRole> userRoles = getUserRoles(member);

		log.debug("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_CHECK_CHANNEL_PERMISSION_ACTION,
				LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId).channelId(channelId)
				.permission(permission.getValue()));

		if (hasAdminRole(userRoles)) {
			return true;
		}

		if (!calculateChannelPermissions(member, userRoles, channelId, permission)) {
			log.warn("{}",
					LogEvent.buildFailedEvent(LogEventConstants.EVENT_CHECK_CHANNEL_PERMISSION_ACTION,
							"User denied access to channel",
							LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId)
							.channelId(channelId)
							.permission(permission.getValue()));
			return false;
		} else {
			return true;
		}
	}

	public boolean hasPermissionInFolder(Long userId, Long folderId, Permission permission) {
		long durationStart = System.currentTimeMillis();
		Optional<ServerMember> optionalMember = memberRepository.findByUserIdAndFolderId(userId, folderId);
		if (optionalMember.isEmpty() || !optionalMember.get().getIsActive()) {
			return false;
		}

		ServerMember member = optionalMember.get();
		List<ServerRole> userRoles = getUserRoles(member);

		log.debug("{}", LogEvent.buildSuccessEvent(LogEventConstants.EVENT_CHECK_FOLDER_PERMISSION_ACTION,
				LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId).folderId(folderId)
				.permission(permission.getValue()));

		if (hasAdminRole(userRoles)) {
			return true;
		}

		if (!calculateFolderPermissions(member, userRoles, folderId, permission)) {
			log.warn("{}",
					LogEvent.buildFailedEvent(LogEventConstants.EVENT_CHECK_FOLDER_PERMISSION_ACTION,
							"User denied access to folder",
							LogTimingUtils.calculateDurationDifference(durationStart)).userId(userId)
							.folderId(folderId)
							.permission(permission.getValue()));
			return false;
		} else {
			return true;
		}
	}

	public boolean isServerMember(Long userId, Long serverId) {
		Optional<ServerMember> member = memberRepository.findByUserIdAndServerId(userId, serverId);
		return member.isPresent() && member.get().getIsActive();
	}

	public boolean canManageServer(Long userId, Long serverId) {
		return hasPermissionInServer(userId, serverId, Permission.MANAGE_SERVER);
	}

	public boolean canKickMembers(Long userId, Long serverId) {
		return hasPermissionInServer(userId, serverId, Permission.KICK_MEMBERS);
	}

	public boolean canBanMembers(Long userId, Long serverId) {
		return hasPermissionInServer(userId, serverId, Permission.BAN_MEMBERS);
	}

	public boolean canManageRoles(Long userId, Long serverId) {
		return hasPermissionInServer(userId, serverId, Permission.MANAGE_ROLES);
	}

	public boolean canCreateInvites(Long userId, Long serverId) {
		return hasPermissionInServer(userId, serverId, Permission.CREATE_INVITE);
	}

	public Long getUserServerPermissions(Long userId, Long serverId) {
		Optional<ServerMember> optionalMember = memberRepository.findByUserIdAndServerId(userId, serverId);
		if (optionalMember.isEmpty() || !optionalMember.get().getIsActive()) {
			return 0L;
		}

		ServerMember member = optionalMember.get();
		List<ServerRole> userRoles = getUserRoles(member);

		// Если админ - все разрешения
		if (hasAdminRole(userRoles)) {
			return Permission.ADMINISTRATOR.getValue();
		}

		// Собираем все разрешения от ролей
		long permissions = 0L;
		for (ServerRole role : userRoles) {
			permissions |= role.getServerPermissions();
		}

		// Добавляем персональные разрешения
		permissions |= member.getPersonalPermissions();

		return permissions;
	}

	public List<ServerRole> getUserServerRoles(Long userId, Long serverId) {
		Optional<ServerMember> optionalMember = memberRepository.findByUserIdAndServerId(userId, serverId);
		if (optionalMember.isEmpty() || !optionalMember.get().getIsActive()) {
			return List.of();
		}

		return getUserRoles(optionalMember.get());
	}

	// ===== PRIVATE HELPER METHODS =====

	private boolean calculateServerPermissions(ServerMember member, List<ServerRole> roles, Permission permission) {
		Long permValue = permission.getValue();

		// Шаг 1: Базовые разрешения от ролей
		long serverPermissions = 0L;
		for (ServerRole role : roles) {
			serverPermissions |= role.getServerPermissions();
		}

		// Шаг 2: Персональные разрешения участника
		serverPermissions |= member.getPersonalPermissions();

		// Шаг 3: Проверка разрешения
		return (serverPermissions & permValue) != 0;
	}

	public boolean calculateChannelPermissions(ServerMember member, List<ServerRole> roles, Long channelId,
			Permission permission) {
		Long permValue = permission.getValue();

		// Шаг 1: Базовые разрешения от ролей
		Long basePermissions = 0L;
		for (ServerRole role : roles) {
			basePermissions |= role.getServerPermissions();
		}

		// Шаг 2: Разрешения папки (наследуются каналом)
		Channel channel = channelService.getChannelByIdInternal(channelId);
		ChannelFolder folder = channel.getFolder();

		long folderPermissions = calculateFolderPermissionsValue(member, roles, folder.getId());
		basePermissions |= folderPermissions;

		// Шаг 3: Переопределения канала для ролей
		List<ChannelPermissionOverride> roleOverrides = channelOverrideRepository.findByChannelIdAndRoleIn(channelId,
				roles);

		long channelAllowed = basePermissions;
		long channelDenied = 0L;

		for (ChannelPermissionOverride override : roleOverrides) {
			channelAllowed |= override.getAllowedPermissions();
			channelDenied |= override.getDeniedPermissions();
		}

		// Шаг 4: Персональные переопределения пользователя
		Optional<ChannelPermissionOverride> optionalChannelOverride = channelOverrideRepository
				.findByChannelIdAndUserId(channelId, member.getUser().getId());

		if (optionalChannelOverride.isPresent()) {
			ChannelPermissionOverride channelOverride = optionalChannelOverride.get();
			channelAllowed |= channelOverride.getAllowedPermissions();
			channelDenied |= channelOverride.getDeniedPermissions();
		}

		// Шаг 5: Персональные разрешения участника
		channelAllowed |= member.getPersonalPermissions();

		// Шаг 6: Финальная проверка (запреты приоритетнее)
		if ((channelDenied & permValue) != 0) {
			return false; // Явно запрещено
		}

		return (channelAllowed & permValue) != 0; // Разрешено
	}

	private boolean calculateFolderPermissions(ServerMember member, List<ServerRole> roles,
			Long folderId, Permission permission) {
		Long permValue = permission.getValue();

		// Шаг 1: Базовые разрешения от ролей
		long basePermissions = 0L;
		for (ServerRole role : roles) {
			basePermissions |= role.getServerPermissions();
		}

		// Шаг 2: Переопределения папки для ролей
		List<FolderPermissionOverride> roleOverrides = folderOverrideRepository.findByFolderIdAndRoleIn(folderId,
				roles);

		long folderAllowed = basePermissions;
		long folderDenied = 0L;

		for (FolderPermissionOverride override : roleOverrides) {
			folderAllowed |= override.getAllowedPermissions();
			folderDenied |= override.getDeniedPermissions();
		}

		// Шаг 3: Персональные переопределения пользователя для папки
		Optional<FolderPermissionOverride> userOverrideOpt = folderOverrideRepository.findByFolderIdAndUserId(folderId,
				member.getUser().getId());

		if (userOverrideOpt.isPresent()) {
			FolderPermissionOverride userOverride = userOverrideOpt.get();
			folderAllowed |= userOverride.getAllowedPermissions();
			folderDenied |= userOverride.getDeniedPermissions();
		}

		// Шаг 4: Персональные разрешения участника
		folderAllowed |= member.getPersonalPermissions();

		// Шаг 5: Финальная проверка (запреты приоритетнее)
		if ((folderDenied & permValue) != 0) {
			return false; // Явно запрещено
		}

		return (folderAllowed & permValue) != 0; // Разрешено
	}

	private long calculateFolderPermissionsValue(ServerMember member, List<ServerRole> roles, Long folderId) {
		List<FolderPermissionOverride> roleOverrides = folderOverrideRepository.findByFolderIdAndRoleIn(folderId,
				roles);

		long allowed = 0L;
		long denied = 0L;

		for (FolderPermissionOverride override : roleOverrides) {
			allowed |= override.getAllowedPermissions();
			denied |= override.getDeniedPermissions();
		}

		// Персональные переопределения для папки
		Optional<FolderPermissionOverride> optionalFolderOverride = folderOverrideRepository
				.findByFolderIdAndUserId(folderId, member.getUser().getId());

		if (optionalFolderOverride.isPresent()) {
			FolderPermissionOverride folderOverride = optionalFolderOverride.get();
			allowed |= folderOverride.getAllowedPermissions();
			denied |= folderOverride.getDeniedPermissions();
		}

		return allowed & ~denied; // Возвращаем разрешения минус запреты
	}

	private List<ServerRole> getUserRoles(ServerMember member) {
		return member.getMemberRoles().stream()
				.map(ServerMemberRole::getRole)
				.filter(ServerRole::getIsActive)
				.collect(Collectors.toList());
	}

	private boolean hasAdminRole(List<ServerRole> roles) {
		return roles.stream()
				.anyMatch(role -> Permission.hasPermission(role.getServerPermissions(), Permission.ADMINISTRATOR));
	}
}


```

### src/main/java/com/zvonok/service/InviteCodeService.java
```java
package com.zvonok.service;

import com.zvonok.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Service for generating unique invite codes for servers.
 * Сервис для генерации уникальных кодов приглашения для серверов.
 * 
 * Использует репозиторий напрямую для проверки уникальности кода,
 * чтобы избежать циклической зависимости с ServerService.
 */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private final ServerRepository serverRepository;
    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 15;
    private final SecureRandom random = new SecureRandom();

    public String generateUniqueInviteCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (serverRepository.existsByInvitedCode(code));

        return code;
    }

    public String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}

```

### src/main/java/com/zvonok/service/dto/Permission.java
```java
package com.zvonok.service.dto;

import lombok.Getter;

@Getter
public enum Permission {
    // Базовые разрешения
    NOTHING(0L),             // Ничего
    VIEW_CHANNEL(1L << 0),         // Видеть канал
    SEND_MESSAGES(1L << 1),        // Отправлять сообщения
    READ_MESSAGE_HISTORY(1L << 2), // Читать историю сообщений

    // Форматирование сообщений
    EMBED_LINKS(1L << 3),          // Вставлять ссылки
    ATTACH_FILES(1L << 4),         // Прикреплять файлы

    // Редактирование сообщений
    EDIT_MESSAGES(1L << 5),        // Редактировать чужие сообщения

    // Голосовые каналы
    CONNECT(1L << 6),             // Подключаться к голосовым каналам
    SPEAK(1L << 7),               // Говорить в голосовых каналах
    MUTE_MEMBERS(1L << 8),        // Заглушать участников
    DEAFEN_MEMBERS(1L << 9),     // Оглушать участников
    MOVE_MEMBERS(1L << 10),       // Перемещать участников между каналами

    // Управление сообщениями
    MANAGE_MESSAGES(1L << 11),    // Управлять сообщениями (удалять чужие)

    // Управление каналами и папками
    MANAGE_CHANNELS(1L << 12),    // Управлять каналами (создавать, редактировать, удалять)
    MANAGE_PERMISSIONS(1L << 13), // Управлять разрешениями канала

    // Управление сервером
    CHANGE_NICKNAME(1L << 14),    // Изменять свой никнейм
    MANAGE_NICKNAMES(1L << 15),   // Изменять никнеймы других участников
    KICK_MEMBERS(1L << 16),       // Исключать участников
    BAN_MEMBERS(1L << 17),        // Банить участников
    MANAGE_ROLES(1L << 18),       // Управлять ролями
    MANAGE_SERVER(1L << 19),      // Управлять сервером
    CREATE_INVITE(1L << 20),      // Создавать приглашения

    // Администраторские права
    ADMINISTRATOR(1L << 21);      // Полные права администратора

    private final long value;

    Permission(long value) {
        this.value = value;
    }

    // Проверить есть ли разрешение в битовой маске
    public static boolean hasPermission(Long permissions, Permission permission) {
        return (permissions & permission.getValue()) != 0;
    }

    // Добавить разрешение к битовой маске
    public static Long addPermission(Long permissions, Permission permission) {
        return permissions | permission.getValue();
    }

    // Удалить разрешение из битовой маски
    public static Long removePermission(Long permissions, Permission permission) {
        return permissions & ~permission.getValue();
    }
}

```