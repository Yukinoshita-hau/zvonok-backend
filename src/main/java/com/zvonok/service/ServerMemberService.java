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
