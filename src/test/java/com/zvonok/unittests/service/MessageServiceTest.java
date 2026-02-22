package com.zvonok.unittests.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.zvonok.controller.dto.ChannelMessageResponse;
import com.zvonok.exception.*;
import com.zvonok.model.Channel;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.Server;
import com.zvonok.service.dto.EventType;
import com.zvonok.service.dto.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.controller.dto.ShortMessageWrapped;
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.ChannelService;
import com.zvonok.service.MessageService;
import com.zvonok.service.PermissionService;
import com.zvonok.service.RoomService;
import com.zvonok.service.UserService;

@ExtendWith(MockitoExtension.class)
public class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private RoomService roomService;

    @Mock
    private UserService userService;

    @Mock
    private ChannelService channelService;

    @Mock
    private PermissionService permissionService;

    @InjectMocks 
    private MessageService messageService;

    private String testSenderUsername = "testSenderUsername";
    private String testReceiverUsername = "testReceiverUsername";
    private Room testRoom;
    private Long ROOM_ID = 1L;
    private Long CHANNEL_ID = 2L;
    private Long MESSAGE_ID = 3L;
    private Long SERVER_ID = 4L;

    private String testContent = "testContent";
    private User sender;
    private User receiver;
    private Channel testChannel;
    private ChannelFolder testFolder;
    private Server testServer;

    @BeforeEach
    void setUp() {
        // Инициализация сервера
        testServer = new Server();
        testServer.setId(SERVER_ID);
        testServer.setName("testServer");

        // Инициализация папки канала
        testFolder = new ChannelFolder();
        testFolder.setId(1L);
        testFolder.setName("testFolder");
        testFolder.setServer(testServer);

        // Инициализация канала
        testChannel = new Channel();
        testChannel.setId(CHANNEL_ID);
        testChannel.setName("testChannel");
        testChannel.setFolder(testFolder);

        // Инициализация комнаты
        testRoom = new Room();
        testRoom.setId(ROOM_ID);
        testRoom.setName("testRoomName");
        testRoom.setType(RoomType.PRIVATE);
        testRoom.setIsActive(true);
        testRoom.setCreatedAt(LocalDateTime.now());

        // Инициализация пользователей
        sender = new User();
        sender.setId(1L);
        sender.setUsername(testSenderUsername);
    
        receiver = new User();
        receiver.setId(2L);
        receiver.setUsername(testReceiverUsername);
    
        testRoom.setMembers(List.of(sender, receiver));
    }

    @Test
    void sendPrivateMessage_shouldReturnMessageResponse_whenValidData() {
        // Arrange
        when(roomService.createOrGetPrivateRoom(testSenderUsername, testReceiverUsername))
            .thenReturn(testRoom);
        when(userService.getUser(testSenderUsername)).thenReturn(sender);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(1L);
            return msg;
        });

        // Act
        MessageResponse response = messageService.sendPrivateMessage(
            testSenderUsername, 
            testReceiverUsername, 
            testContent
        );

        // Assert
        assertNotNull(response);
        verify(messageRepository).save(any(Message.class));
        verify(simpMessagingTemplate, times(testRoom.getMembers().size()))
            .convertAndSendToUser(anyString(), eq("/queue/messages"), any(MessageResponse.class));
    }

    @Test
    void sendGroupMessage_shouldReturnMessageResponse_whenValidData() {
        // Arrange
        when(roomService.getRoom(ROOM_ID, testSenderUsername)).thenReturn(testRoom);
        when(userService.getUser(testSenderUsername)).thenReturn(sender);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(1L);
            return msg;
        });

        // Act
        MessageResponse response = messageService.sendGroupMessage(
            testSenderUsername, 
            ROOM_ID, 
            testContent
        );

        // Assert
        assertNotNull(response);
        assertEquals(ROOM_ID, response.getRoomId());
        verify(messageRepository).save(any(Message.class));
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/room." + ROOM_ID), any(MessageResponse.class));
    }

    @Test
    void sendGroupMessage_shouldThrowException_whenUserNotMember() {
        // Arrange
        User nonMember = new User();
        nonMember.setId(99L);
        nonMember.setUsername("nonMember");
        
        when(roomService.getRoom(ROOM_ID, "nonMember")).thenReturn(testRoom);
        when(userService.getUser("nonMember")).thenReturn(nonMember);

        // Act & Assert
        assertThrows(InsufficientPermissionsException.class, () -> 
            messageService.sendGroupMessage("nonMember", ROOM_ID, testContent)
        );
    }

    @Test
    void sendChannelMessage_shouldReturnChannelMessageResponse_whenValidData() {
        // Arrange
        when(userService.getUser(testSenderUsername)).thenReturn(sender);
        when(channelService.getChannel(CHANNEL_ID)).thenReturn(testChannel);
        when(permissionService.canUserSendMessages(sender.getId(), CHANNEL_ID)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(1L);
            return msg;
        });

        // Act
        ChannelMessageResponse response = messageService.sendChannelMessage(
            testSenderUsername, 
            CHANNEL_ID, 
            testContent
        );

        // Assert
        assertNotNull(response);
        assertEquals(CHANNEL_ID, response.getChannelId());
        verify(messageRepository).save(any(Message.class));
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/channel." + CHANNEL_ID), any(ChannelMessageResponse.class));
    }

    @Test
    void sendChannelMessage_shouldThrowException_whenNoPermissions() {
        // Arrange
        when(userService.getUser(testSenderUsername)).thenReturn(sender);
        when(channelService.getChannel(CHANNEL_ID)).thenReturn(testChannel);
        when(permissionService.canUserSendMessages(sender.getId(), CHANNEL_ID)).thenReturn(false);

        // Act & Assert
        assertThrows(InsufficientPermissionsException.class, () -> 
            messageService.sendChannelMessage(testSenderUsername, CHANNEL_ID, testContent)
        );
    }

    @Test
    void editMessage_shouldReturnUpdatedResponse_whenValidData() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(sender);
        existingMessage.setContent("oldContent");
        existingMessage.setSentAt(LocalDateTime.now().minusMinutes(5));
        existingMessage.setRoom(testRoom); // Добавляем привязку к комнате

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser(testSenderUsername)).thenReturn(sender);
        when(messageRepository.save(any(Message.class))).thenReturn(existingMessage);

        // Act
        ShortMessageWrapped response = messageService.editMessage(
            MESSAGE_ID, 
            testSenderUsername, 
            "newContent"
        );

        // Assert
        assertNotNull(response);
        assertEquals("newContent", response.getContent());
        verify(messageRepository).save(any(Message.class));
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/room." + ROOM_ID), any(MessageResponse.class));
    }

    @Test
    void editMessage_shouldThrowException_whenNotSender() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(receiver); // Другой отправитель

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser(testSenderUsername)).thenReturn(sender);

        // Act & Assert
        assertThrows(InsufficientPermissionsException.class, () -> 
            messageService.editMessage(MESSAGE_ID, testSenderUsername, "newContent")
        );
    }

    @Test
    void editMessage_shouldThrowException_whenMessageDeleted() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(sender);
        existingMessage.setDeletedAt(LocalDateTime.now());

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser(testSenderUsername)).thenReturn(sender);

        // Act & Assert
        assertThrows(CannotEditDeletedMessageException.class, () -> 
            messageService.editMessage(MESSAGE_ID, testSenderUsername, "newContent")
        );
    }

    @Test
    void deleteMessage_shouldMarkAsDeleted_whenSender() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(sender);
        existingMessage.setRoom(testRoom);

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser(testSenderUsername)).thenReturn(sender);

        // Act
        messageService.deleteMessage(MESSAGE_ID, testSenderUsername);

        // Assert
        assertNotNull(existingMessage.getDeletedAt());
        verify(messageRepository).save(existingMessage);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/room." + ROOM_ID), any(MessageResponse.class));
    }

    @Test
    void deleteMessage_shouldMarkAsDeleted_whenAdmin() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(receiver); // Другой отправитель
        existingMessage.setChannel(testChannel);

        User adminUser = new User();
        adminUser.setId(3L);
        adminUser.setUsername("admin");

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser("admin")).thenReturn(adminUser);
        when(permissionService.hasPermissionInServer(
            adminUser.getId(), 
            testServer.getId(), // Используем напрямую testServer.getId()
            Permission.ADMINISTRATOR
        )).thenReturn(true);

        // Act
        messageService.deleteMessage(MESSAGE_ID, "admin");

        // Assert
        assertNotNull(existingMessage.getDeletedAt());
        verify(messageRepository).save(existingMessage);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/channel." + CHANNEL_ID), any(ChannelMessageResponse.class));
    }

    @Test
    void deleteMessage_shouldThrowException_whenNoPermissions() {
        // Arrange
        Message existingMessage = new Message();
        existingMessage.setId(MESSAGE_ID);
        existingMessage.setSender(receiver); // Другой отправитель

        User regularUser = new User();
        regularUser.setId(3L);
        regularUser.setUsername("regular");

        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(existingMessage));
        when(userService.getUser("regular")).thenReturn(regularUser);

        // Act & Assert
        assertThrows(InsufficientPermissionsException.class, () -> 
            messageService.deleteMessage(MESSAGE_ID, "regular")
        );
    }
}
