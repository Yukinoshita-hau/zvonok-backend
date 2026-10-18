package com.zvonok.unittests.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.zvonok.exception.*;
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
import com.zvonok.service.MessageService;
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

    @InjectMocks 
    private MessageService messageService;

    private String testSenderUsername = "testSenderUsername";
    private String testReceiverUsername = "testReceiverUsername";
    private Room testRoom;
    private Long ROOM_ID = 1L;
    private Long MESSAGE_ID = 3L;

    private String testContent = "testContent";
    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
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
        when(messageRepository.findRoomIdByMessageId(MESSAGE_ID)).thenReturn(ROOM_ID);
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
    }

    @Test
    void archivedMessageCannotBeEditedOrDeleted() {
        when(messageRepository.findRoomIdByMessageId(MESSAGE_ID)).thenReturn(null);
        assertThrows(MessageNotFoundException.class,
                () -> messageService.editMessage(MESSAGE_ID, testSenderUsername, "changed"));
        assertThrows(MessageNotFoundException.class,
                () -> messageService.deleteMessage(MESSAGE_ID, testSenderUsername));
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(simpMessagingTemplate);
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

