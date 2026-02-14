package com.zvonok.unittests.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zvonok.model.Server;
import com.zvonok.model.ServerMember;
import com.zvonok.model.ServerRole;
import com.zvonok.exception.InvalidServerMaxMemberException;
import com.zvonok.exception.InvalidServerNameException;
import com.zvonok.exception.OwnerCanNotLeaveServerException;
import com.zvonok.exception.UserNotFoundException;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.User;
import com.zvonok.repository.ServerRepository;
import com.zvonok.service.*;
import com.zvonok.service.dto.CreateChannelFolderDto;
import com.zvonok.service.dto.CreateServerRoleDto;
import com.zvonok.service.dto.Permission;
import com.zvonok.service.dto.request.CreateServerRequest;
import com.zvonok.service.dto.response.ServerResponse;

@ExtendWith(MockitoExtension.class)
public class ServerServiceTest {
    
    @Mock
    private ServerRepository serverRepository;

    @Mock
    private UserService userService;

    @Mock
    private InviteCodeService inviteCodeService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private ServerMemberService serverMemberService;

    @Mock
    private ServerMemberRoleService serverMemberRoleService;

    @Mock
    private ServerRoleService serverRoleService;

    @Mock
    private ChannelService channelService;

    @Mock
    private ChannelFolderService channelFolderService;

    @Mock
    private ServerBanService serverBanService;

    @InjectMocks
    private ServerService serverService;

    private Server testServer;
    private User testUser;
    static private final Long USER_ID = 1L;
    private final Long SERVER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setUsername("testuser");

        testServer = new Server();
        testServer.setId(SERVER_ID);
        testServer.setName("Test Server");
        testServer.setInvitedCode("INVITE123");
        testServer.setMaxMember(100);
        testServer.setCreatedAt(LocalDateTime.now());
        testServer.setOwner(testUser);
    }

    // CreateServer
    @Test
    @DisplayName("createServer - успешное создание сервера")
    void createServer_shouldReturnServerResponse_whenValidData() {
        // Arrange
        when(userService.getUser(USER_ID)).thenReturn(testUser);
        when(serverRepository.save(any())).thenReturn(testServer);
        when(serverRoleService.createServerRole(
            any(CreateServerRoleDto.class)
        )).thenReturn(mock(ServerRole.class));
        when(serverMemberService.createServerMember(
            any(Server.class),
            any(User.class)
        )).thenReturn(mock(ServerMember.class));
        when(channelFolderService.createChannelFolder(
            any(CreateChannelFolderDto.class)
        )).thenReturn(mock(ChannelFolder.class));

        CreateServerRequest request = new CreateServerRequest();
        request.setName("testName");
        request.setMaxMembers(10);

        // Act
        ServerResponse response = serverService.createServer(request, testUser.getId());

        // Assert
        assertNotNull(response);
        assertAll(
            () -> assertEquals(response.getName(), testServer.getName()),
            () -> assertEquals(response.getMaxMembers(), testServer.getMaxMember()),
            () -> assertEquals(response.getOwnerId(), testServer.getOwner().getId()),
            () -> assertEquals(response.getOwnerName(), testServer.getOwner().getUsername())
        );
    }

    @Test
    @DisplayName("createServer - неуспешное создание сервера с некоректным владельцем")
    void createServer_shouldThrowException_whenOwnerNotFound() {
        // Arrange
        when(userService.getUser(anyLong())).thenThrow(UserNotFoundException.class);

        CreateServerRequest request = new CreateServerRequest();
        request.setName("testName");
        request.setMaxMembers(10);

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            serverService.createServer(request, 999L);
        });
        
        verify(serverRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("createServerThrowExceptionCases")
    void createServer_shouldThrowException(Class<Exception> errorClass, String name, Integer maxMembers) {
        // Arrange
        CreateServerRequest request = new CreateServerRequest();
        request.setName(name);
        request.setMaxMembers(maxMembers);

        // Act & Assert
        assertThrows(errorClass, () -> {
            serverService.createServer(request, USER_ID);
        });

        verify(serverRepository, never()).save(any());
    }

    static private Stream<Arguments> createServerThrowExceptionCases() {
        return Stream.of(
            Arguments.of(
                InvalidServerNameException.class,
                "test",
                10
            ),
            Arguments.of(
                InvalidServerMaxMemberException.class,
                "testServerName",
                9
            )
        );
    }

    // JoinServer
    @Test
    @DisplayName("joinServerByInviteCode - вход в сервер по коду приглашения")
    void joinServerByInviteCode_shouldReturnServerResponse_whenValidData() {
        // Arrange
        when(serverRepository.findByInvitedCode(anyString())).thenReturn(Optional.of(testServer));
        when(userService.getUser(anyLong())).thenReturn(testUser);
        when(serverBanService.isUserBanned(anyLong(), anyLong())).thenReturn(false);
        when(serverMemberService.findServerMemberOrNull(anyLong(), anyLong())).thenReturn(null);
        when(serverMemberService.countServerMembers(anyLong())).thenReturn(1L);
        when(serverMemberService.createServerMember(
            any(Server.class), 
            any(User.class)
        )).thenReturn(mock(ServerMember.class));
        when(serverRoleService.getServerRoleWithIsEveryoneTrue(anyLong())).thenReturn(mock(ServerRole.class));

        // Act
        ServerResponse response = serverService.joinServerByInviteCode(testServer.getInvitedCode(), USER_ID);

        // Assert
        assertNotNull(response);
        assertAll(
            () -> assertEquals(response.getName(), testServer.getName()),
            () -> assertEquals(response.getMaxMembers(), testServer.getMaxMember()),
            () -> assertEquals(response.getOwnerId(), testServer.getOwner().getId()),
            () -> assertEquals(response.getOwnerName(), testServer.getOwner().getUsername())
        );  
    }

    // Leave
    @Test
    @DisplayName("leaveServer - выход из сервера")
    void leaveServer_shouldLeaveServer_whenValidData() {
        // Arrange
        User owner = new User();
        owner.setId(999L);
        testServer.setOwner(owner);

        ServerMember member = new ServerMember();
        member.setUser(testUser);
        member.setIsActive(true);

        when(serverRepository.findById(SERVER_ID)).thenReturn(Optional.of(testServer));
        when(serverMemberService.getActiveServerMember(USER_ID, SERVER_ID)).thenReturn(member);

        // Act
        serverService.leaveServer(SERVER_ID, USER_ID);

        // Assert
        assertFalse(member.getIsActive(), "Участник должен быть деактивирован");
        assertNotNull(member.getLeftAt(), "Должно быть установлено время выхода");
        verify(serverMemberService).updateServerMember(member);
    }

    @Test
    @DisplayName("leaveServer - Владелец не может покинуть сервер")
    void leaveServer_shouldThrowException_whenOwner() {
        // Arrange
        testServer.setOwner(testUser);
        when(serverRepository.findById(SERVER_ID)).thenReturn(Optional.of(testServer));

        // Act & Assert
        assertThrows(OwnerCanNotLeaveServerException.class, () -> {
            serverService.leaveServer(SERVER_ID, USER_ID);
        }, "Должно быть выброшено исключение для владельца");
    }

    // Kick
    @Test
    @DisplayName("kickMember - исключение участника")
    void kickMember_shouldKickMember_whenValidData() {
        // Arrange
        User owner = new User();
        owner.setId(999L);
        testServer.setOwner(owner);

        ServerMember member = new ServerMember();
        member.setUser(testUser);
        member.setIsActive(true);

        when(serverRepository.findById(SERVER_ID)).thenReturn(Optional.of(testServer));
        when(permissionService.hasPermissionInServer(
            owner.getId(),
            SERVER_ID, 
            Permission.KICK_MEMBERS
        )).thenReturn(true);
        when(serverMemberService.getServerMember(USER_ID, SERVER_ID)).thenReturn(member);

        // Act
        serverService.kickMember(SERVER_ID, USER_ID, owner.getId());

        assertFalse(member.getIsActive(), "Участник должен быть деактивирован");
        assertNotNull(member.getLeftAt(), "Должно быть установлено время выхода");
        verify(serverMemberService).updateServerMember(member);
    }
}
