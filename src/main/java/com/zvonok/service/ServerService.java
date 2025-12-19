package com.zvonok.service;

import com.zvonok.exception.*;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
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
 * Сервис для управления серверами, участниками серверов и операциями, связанными с серверами.
 */
@Service
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

    public ServerService(
            ServerRepository serverRepository,
            UserService userService,
            InviteCodeService inviteCodeService,
            PermissionService permissionService,
            ServerMemberService serverMemberService,
            ServerMemberRoleService serverMemberRoleService,
            ServerRoleService serverRoleService,
            @Lazy ChannelService channelService,
            @Lazy ChannelFolderService channelFolderService,
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
        validateCreateServerRequest(request); 

        User owner = userService.getUser(ownerId);

        // Создаем сервер
        Server server = new Server();
        server.setName(request.getName());
        server.setInvitedCode(inviteCodeService.generateUniqueInviteCode());
        server.setOwner(owner);
        server.setMaxMember(request.getMaxMembers() != null ? request.getMaxMembers() : 1000);
        server.setCreatedAt(LocalDateTime.now());

        Server savedServer = serverRepository.save(server);

        // Создаем роли по умолчанию
        createEveryoneRole(savedServer);
        ServerRole ownerRole = createOwnerRole(savedServer);

        // Добавляем владельца как участника
        ServerMember ownerMember = addOwnerAsMember(savedServer, owner);
        assignRoleToMember(ownerMember, ownerRole, ownerId);

        // Создаем дефолтную папку и каналы
        ChannelFolder defaultFolder = createDefaultFolder(savedServer.getId());
        createDefaultChannels(defaultFolder.getId());

        return mapToResponse(savedServer);
    }

    public Server getServer(Long serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new ServerNotFoundException("Сервер не найден с ID: " + serverId));
    }

    public List<ServerResponse> getUserServers(Long userId) {
        List<Server> servers = serverRepository.findServersByUserId(userId);
        return servers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public boolean hasAccessToServer(Long userId, Long serverId) {
        return permissionService.isServerMember(userId, serverId);
    }

    public void hasAccessToServerAndThrowExceptionIfFalse(Long userId, Long serverId) {
        if (!hasAccessToServer(userId, serverId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }
    }

    public ServerResponse getServerResponse(Long serverId) {
        Server server = getServer(serverId);
        return mapToResponse(server);
    }

    @Transactional
    public ServerResponse joinServerByInviteCode(String inviteCode, Long userId) {
        Server server = serverRepository.findByInvitedCode(inviteCode)
                .orElseThrow(() -> new ServerNotFoundException(
                        HttpResponseMessage.HTTP_SERVER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));

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
        if (isServerMember(userId, server)) {
            return mapToResponse(server); // Уже участник
        }

        // Проверяем лимит участников
        long memberCount = serverMemberService.countServerMembers(server.getId());
        if (memberCount >= server.getMaxMember()) {
            throw new ServerMemberLimitReachedException(
                    BusinessRuleMessage.BUSINESS_SERVER_MEMBER_LIMIT_REACHED_MESSAGE.getMessage());
        }

        // Добавляем как участника
        ServerMember newMember = addUserAsMember(server, user);

        // Назначаем роль @everyone
        ServerRole everyoneRole = serverRoleService.getServerRoleWithIsEveryoneTrue(server.getId());
        assignRoleToMember(newMember, everyoneRole, userId);

        return mapToResponse(server);
    }

    public boolean isServerMember(Long userId, Server server) {
        ServerMember member = serverMemberService.findServerMemberOrNull(userId, server.getId());
        return member != null && member.getIsActive();
    }

    @Transactional
    public ServerResponse updateServer(Long serverId, UpdateServerRequest request, Long userId) {
        Server server = getServer(serverId);

        // Проверяем права на управление сервером
        if (!canManageServer(userId, serverId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }

        if (request.getName() != null) {
            server.setName(request.getName());
        }

        if (request.getMaxMembers() != null) {
            server.setMaxMember(request.getMaxMembers());
        }

        Server updatedServer = serverRepository.save(server);
        return mapToResponse(updatedServer);
    }

    @Transactional
    public String regenerateInviteCode(Long serverId, Long userId) {
        Server server = getServer(serverId);

        if (!canManageServer(userId, serverId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }

        String newInviteCode = inviteCodeService.generateUniqueInviteCode();
        server.setInvitedCode(newInviteCode);
        serverRepository.save(server);

        return newInviteCode;
    }

    @Transactional
    public void leaveServer(Long serverId, Long userId) {
        Server server = getServer(serverId);

        // Владелец не может покинуть сервер
        if (server.getOwner().getId().equals(userId)) {
            throw new OwnerCanNotLeaveServerException(
                    HttpResponseMessage.HTTP_OWNER_CAN_NOT_LEAVE_SERVER_RESPONSE_MESSAGE.getMessage());
        }

        ServerMember member = serverMemberService.getServerMember(userId, serverId);

        member.setIsActive(false);
        member.setLeftAt(LocalDateTime.now());
        serverMemberService.updateServerMember(member);

    }

    public List<ServerMemberResponse> getServerMembers(Long serverId, Long userId) {
        // Проверяем может ли пользователь видеть участников
        if (!canViewMembers(userId, serverId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }

        List<ServerMember> members = serverMemberService.getAllActiveMember(serverId);
        return members.stream()
                .map(this::mapToMemberResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void kickMember(Long serverId, Long targetUserId, Long kickerUserId) {
        Server server = getServer(serverId);

        // Проверяем права на исключение
        if (!canKickMembers(kickerUserId, serverId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
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

        targetMember.setIsActive(false);
        targetMember.setLeftAt(LocalDateTime.now());
        serverMemberService.updateServerMember(targetMember);

    }

    @Transactional
    public ServerMemberResponse updateMemberNickname(Long serverId, Long targetUserId, String nickname, Long actorUserId) {
        // Убедимся, что сервер существует
        getServer(serverId);

        // Получаем участников
        ServerMember actorMember = serverMemberService.getServerMember(actorUserId, serverId);
        ServerMember targetMember = serverMemberService.getServerMember(targetUserId, serverId);

        boolean isSelfUpdate = targetMember.getUser().getId().equals(actorMember.getUser().getId());
        if (!isSelfUpdate && !permissionService.hasPermissionInServer(actorUserId, serverId, Permission.MANAGE_SERVER)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }

        String processedNickname = StringUtils.hasText(nickname) ? nickname.trim() : null;
        targetMember.setNickname(processedNickname);
        serverMemberService.updateServerMember(targetMember);

        return mapToMemberResponse(targetMember);
    }

    // ===== PRIVATE HELPER METHODS =====

    private void validateCreateServerRequest(CreateServerRequest request) {
        if (request.getName().length() < 5 || request.getName().length() > 100) {
            throw new InvalidServerNameException(
                HttpResponseMessage.HTTP_SERVER_NAME_NOT_VALID_RESPONSE_MESSAGE.getMessage()
            );
        }
        
        if (request.getMaxMembers() != null && (request.getMaxMembers() < 10 || request.getMaxMembers() > 100)) {
            throw new InvalidServerMaxMemberException(
                HttpResponseMessage.HTTP_SERVER_MAX_MEMBERS_NOT_VALID_RESPONSE_MESSAGE.getMessage()
            );
        }
    }

    private ServerRole createEveryoneRole(Server server) {
        CreateServerRoleDto createDto = new CreateServerRoleDto();
        createDto.setName("everyone");
        createDto.setColor("#ffffff");
        createDto.setPosition(0);
        createDto.setServerPermissions(
                Permission.VIEW_CHANNEL.getValue() |
                Permission.SEND_MESSAGES.getValue() |
                Permission.READ_MESSAGE_HISTORY.getValue());
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

    // ===== PERMISSION CHECKS =====

    private boolean canManageServer(Long userId, Long serverId) {
        return permissionService.hasPermissionInServer(userId, serverId, Permission.MANAGE_SERVER) ||
            serverRepository.isServerOwner(userId, serverId);
    }

    private boolean canViewMembers(Long userId, Long serverId) {
        ServerMember member = serverMemberService.findServerMemberOrNull(userId, serverId);
        return member != null && member.getIsActive();
    }

    private boolean canKickMembers(Long userId, Long serverId) {
        return permissionService.hasPermissionInServer(userId, serverId, Permission.KICK_MEMBERS) ||
            serverRepository.isServerOwner(userId, serverId);
    }

    // ===== MAPPING METHODS =====

    public ServerResponse mapToResponse(Server server) {
        return ServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .inviteCode(server.getInvitedCode())
                .maxMembers(server.getMaxMember())
                .memberCount(serverMemberService.countServerMembers(server.getId()))
                .ownerId(server.getOwner().getId())
                .ownerName(server.getOwner().getUsername())
                .createdAt(server.getCreatedAt())
                .channelFolders(server.getChannelFolders())
                .build();
    }

    private ServerMemberResponse mapToMemberResponse(ServerMember member) {
        List<String> roleNames = member.getMemberRoles().stream()
                .map(mr -> mr.getRole().getName())
                .collect(Collectors.toList());

        return ServerMemberResponse.builder()
                .userId(member.getUser().getId())
                .username(member.getUser().getUsername())
                .avatarUrl(member.getUser().getAvatarUrl())
                .nickname(member.getNickname())
                .joinedAt(member.getJoinedAt())
                .roles(roleNames)
                .isOwner(member.getServer().getOwner().getId().equals(member.getUser().getId()))
                .build();
    }

    @Transactional
    public void deleteServer(Long serverId, Long userId) {
        Server server = getServer(serverId);
        
        // Проверяем, что пользователь является владельцем
        if (!server.getOwner().getId().equals(userId)) {
            throw new InsufficientPermissionsException(
                    HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage());
        }
        
        // Удаляем сервер (каскадное удаление должно быть настроено в JPA)
        serverRepository.delete(server);
    }
}
