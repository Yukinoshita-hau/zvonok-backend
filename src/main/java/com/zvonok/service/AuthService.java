package com.zvonok.service;

import com.zvonok.exception.InvalidUserOrPasswordException;
import com.zvonok.model.RefreshToken;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.User;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.service.dto.AuthResponse;
import com.zvonok.service.dto.CreateUserDto;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for handling user authentication operations (registration and login).
 * Сервис для обработки операций аутентификации пользователей (регистрация и вход).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(String username, String email, String password) {
        // UserService.createUser уже проверяет уникальность email и username
        // Создаем пользователя через UserService с зашифрованным паролем
        CreateUserDto userDto = new CreateUserDto();
        userDto.setUsername(username);
        userDto.setEmail(email);
        userDto.setPassword(passwordEncoder.encode(password));

        User savedUser = userService.createUser(userDto);
        return buildAuthResponse(savedUser);
    }

    public AuthResponse login(String usernameOrEmail, String password) {
        // Для безопасности не указываем, что именно неверно (username или password)
        User user = userService.getUserByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(() -> new InvalidUserOrPasswordException(
                    HttpResponseMessage.HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE.getMessage()));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidUserOrPasswordException(
                    HttpResponseMessage.HTTP_INVALID_USER_OR_PASSWORD_RESPONSE_MESSAGE.getMessage());
        }
        
        // Обновляем lastSeenAt через UserService
        LocalDateTime now = LocalDateTime.now();
        userService.updateLastSeenAt(user.getId(), now);
        user.setLastSeenAt(now); // Обновляем локальную копию для использования ниже

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken validToken = refreshTokenService.validate(refreshTokenValue);
        User user = validToken.getUser();
        RefreshToken rotated = refreshTokenService.rotate(validToken);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId());
        return new AuthResponse(
                accessToken,
                rotated.getToken(),
                "Bearer",
                jwtTokenProvider.getJwtExpirationMs());
    }

    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }

    public void logoutFromAllDevices(String refreshTokenValue) {
        Long userIdFromToken = refreshTokenService.getRefreshTokenByToken(refreshTokenValue).getUser().getId();
        refreshTokenService.revokeAllForUser(refreshTokenValue, userIdFromToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer",
                jwtTokenProvider.getJwtExpirationMs());
    }
}
