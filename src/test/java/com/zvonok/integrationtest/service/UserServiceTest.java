package com.zvonok.integrationtest.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import com.zvonok.exception.UserNotFoundException;
import com.zvonok.model.User;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserService;

@DataJpaTest
public class UserServiceTest {

    @Autowired
    private UserRepository repository;

    private UserService service;
	private S3Service s3Service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, s3Service);
    }

    @Test
    public void getUser_shouldReturnUser_whenUserExists() {
        // Arrange - создаем пользователя в базе
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password123");
        user.setIsEmailVerified(false);

        final User savedUser = repository.save(user);
        
        // Act
        final User foundUser = service.getUser(savedUser.getId());

        // Assert
        assertNotNull(foundUser);
        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals(savedUser.getUsername(), foundUser.getUsername());
        assertEquals(savedUser.getEmail(), foundUser.getEmail());
    }

    @Test
    public void getUser_shouldThrowException_whenUserNotFound() {
        // Arrange
        Long nonExistentId = 999L;

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            service.getUser(nonExistentId);
        });
    }
}
