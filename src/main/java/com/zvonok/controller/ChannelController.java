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
