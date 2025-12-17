package com.zvonok.unittests.security;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zvonok.security.JwtTokenProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
public class JwtTokenProviderTest {
    private JwtTokenProvider jwtTokenProvider; 

    private long testJwtExpirationMs = 3600000L;
    private SecretKey testJwtSecret;
    private Long testUserId = 1L;
    private String testUserName = "testUserName";
    private String testValidToken;
    private String testInvalidToken = "testInvalidToken";

    @BeforeEach
    void setUp() {
        testJwtSecret = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        
        jwtTokenProvider = new JwtTokenProvider();
        jwtTokenProvider.setJwtExpirationMs(testJwtExpirationMs);
        jwtTokenProvider.setJwtSecret(
            Base64.getEncoder().encodeToString(testJwtSecret.getEncoded())
        );
        testValidToken = jwtTokenProvider.generateToken(testUserName, testUserId);
    }

    private Claims parseToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(testJwtSecret.getEncoded()))
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    // Generate
    @Test
    @DisplayName("generate - успешная генерация токена")
    void generateToken_shouldReturnToken_whenValidData() {
        // Act
        String token = jwtTokenProvider.generateToken(testUserName, testUserId);
        // Assert
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    @DisplayName("generate - Проверка содержимого JWT токена")
    void generatedToken_shouldContainCorrectClaims() {
        // Act
        String token = jwtTokenProvider.generateToken(testUserName, testUserId);
        Claims claims = parseToken(token);

        // Assert
        assertEquals(testUserName, claims.getSubject());
        assertEquals(testUserId, claims.get("userId", Long.class));
    }

    // Validate
    @Test
    @DisplayName("validate - проверка валидности токена")
    void validateToken_shouldReturnTrue_whenTokenIsValid() {
        // Act
        boolean result = jwtTokenProvider.isValidToken(testValidToken);

        // Assert
        assertEquals(result, true);
    }

    @Test
    @DisplayName("validate - проверка невалидности токена")
    void validateToken_shouldReturnFalse_whenTokenIsInvalid() {
        // Act
        boolean result = jwtTokenProvider.isValidToken(testInvalidToken);

        // Assert
        assertEquals(result, false);
    }

    // GetClaims
    @Test
    @DisplayName("getClaims - Успешное извлечение claims из валидного токена")
    void getClaims_couldReturnClaims_whenTokenIsValid() throws Exception {
        // Act
        Method method = JwtTokenProvider.class.getDeclaredMethod("getClaims", String.class);
        method.setAccessible(true);
        Claims claims = (Claims) method.invoke(jwtTokenProvider, testValidToken);

        // Assert
        assertAll(
            () -> assertEquals(claims.getSubject(), testUserName),
            () -> assertEquals(((Number) claims.get("userId")).longValue() , testUserId)
        );
    }

    @Test
    @DisplayName("getClaims - Выбрасывает исключение при невалидном токене")
    void getClaims_couldThrowException_whenInvalidToken() throws Exception {
        // Act
        Method method = JwtTokenProvider.class.getDeclaredMethod("getClaims", String.class);
        method.setAccessible(true);

        // Assert
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(jwtTokenProvider, testInvalidToken);
        });

        assertTrue(ex instanceof InvocationTargetException);
    }
}