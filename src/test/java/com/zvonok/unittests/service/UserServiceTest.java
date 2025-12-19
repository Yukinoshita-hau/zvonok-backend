package com.zvonok.unittests.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.UserWIthThisUsernameAlreadyExistException;
import com.zvonok.exception.UserWithThisEmailAlreadyExistException;
import com.zvonok.model.User;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.CreateUserDto;
import com.zvonok.service.dto.UpdateUserDto;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    private CreateUserDto createDto;
    private UpdateUserDto updateDto;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setAvatarUrl("avatarUrl");

        createDto = new CreateUserDto();
        updateDto = new UpdateUserDto();
    }

    @Test
    void createUser_shouldThrowException_whenUsernameExist() {
        // Arrange
        String username = "exception";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new User()));

        createDto.setUsername(username);

        // Act
        assertThrows(UserWIthThisUsernameAlreadyExistException.class,
            () -> userService.createUser(createDto));

        // Assert
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_shouldThrowException_whenEmailExist() {
        // Arrange
        String email = "exception";
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));

        createDto.setUsername("newuser");
        createDto.setEmail(email);

        // Act
        assertThrows(UserWithThisEmailAlreadyExistException.class,
            () -> userService.createUser(createDto));

        // Assert
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenIdNotFound() {
        // Arrange 
        Long id = 2L;
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        assertThrows(UserNotFoundException.class,
            () -> userService.updateUser(id, updateDto)
        );

        // Assert
        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenUsernameAlreadyExist() {
        // Arrange
        String username = "exception";
        User existingUser = new User();
        existingUser.setId(2L);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));
        updateDto.setUsername(username);

        // Act
        assertThrows(UserWIthThisUsernameAlreadyExistException.class, 
            () -> userService.updateUser(testUser.getId(), updateDto)
        );

        // Assert
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenEmailAlreadyExist() {
        // Arrange
        String email = "exception";
        User existingUser = new User();
        existingUser.setId(2L);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        updateDto.setEmail(email);

        // Act
        assertThrows(UserWithThisEmailAlreadyExistException.class, 
            () -> userService.updateUser(testUser.getId(), updateDto)
        );

        // Assert
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldNotThrowException_whenUserEqual() {
        // Arrange
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        updateDto.setUsername(testUser.getUsername());
        // Act
        assertDoesNotThrow(() -> userService.updateUser(testUser.getId(), updateDto));

        // Assert
        verify(userRepository).findByUsername(testUser.getUsername());
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void updateUser_shouldNotThrowException_whenEmailEqual() {
        // Arrange
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        updateDto.setEmail(testUser.getEmail());
        // Act
        assertDoesNotThrow(() -> userService.updateUser(testUser.getId(), updateDto));

        // Assert
        verify(userRepository).findByEmail(testUser.getEmail());
        verify(userRepository).save(any(User.class));
    }
}
