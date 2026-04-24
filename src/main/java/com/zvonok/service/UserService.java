package com.zvonok.service;

import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.UserWIthThisUsernameAlreadyExistException;
import com.zvonok.exception.UserWithThisEmailAlreadyExistException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.User;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.dto.CreateUserDto;
import com.zvonok.service.dto.UpdateUserDto;
import com.zvonok.service.dto.UserShortDto;
import com.zvonok.service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing user entities and user-related operations.
 * Сервис для управления сущностями пользователей и операциями, связанными с пользователями.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserEventService userEventService;

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(
                        HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public UserShortDto getUserShort(Long id) {
        return userMapper.toUserShortDto(getUser(id));
    }

    public User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(() -> new UserNotFoundException(
                        HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
    }

    /**
     * Создает нового пользователя.
     * Creates a new user.
     */
    @Transactional
    public User createUser(CreateUserDto userDto) {
        userRepository.findByUsername(userDto.getUsername())
                .ifPresent(existing -> {
                    throw new UserWIthThisUsernameAlreadyExistException(
                            HttpResponseMessage.HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE.getMessage());
                });

        userRepository.findByEmail(userDto.getEmail())
                .ifPresent(existing -> {
                    throw new UserWithThisEmailAlreadyExistException(
                            HttpResponseMessage.HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE.getMessage());
                });

        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(userDto.getPassword());

        return userRepository.save(user);
    }

    /**
     * Обновляет данные пользователя.
     * Updates user data.
     */
    @Transactional
    public User updateUser(Long id, UpdateUserDto userDto) {
        User user = getUser(id);

        if (userDto.getUsername() != null && !userDto.getUsername().isEmpty()) {
            userRepository.findByUsername(userDto.getUsername())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(id)) {
                            throw new UserWIthThisUsernameAlreadyExistException(HttpResponseMessage.HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE.getMessage());
                        }
                    });
            user.setUsername(userDto.getUsername());
        }

        if (userDto.getEmail() != null && !userDto.getEmail().isEmpty()) {
            userRepository.findByEmail(userDto.getEmail())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(id)) {
                            throw new UserWithThisEmailAlreadyExistException(HttpResponseMessage.HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE.getMessage());
                        }
                    });
            user.setEmail(userDto.getEmail());
        }

        if (userDto.getAvatarUrl() != null) {
            user.setAvatarUrl(userDto.getAvatarUrl());
        }

        User savedUser = userRepository.save(user);
        userEventService.notifyUserProfileUpdated(savedUser);
        return savedUser;
    }

    /**
     * Удаляет пользователя по ID.
     * Deletes a user by ID.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        userRepository.delete(user);
    }

    /**
     * Обновляет время последней активности пользователя.
     * Updates user's last seen timestamp.
     */
    @Transactional
    public void updateLastSeenAt(Long userId, LocalDateTime lastSeenAt) {
        User user = getUser(userId);
        user.setLastSeenAt(lastSeenAt);
        userRepository.save(user);
    }

    /**
     * Обновляет время последней активности пользователя (перегруженный метод для username).
     * Updates user's last seen timestamp (overloaded method for username).
     */
    @Transactional
    public void updateLastSeenAt(String username, LocalDateTime lastSeenAt) {
        User user = getUser(username);
        user.setLastSeenAt(lastSeenAt);
        userRepository.save(user);
    }
}
