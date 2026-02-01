package com.zvonok.controller;

import com.zvonok.documentation.CommonApiDescriptions;
import com.zvonok.documentation.ServerApiDescriptions;
import com.zvonok.documentation.ServerMemberRoleApiDescriptions;
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
	@ApiResponses(value = {@ApiResponse(responseCode = "200",
			description = ServerMemberRoleApiDescriptions.SERVER_MEMBER_ROLE_ASSIGN_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND),})
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
	@ApiResponses(value = {@ApiResponse(responseCode = "204",
			description = ServerMemberRoleApiDescriptions.SERVER_MEMBER_ROLE_REMOVE_SUCCESS),
			@ApiResponse(responseCode = "403",
					description = CommonApiDescriptions.NOT_ENOUGH_RIGHTS),
			@ApiResponse(responseCode = "404",
					description = ServerApiDescriptions.SERVER_NOT_FOUND),})
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

