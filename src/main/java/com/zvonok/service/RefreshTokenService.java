package com.zvonok.service;

import com.zvonok.exception.InvalidRefreshTokenException;
import com.zvonok.exception.RefreshTokenExpiredException;
import com.zvonok.exception.RefreshTokenRevokedException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.RefreshToken;
import com.zvonok.model.User;
import com.zvonok.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    public RefreshToken getRefreshTokenByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                    .orElseThrow(() -> new InvalidRefreshTokenException(
                        HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage()));

    }

    public RefreshToken createToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(generateTokenValue());
        refreshToken.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validate(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException(
                        HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage()));

        if (token.isRevoked()) {
            throw new RefreshTokenRevokedException(
                    HttpResponseMessage.HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE.getMessage());
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenExpiredException(
                    HttpResponseMessage.HTTP_REFRESH_TOKEN_EXPIRED_RESPONSE_MESSAGE.getMessage());
        }

        return token;
    }

    public RefreshToken rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        return createToken(oldToken.getUser());
    }

    public void revoke(String tokenValue) {
        RefreshToken token =  refreshTokenRepository.findByToken(tokenValue)
            .orElseThrow(() -> new InvalidRefreshTokenException(
                        HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage()));

        if (token.isRevoked()) {
            throw new RefreshTokenRevokedException(
                HttpResponseMessage.HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE.getMessage());
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    public void revokeAllForUser(String tokenValue, Long userId) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
            .orElseThrow(() -> new InvalidRefreshTokenException(
                        HttpResponseMessage.HTTP_INVALID_REFRESH_TOKEN_RESPONSE_MESSAGE.getMessage()));

        if (token.isRevoked()) {
            throw new RefreshTokenRevokedException(
                HttpResponseMessage.HTTP_REFRESH_TOKEN_REVOKED_RESPONSE_MESSAGE.getMessage());
        } 
        refreshTokenRepository.revokeAllActiveByUserId(userId);
    }

    public int cleanUpExpired() {
        return refreshTokenRepository.deleteAllExpired(LocalDateTime.now());
    }

    private String generateTokenValue() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        String part1 = UUID.randomUUID().toString();
        String part2 = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return part1 + "." + part2;
    }
}

