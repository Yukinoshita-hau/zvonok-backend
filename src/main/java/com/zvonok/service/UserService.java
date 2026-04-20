package com.zvonok.service;

import com.zvonok.controller.dto.MyUser;
import com.zvonok.exception.UserNotFoundException;
import com.zvonok.exception.UserWIthThisUsernameAlreadyExistException;
import com.zvonok.exception.UserWithThisEmailAlreadyExistException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.User;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.dto.CreateUserDto;
import com.zvonok.service.dto.UpdateUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing user entities and user-related operations. Сервис для управления сущностями
 * пользователей и операциями, связанными с пользователями.
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final S3Service s3Service;

	@Value("${s3.localEndpoint}")
	private String endpoint;

	public User getUser(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(
				HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public User getUser(String usernameOrEmail) {
		return userRepository.findByEmail(usernameOrEmail)
				.or(() -> userRepository.findByUsername(usernameOrEmail))
				.orElseThrow(() -> new UserNotFoundException(
						HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage()));
	}

	public MyUser getMyUser(String username) {
		User user = getUser(username);

		return myUserWrapper(user);
	}

	public Optional<User> getUserByUsernameOrEmail(String usernameOrEmail) {
		return userRepository.findByEmail(usernameOrEmail)
				.or(() -> userRepository.findByUsername(usernameOrEmail));
	}

	@Transactional
	public User createUser(CreateUserDto userDto) {

		userRepository.findByUsername(userDto.getUsername()).ifPresent(existing -> {
			throw new UserWIthThisUsernameAlreadyExistException(
					HttpResponseMessage.HTTP_USER_WITH_THIS_USERNAME_ALREADY_EXIST_RESPONSE_MESSAGE
							.getMessage());
		});

		userRepository.findByEmail(userDto.getEmail()).ifPresent(existing -> {
			throw new UserWithThisEmailAlreadyExistException(
					HttpResponseMessage.HTTP_USER_WITH_THIS_EMAIL_ALREADY_EXIST_RESPONSE_MESSAGE
							.getMessage());
		});

		User user = new User();
		user.setUsername(userDto.getUsername());
		user.setDisplayName(userDto.getUsername());
		user.setAvatarUrl(null);
		user.setEmail(userDto.getEmail());
		user.setPassword(userDto.getPassword());
		user.setAboutMe(null);

		return userRepository.save(user);
	}

	@Transactional
	public MyUser updateMyUser(String username, UpdateUserDto userDto) {

		User user = getUser(username);

		if (userDto.getDisplayName() != null && !userDto.getDisplayName().isEmpty()) {
			user.setDisplayName(userDto.getDisplayName());
		}

		if (userDto.getAvatarUrl() != null && !userDto.getAvatarUrl().isEmpty()) {
			user.setAvatarUrl(userDto.getAvatarUrl());
		}

		if (userDto.getAboutMe() != null && !userDto.getAboutMe().isEmpty()) {
			user.setAboutMe(userDto.getAboutMe());
		}

		User response = userRepository.save(user);

		return myUserWrapper(response);
	}

	public void uploadAvatar(String username, InputStream inputStream, long contentLength,
			String contentType, String extension) {
		User user = getUser(username);
		String timestamp = String.valueOf(System.currentTimeMillis());
		String avatarName = username + "_" + timestamp + extension;

		s3Service.uploadFile(avatarName, inputStream, contentLength, contentType);

		user.setAvatarUrl(endpoint + "/" + avatarName);
		userRepository.save(user);
	}

	public MyUser myUserWrapper(User user) {
		MyUser myUser = new MyUser();
		myUser.setId(user.getId());
		myUser.setUsername(user.getUsername());
		myUser.setDisplayName(user.getDisplayName());
		myUser.setAboutMe(user.getAboutMe());
		myUser.setAvatarUrl(user.getAvatarUrl());
		myUser.setEmail(user.getEmail());
		myUser.setIsEmailVerified(user.getIsEmailVerified());
		myUser.setStatus(user.getStatus());
		myUser.setAvatarUrl(user.getAvatarUrl());
		myUser.setLastSeenAt(user.getLastSeenAt());
		myUser.setUpdatedAt(user.getUpdatedAt());
		myUser.setCreatedAt(user.getCreatedAt());

		return myUser;
	}

	@Transactional
	public void deleteUser(Long id) {
		User user = getUser(id);
		userRepository.delete(user);
	}

	@Transactional
	public void updateLastSeenAt(Long userId, LocalDateTime lastSeenAt) {
		User user = getUser(userId);
		user.setLastSeenAt(lastSeenAt);
		userRepository.save(user);
	}

	@Transactional
	public void updateLastSeenAt(String username, LocalDateTime lastSeenAt) {
		User user = getUser(username);
		user.setLastSeenAt(lastSeenAt);
		userRepository.save(user);
	}

}
