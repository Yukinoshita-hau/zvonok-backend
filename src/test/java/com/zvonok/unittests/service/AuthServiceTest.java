package com.zvonok.unittests.service;

import com.zvonok.exception.InvalidUserOrPasswordException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class) // Подключает Mockito к JUnit 5
class AuthServiceTest {

    Long expirationMs = 3600000L;
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
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        // Подготовка тестовых данных перед каждым тестом
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");

        testRefreshToken = new RefreshToken();
        testRefreshToken.setToken("refresh-token-value");
        testRefreshToken.setUser(testUser);
    }
    // ==================== ТЕСТЫ РЕГИСТРАЦИИ ====================
    @Test
    @DisplayName("create - успешная регистрация пользователя")
    void register_shouldReturnsAuthResponse_whenValidData() {
        // Arrange 
        String rawPassword = "password123";
        String encodedPassword = testUser.getPassword();
        
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        
        when(userService.createUser(any(CreateUserDto.class))).thenReturn(testUser);
        when(jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId()))
                .thenReturn("jwt-access-token");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(expirationMs);
        when(refreshTokenService.createToken(testUser)).thenReturn(testRefreshToken);

        // Act
        AuthResponse response = authService.register(testUser.getUsername(), testUser.getEmail(), rawPassword);

        // Assert
        assertNotNull(response);
        assertAll(
            () -> assertEquals("jwt-access-token", response.getAccessToken()),
            () -> assertEquals(testRefreshToken.getToken(), response.getRefreshToken()),
            () -> assertEquals("Bearer", response.getTokenType()),
            () -> assertEquals(expirationMs, response.getExpiresIn())
        );

        verify(userService).createUser(argThat(dto -> 
            dto.getUsername().equals(testUser.getUsername()) &&
            dto.getEmail().equals(testUser.getEmail()) &&
            dto.getPassword().equals(encodedPassword)
        ));
        verify(passwordEncoder).encode(rawPassword);
    }



    // ==================== ТЕСТЫ ЛОГИНА ====================

    @Test
    @DisplayName("login - успешный вход с правильными данными")
    void login_shouldReturnsAuthResponse_whenValidCredentials() {
        // Arrange (Подготовка) - настраиваем поведение моков
        String usernameOrEmail = "testuser";
        String password = "password123";

        when(userService.getUserByUsernameOrEmail(usernameOrEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, testUser.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateToken(testUser.getUsername(), testUser.getId()))
                .thenReturn("jwt-access-token");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(3600000L);
        when(refreshTokenService.createToken(testUser)).thenReturn(testRefreshToken);

        // Act (Действие) - вызываем тестируемый метод
        AuthResponse response = authService.login(usernameOrEmail, password);

        // Assert (Проверка) - проверяем результат
        assertNotNull(response);
        assertEquals("jwt-access-token", response.getAccessToken());
        assertEquals("refresh-token-value", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        verify(userService).getUserByUsernameOrEmail(usernameOrEmail);
        verify(passwordEncoder).matches(password, testUser.getPassword());
        verify(userService).updateLastSeenAt(eq(testUser.getId()), any());
    }

    @Test
    @DisplayName("login - неверный пароль выбрасывает исключение")
    void login_shouldThrowsException_whenInvalidPassword() {
        // Arrange
        String usernameOrEmail = "testuser";
        String wrongPassword = "wrongpassword";

        when(userService.getUserByUsernameOrEmail(usernameOrEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPassword())).thenReturn(false);

        // Act & Assert
        assertThrows(InvalidUserOrPasswordException.class, () -> {
            authService.login(usernameOrEmail, wrongPassword);
        });

        // Проверяем, что токены НЕ создавались
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyLong());
        verify(refreshTokenService, never()).createToken(any());
    }

    // ==================== ТЕСТЫ REFRESH ====================

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

        verify(refreshTokenService).validate(refreshTokenValue);
        verify(refreshTokenService).rotate(testRefreshToken);
    }

    // ==================== ТЕСТЫ LOGOUT ====================

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
    @DisplayName("logoutFromAllDevices - отзывает все токены пользователя")
    void logoutFromAllDevices_RevokesAllUserTokens() {
        // Arrange
        when(refreshTokenService.getRefreshTokenByToken(anyString())).thenReturn(testRefreshToken);
        String refreshTokenValue = "token-to-revoke";

        // Act
        authService.logoutFromAllDevices(refreshTokenValue);

        // Assert
        verify(refreshTokenService).revokeAllForUser(refreshTokenValue, testRefreshToken.getUser().getId());
    }
}
