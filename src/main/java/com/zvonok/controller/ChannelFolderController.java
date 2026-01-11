package com.zvonok.controller;

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
@RestController
@RequestMapping("/server/{serverId}/channel-folders")
@RequiredArgsConstructor
public class ChannelFolderController {

	private final ChannelFolderService channelFolderService;
	private final PermissionService permissionService;
	private final UserService userService;
	private final ServerService serverService;

	@GetMapping
	public ResponseEntity<List<ChannelFolder>> getChannelFolders(@PathVariable Long serverId,
			@AuthenticationPrincipal UserPrincipal principal) {
		Long userId = getCurrentUserId(principal);

		ensureServerExists(serverId);
		ensureIsServerMember(userId, serverId);

		List<ChannelFolder> folders = channelFolderService.getActiveChannelFolders(serverId);
		return ResponseEntity.ok(folders);
	}

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

