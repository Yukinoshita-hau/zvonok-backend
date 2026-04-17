package com.zvonok.unittests.service;

import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.UserWIthThisUsernameAlreadyExistException;
import com.zvonok.exception.UserWithThisEmailAlreadyExistException;
import com.zvonok.model.User;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.CreateUserDto;
import com.zvonok.service.dto.UpdateUserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	@InjectMocks
	private S3Service storageService;

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
		String username = "exception";
		when(userRepository.findByUsername(username)).thenReturn(Optional.of(new User()));
		createDto.setUsername(username);

		assertThrows(UserWIthThisUsernameAlreadyExistException.class,
				() -> userService.createUser(createDto));

		verify(userRepository, never()).findByEmail(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void createUser_shouldThrowException_whenEmailExist() {
		String email = "exception";
		when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
		when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));
		createDto.setUsername("newuser");
		createDto.setEmail(email);

		assertThrows(UserWithThisEmailAlreadyExistException.class,
				() -> userService.createUser(createDto));

		verify(userRepository, never()).save(any());
	}

	@Test
	void updateMyUser_shouldThrowException_whenUsernameNotFound() {
		String username = "missing-user";
		when(userRepository.findByEmail(username)).thenReturn(Optional.empty());
		when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

		assertThrows(UserNotFoundException.class,
				() -> userService.updateMyUser(username, updateDto));

		verify(userRepository, never()).save(any());
	}

	@Test
	void updateMyUser_shouldThrowException_whenUsernameAlreadyExist() {
		String newUsername = "exception";
		User existingUser = new User();
		existingUser.setUsername("other-user");
		when(userRepository.findByEmail(testUser.getUsername())).thenReturn(Optional.empty());
		when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
		when(userRepository.findByUsername(newUsername)).thenReturn(Optional.of(existingUser));
		updateDto.setUsername(newUsername);

		assertThrows(UserWIthThisUsernameAlreadyExistException.class,
				() -> userService.updateMyUser(testUser.getUsername(), updateDto));

		verify(userRepository, never()).save(any());
	}

	@Test
	void updateMyUser_shouldThrowException_whenEmailAlreadyExist() {
		String newEmail = "exception@example.com";
		User existingUser = new User();
		existingUser.setUsername("other-user");
		when(userRepository.findByEmail(testUser.getUsername())).thenReturn(Optional.empty());
		when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
		when(userRepository.findByEmail(newEmail)).thenReturn(Optional.of(existingUser));
		updateDto.setEmail(newEmail);

		assertThrows(UserWithThisEmailAlreadyExistException.class,
				() -> userService.updateMyUser(testUser.getUsername(), updateDto));

		verify(userRepository, never()).save(any());
	}

	@Test
	void updateMyUser_shouldNotThrowException_whenUsernameEqual() {
		when(userRepository.findByEmail(testUser.getUsername())).thenReturn(Optional.empty());
		when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
		when(userRepository.save(any(User.class))).thenReturn(testUser);
		updateDto.setUsername(testUser.getUsername());

		assertDoesNotThrow(() -> userService.updateMyUser(testUser.getUsername(), updateDto));

		verify(userRepository, org.mockito.Mockito.times(2)).findByUsername(testUser.getUsername());
		verify(userRepository).save(any(User.class));
	}

	@Test
	void updateMyUser_shouldNotThrowException_whenEmailEqual() {
		when(userRepository.findByEmail(testUser.getUsername())).thenReturn(Optional.empty());
		when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
		when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
		when(userRepository.save(any(User.class))).thenReturn(testUser);
		updateDto.setEmail(testUser.getEmail());

		assertDoesNotThrow(() -> userService.updateMyUser(testUser.getUsername(), updateDto));

		verify(userRepository).findByEmail(testUser.getEmail());
		verify(userRepository).save(any(User.class));
	}
}



