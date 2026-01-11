package com.zvonok.unittests.service;

import com.zvonok.exception.InvalidUserOrPasswordException;
import com.zvonok.exception.UserWIthThisUsernameAlreadyExistException;
import com.zvonok.exception.UserWithThisEmailAlreadyExistException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.RefreshToken;
import com.zvonok.model.User;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.service.AuthService;
import com.zvonok.service.RefreshTokenService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.AuthResponse;
import com.zvonok.service.dto.CreateUserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private final Long expirationMs = 3600000L;
	private final String accessToken = "jwt-access-token";
	private final String rawPassword = "rawPassword123";
	private String usernameOrEmail = "testuser";
	private final String invalidRefreshToken = "invalidRefreshToken";

	@Mock
	private UserService userService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenService refreshTokenService;

	@InjectMocks
	private AuthService authService;

	private User testUser;
	private CreateUserDto existUserDto;
	private RefreshToken testRefreshToken;

	@BeforeEach
	void setUp() {
		// Подготовка тестовых данных перед каждым тестом
		testUser = new User();
		testUser.setId(1L);
		testUser.setUsername("testuser");
		testUser.setEmail("test@example.com");
		testUser.setPassword("encodedPassword123");

		existUserDto = new CreateUserDto();
		existUserDto.setUsername("existUsername");
		existUserDto.setEmail("existEmail");
		existUserDto.setPassword("existPassword");

		testRefreshToken = new RefreshToken();
		testRefreshToken.setToken("refresh-token-value");
		testRefreshToken.setUser(testUser);
	}

	// Register

	// Happy path
	@Test
	@DisplayName("register - успешная регистрация пользователя")
	void register_shouldReturnsAuthResponse_whenValidData() {
		// Arrange
		when(passwordEncoder.encode(anyString())).thenReturn(testUser.getPassword());
		when(userService.createUser(any(CreateUserDto.class))).thenReturn(testUser);
		when(jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId()))
				.thenReturn(accessToken);
		when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(expirationMs);
		when(refreshTokenService.createToken(testUser)).thenReturn(testRefreshToken);


		// Act
		AuthResponse response =
				authService.register(testUser.getUsername(), testUser.getEmail(), rawPassword);

		// Assert
		assertNotNull(response);
		assertAll(() -> assertEquals("jwt-access-token", response.getAccessToken()),
				() -> assertEquals(testRefreshToken.getToken(), response.getRefreshToken()),
				() -> assertEquals("Bearer", response.getTokenType()),
				() -> assertEquals(expirationMs, response.getExpiresIn()));

		verify(userService)
				.createUser(argThat(dto -> dto.getUsername().equals(testUser.getUsername())
						&& dto.getEmail().equals(testUser.getEmail()) && dto.getPassword().equals(testUser.getPassword())));

		InOrder inOrder =
				inOrder(userService, jwtTokenProvider, refreshTokenService, passwordEncoder);

		inOrder.verify(passwordEncoder).encode(rawPassword);
		inOrder.verify(userService).createUser(any(CreateUserDto.class));
		inOrder.verify(jwtTokenProvider).generateToken(testUser.getUsername(), testUser.getId());
		inOrder.verify(refreshTokenService).createToken(testUser);
		inOrder.verify(jwtTokenProvider).getJwtExpirationMs();
	}

	// Bussiness logic
	@Test
	@DisplayName("register - должен кинуть исключение когда пользователь с таким username уже существует")
	void register_shouldThrowException_whenUserWithThisUsernameIsExist() {
		// Arrange
		when(userService.createUser(any(CreateUserDto.class)))
				.thenThrow(new UserWIthThisUsernameAlreadyExistException(
						HttpResponseMessage.HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE
								.getMessage()));

		// Act
		UserWIthThisUsernameAlreadyExistException exception =
				assertThrows(UserWIthThisUsernameAlreadyExistException.class,
						() -> authService.register(existUserDto.getUsername(),
								existUserDto.getEmail(), existUserDto.getPassword()));

		// Assert
		assertEquals("User with this username already exist", exception.getMessage());

		verify(userService)
				.createUser(argThat(dto -> dto.getUsername().equals(existUserDto.getUsername())
						&& dto.getEmail().equals(existUserDto.getEmail())));

		verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
		verify(refreshTokenService, never()).createToken(any(User.class));
		verify(jwtTokenProvider, never()).getJwtExpirationMs();
	}


	@Test
	@DisplayName("register - должен кинуть исключение когда пользователь с таким Email уже существует")
	void register_shouldThrowException_whenUserWithThisEmailIsExist() {
		// Arrange
		when(userService.createUser(any(CreateUserDto.class)))
				.thenThrow(new UserWithThisEmailAlreadyExistException(
						HttpResponseMessage.HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE
								.getMessage()));

		// Act
		UserWithThisEmailAlreadyExistException exception =
				assertThrows(UserWithThisEmailAlreadyExistException.class,
						() -> authService.register(existUserDto.getUsername(),
								existUserDto.getEmail(), existUserDto.getPassword()));

		// Assert
		assertEquals("User with this email already exist", exception.getMessage());

		verify(userService)
				.createUser(argThat(dto -> dto.getUsername().equals(existUserDto.getUsername())
						&& dto.getEmail().equals(existUserDto.getEmail())));

		verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
		verify(refreshTokenService, never()).createToken(any(User.class));
		verify(jwtTokenProvider, never()).getJwtExpirationMs();
	}


	// Login
	// Happy path
	@Test
	@DisplayName("login - успешный вход с правильными данными")
	void login_shouldReturnsAuthResponse_whenValidCredentials() {
		// Arrange
		when(userService.getUserByUsernameOrEmail(usernameOrEmail))
				.thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches(rawPassword, testUser.getPassword())).thenReturn(true);
		when(jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId()))
				.thenReturn(accessToken);
		when(refreshTokenService.createToken(testUser)).thenReturn(testRefreshToken);
		when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(expirationMs);

		// Act
		AuthResponse response = authService.login(usernameOrEmail, rawPassword);

		// Assert
		assertNotNull(response);
		assertAll(() -> assertEquals("jwt-access-token", response.getAccessToken()),
				() -> assertEquals("refresh-token-value", response.getRefreshToken()),
				() -> assertEquals("Bearer", response.getTokenType()));

		verify(userService).getUserByUsernameOrEmail(usernameOrEmail);
		verify(passwordEncoder).matches(rawPassword, testUser.getPassword());
		verify(userService).updateLastSeenAt(eq(testUser.getId()), any());

		InOrder inOrder = inOrder(userService, passwordEncoder);

		inOrder.verify(userService).getUserByUsernameOrEmail(usernameOrEmail);
		inOrder.verify(passwordEncoder).matches(rawPassword, testUser.getPassword());
		inOrder.verify(userService).updateLastSeenAt(eq(testUser.getId()), any());
	}

	// Bussiness logic
	@Test
	@DisplayName("login - неверный логин или пароль выбрасывают исключение")
	void login_shouldThrowsException_whenInvalidUsernameOrPassword() {
		// Arrange
		final String invalidUsernameOrPassword = "invalidUsernameOrPassword";
		when(userService.getUserByUsernameOrEmail(invalidUsernameOrPassword))
				.thenReturn(Optional.empty());
		// .thenThrow(new InvalidUserOrPasswordException(
		// HttpResponseMessage.HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE
		// .getMessage()));

		// Act
		InvalidUserOrPasswordException exception =
				assertThrows(InvalidUserOrPasswordException.class,
						() -> authService.login(invalidUsernameOrPassword, rawPassword));

		// Assert
		assertEquals(
				HttpResponseMessage.HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE.getMessage(),
				exception.getMessage());

		verify(passwordEncoder, never()).matches(anyString(), anyString());
		verify(userService, never()).updateLastSeenAt(anyLong(), any(LocalDateTime.class));
		verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
		verify(refreshTokenService, never()).createToken(any(User.class));
		verify(jwtTokenProvider, never()).getJwtExpirationMs();
	}

	@Test
	@DisplayName("login - неверный пароль выбрасывает исключение")
	void login_shouldThrowsException_whenInvalidPassword() {
		// Arrange
		String wrongPassword = "wrongpassword";

		when(userService.getUserByUsernameOrEmail(usernameOrEmail))
				.thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches(wrongPassword, testUser.getPassword())).thenReturn(false);

		// Act
		InvalidUserOrPasswordException exception =
				assertThrows(InvalidUserOrPasswordException.class, () -> {
					authService.login(usernameOrEmail, wrongPassword);
				});

		// Assert
		assertNotNull(exception);

		assertEquals("Invalid user or password", exception.getMessage());

		verify(userService, never()).updateLastSeenAt(anyLong(), any(LocalDateTime.class));
		verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
		verify(refreshTokenService, never()).createToken(any());
	}

	// Refresh
	@Test
	@DisplayName("refresh - успешное обновление токена")
	void refresh_shouldReturnsNewTokens_whenValidToken() {
		// Arrange
		String refreshTokenValue = "valid-refresh-token";
		RefreshToken rotatedToken = new RefreshToken();
		rotatedToken.setToken("new-refresh-token");

		when(refreshTokenService.validate(refreshTokenValue)).thenReturn(testRefreshToken);
		when(refreshTokenService.rotate(testRefreshToken)).thenReturn(rotatedToken);
		when(jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId()))
				.thenReturn("new-access-token");
		when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(3600000L);

		// Act
		AuthResponse response = authService.refresh(refreshTokenValue);

		// Assert
		assertNotNull(response);
		assertEquals("new-access-token", response.getAccessToken());
		assertEquals("new-refresh-token", response.getRefreshToken());

		InOrder inOrder = inOrder(refreshTokenService, jwtTokenProvider);

		inOrder.verify(refreshTokenService).validate(refreshTokenValue);
		inOrder.verify(refreshTokenService).rotate(testRefreshToken);
		inOrder.verify(jwtTokenProvider).generateToken(testUser.getUsername(), testUser.getId());
		inOrder.verify(jwtTokenProvider).getJwtExpirationMs();
	}

	@Test
	@DisplayName("refresh - должен выкинуть исключение если refreshToken невалидный")
	void refresh_shouldThrowException_whenInvalidRefreshToken() {
		// Arrange
		when(refreshTokenService.validate(invalidRefreshToken))
				.thenThrow(new RuntimeException("Invalid token"));

		// Act
		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> authService.refresh(invalidRefreshToken));

		// Assert
		assertEquals("Invalid token", exception.getMessage());

		verify(refreshTokenService, never()).rotate(any(RefreshToken.class));
		verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
		verify(jwtTokenProvider, never()).getJwtExpirationMs();
	}


	// Logout
	@Test
	@DisplayName("logout - отзывает refresh токен")
	void logout_RevokesRefreshToken() {
		// Arrange
		String refreshTokenValue = "token-to-revoke";

		// Act
		authService.logout(refreshTokenValue);

		// Assert - проверяем, что метод был вызван с правильным аргументом
		verify(refreshTokenService).revoke(refreshTokenValue);
	}

	@Test
	@DisplayName("logout - должен выбросить исключение при невалидном токене")
	void logout_shouldThrowException_whenInvalidRefreshToken() {
		// Arrange
		doThrow(new RuntimeException("Invalid token")).when(refreshTokenService)
				.revoke(invalidRefreshToken);

		// Act
		RuntimeException exception =
				assertThrows(RuntimeException.class, () -> authService.logout(invalidRefreshToken));

		// Assert
		assertEquals("Invalid token", exception.getMessage());

		verify(refreshTokenService, times(1)).revoke(invalidRefreshToken);
	}

	@Test
	@DisplayName("logoutFromAllDevices - отзывает все токены пользователя")
	void logoutFromAllDevices_RevokesAllUserTokens() {
		// Arrange
		when(refreshTokenService.getRefreshTokenByToken(anyString())).thenReturn(testRefreshToken);
		String refreshTokenValue = "token-to-revoke";

		// Act
		authService.logoutFromAllDevices(refreshTokenValue);

		// Assert
		verify(refreshTokenService).revokeAllForUser(refreshTokenValue,
				testRefreshToken.getUser().getId());
	}

	@Test
	@DisplayName("logoutFromAllDevices - должен выбросить исключение при невалидном токене")
	void logoutFromAllDevices_shouldThrowException_whenInvalidLogoutToken() {
		// Arrange
		when(refreshTokenService.getRefreshTokenByToken(invalidRefreshToken))
				.thenThrow(new RuntimeException("Invalid token"));

		// Act
		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> authService.logoutFromAllDevices(invalidRefreshToken));

		// Assert
		assertEquals("Invalid token", exception.getMessage());

		verify(refreshTokenService, times(1)).getRefreshTokenByToken(invalidRefreshToken);

		verify(refreshTokenService, never()).createToken(any(User.class));
		verify(jwtTokenProvider, never()).getJwtExpirationMs();
	}
}
