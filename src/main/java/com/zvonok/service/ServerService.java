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

			User owner = userService.getUser(ownerId);

			// Создаем сервер
			Server server = new Server();
			server.setName(request.getName());
			server.setInvitedCode(inviteCodeService.generateUniqueInviteCode());
			server.setOwner(owner);
			server.setMaxMember(request.getMaxMembers() != null ? request.getMaxMembers() : 1000);
			server.setCreatedAt(LocalDateTime.now());

			savedServer = serverRepository.save(server);

			// Создаем роли по умолчанию
			createEveryoneRole(savedServer);
			ServerRole ownerRole = createOwnerRole(savedServer);

			// Добавляем владельца как участника
			ServerMember ownerMember = addOwnerAsMember(savedServer, owner);
			assignRoleToMember(ownerMember, ownerRole, ownerId);

			// Создаем дефолтную папку и каналы
			ChannelFolder defaultFolder = createDefaultFolder(savedServer.getId());
			createDefaultChannels(defaultFolder.getId());

			log.info("{}",
					LogEvent.buildSuccessEvent(LogEventConstants.EVENT_CREATE_SERVER_ACTION,
							LogTimingUtils.calculateDurationDifference(durationStart))
							.serverId(savedServer.getId()).userId(ownerId).build());
		} catch (InvalidServerNameException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_CREATE_SERVER_ACTION, durationStart, ownerId, savedServer,
					false);
			throw e;
		} catch (InvalidServerMaxMemberException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_CREATE_SERVER_ACTION, durationStart, ownerId, savedServer,
					false);
			throw e;
		} catch (ServerNotFoundException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_CREATE_SERVER_ACTION, durationStart, ownerId, savedServer,
					false);
			throw e;
		} catch (ChannelNotFoundException e) {
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

		} catch (ServerNotFoundException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION, durationStart, userId, server,
					false);
			throw e;
		} catch (UserBannedException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION, durationStart, userId, server,
					false);
			throw e;
		} catch (ServerMemberLimitReachedException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_JOIN_BY_INVITE_CODE_ACTION, durationStart, userId, server,
					false);
			throw e;
		} catch (ServerRoleNotFoundException e) {
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
		} catch (ServerNotFoundException e) {
			buildServerFailedLog(e, LogEventConstants.EVENT_UPDATE_SERVER_ACTION, durationStart, userId, updatedServer,
					false);
			throw e;
		} catch (InsufficientPermissionsException e) {
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
		} catch (ServerNotFoundException e) {
			log.warn("{}", LogEvent.buildFailedEvent(LogEventConstants.EVENT_DELETE_SERVER_ACTION, e.getMessage(),
					LogTimingUtils.calculateDurationDifference(durationStart)).serverId(serverId).userId(userId));
			throw e;
		} catch (InsufficientPermissionsException e) {
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
		} catch (ServerMemberNotFoundException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_LEAVE_SERVER_ACION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(userId).build());
			throw e;
		} catch (OwnerCanNotLeaveServerException e) {
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
		} catch (ServerNotFoundException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (InsufficientPermissionsException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (CannotKickServerOwnerException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (CannotKickYourselfException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (ServerMemberNotFoundException e) {
			log.warn("{}", LogEvent
					.buildFailedEvent(LogEventConstants.EVENT_KICK_MEMBER_ACTION, e.getMessage(),
							LogTimingUtils.calculateDurationDifference(durationStart))
					.serverId(serverId).userId(targetUserId).build());
			throw e;
		} catch (ServerMemberAlreadyWasKicked e) {
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

	private void createDefaultChannels(Long folderId) {
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

		channelService.createChannel(generalChannel);
		channelService.createChannel(voiceChannel);
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
