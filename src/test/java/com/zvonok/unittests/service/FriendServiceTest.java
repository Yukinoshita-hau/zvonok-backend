package com.zvonok.unittests.service;

import com.zvonok.exception.*;
import com.zvonok.model.FriendRequest;
import com.zvonok.model.Friendship;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.FriendRequestStatus;
import com.zvonok.repository.FriendRequestRepository;
import com.zvonok.repository.FriendshipRepository;
import com.zvonok.service.FriendService;
import com.zvonok.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FriendServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private FriendService friendService;

    private User sender;
    private User receiver;
    private FriendRequest pendingRequest;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(1L);
        sender.setUsername("senderUser");

        receiver = new User();
        receiver.setId(2L);
        receiver.setUsername("receiverUser");

        pendingRequest = new FriendRequest();
        pendingRequest.setId(1L);
        pendingRequest.setSender(sender);
        pendingRequest.setReceiver(receiver);
        pendingRequest.setStatus(FriendRequestStatus.PENDING);
    }

    @Test
    void sendFriendRequest_shouldCreateRequest_whenValidData() {
        // Arrange
        when(userService.getUser(1L)).thenReturn(sender);
        when(userService.getUser(2L)).thenReturn(receiver);
        when(friendshipRepository.existsByUserOneIdAndUserTwoId(anyLong(), anyLong())).thenReturn(false);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatusIn(anyLong(), anyLong(), any()))
            .thenReturn(false);
        when(friendRequestRepository.save(any())).thenReturn(pendingRequest);

        // Act
        FriendRequest result = friendService.sendFriendRequest(1L, 2L);

        // Assert
        assertNotNull(result);
        assertEquals(FriendRequestStatus.PENDING, result.getStatus());
        verify(friendRequestRepository).save(any(FriendRequest.class));
    }

    @Test
    void sendFriendRequest_shouldThrowException_whenSelfRequest() {
        // Act & Assert
        assertThrows(CannotAddYourselfAsFriendException.class, () -> 
            friendService.sendFriendRequest(1L, 1L)
        );
    }

    @Test
    void sendFriendRequest_shouldThrowException_whenDuplicateRequest() {
        // Arrange
        when(userService.getUser(1L)).thenReturn(sender);
        when(userService.getUser(2L)).thenReturn(receiver);
        when(friendRequestRepository.existsBySenderIdAndReceiverIdAndStatusIn(anyLong(), anyLong(), any()))
            .thenReturn(true);

        // Act & Assert
        assertThrows(FriendRequestAlreadyExistsException.class, () -> 
            friendService.sendFriendRequest(1L, 2L)
        );
    }

    @Test
    void acceptFriendRequest_shouldCreateFriendship_whenValidRequest() {
        // Arrange
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(friendshipRepository.save(any())).thenReturn(new Friendship());

        // Act
        Friendship result = friendService.acceptFriendRequest(1L, 2L);

        // Assert
        assertNotNull(result);
        assertEquals(FriendRequestStatus.ACCEPTED, pendingRequest.getStatus());
        verify(friendshipRepository).save(any(Friendship.class));
        verify(friendRequestRepository).save(pendingRequest);
    }

    @Test
    void acceptFriendRequest_shouldThrowException_whenWrongReceiver() {
        // Arrange
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));

        // Act & Assert
        assertThrows(FriendRequestActionNotAllowedException.class, () -> 
            friendService.acceptFriendRequest(1L, 999L)
        );
    }

    @Test
    void acceptFriendRequest_shouldThrowException_whenNotPending() {
        // Arrange
        pendingRequest.setStatus(FriendRequestStatus.ACCEPTED);
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));

        // Act & Assert
        assertThrows(FriendRequestActionNotAllowedException.class, () -> 
            friendService.acceptFriendRequest(1L, 2L)
        );
    }

    @Test
    void rejectFriendRequest_shouldUpdateStatus_whenValidRequest() {
        // Arrange
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(friendRequestRepository.save(pendingRequest)).thenReturn(pendingRequest);

        // Act
        FriendRequest result = friendService.rejectFriendRequest(1L, 2L);

        // Assert
        assertNotNull(result);
        assertEquals(FriendRequestStatus.REJECTED, result.getStatus());
        verify(friendRequestRepository).save(pendingRequest);
    }

    @Test
    void rejectFriendRequest_shouldThrowException_whenWrongReceiver() {
        // Arrange
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));

        // Act & Assert
        assertThrows(FriendRequestActionNotAllowedException.class, () -> 
            friendService.rejectFriendRequest(1L, 999L)
        );
    }

    @Test
    void rejectFriendRequest_shouldThrowException_whenNotPending() {
        // Arrange
        pendingRequest.setStatus(FriendRequestStatus.ACCEPTED);
        when(friendRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));

        // Act & Assert
        assertThrows(FriendRequestActionNotAllowedException.class, () -> 
            friendService.rejectFriendRequest(1L, 2L)
        );
    }
}
