package com.zvonok.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvonok.controller.dto.UpdateUserThemeSettingsRequest;
import com.zvonok.controller.dto.UserThemeSettingsDto;
import com.zvonok.exception.InvalidUserSettingsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.User;
import com.zvonok.model.UserSettings;
import com.zvonok.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

	private static final Pattern COLOR_PATTERN =
			Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$");

	private final UserService userService;
	private final UserSettingsRepository userSettingsRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public UserThemeSettingsDto getSettings(String username) {
		User user = userService.getUser(username);
		return toDto(getOrCreateSettings(user));
	}

	@Transactional
	public UserThemeSettingsDto updateTheme(String username,
			UpdateUserThemeSettingsRequest request) {
		User user = userService.getUser(username);
		UserSettings settings = getOrCreateSettings(user);
		if (request.selectedTheme() != null && !request.selectedTheme().isBlank()) {
			settings.setSelectedTheme(request.selectedTheme().trim());
		}
		if (request.customThemeEnabled() != null) {
			settings.setCustomThemeEnabled(request.customThemeEnabled());
		}
		if (request.customTheme() != null) {
			validateCustomTheme(request.customTheme());
			settings.setCustomThemeJson(writeTheme(request.customTheme()));
		}
		return toDto(userSettingsRepository.save(settings));
	}

	private UserSettings getOrCreateSettings(User user) {
		return userSettingsRepository.findByUserId(user.getId()).orElseGet(() -> {
			UserSettings settings = new UserSettings();
			settings.setUser(user);
			return userSettingsRepository.save(settings);
		});
	}

	private void validateCustomTheme(Map<String, String> customTheme) {
		for (Map.Entry<String, String> entry : customTheme.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()
					|| entry.getValue() == null
					|| !COLOR_PATTERN.matcher(entry.getValue()).matches()) {
				throw new InvalidUserSettingsException(
						HttpResponseMessage.HTTP_USER_THEME_INVALID_RESPONSE_MESSAGE.getMessage());
			}
		}
	}

	private UserThemeSettingsDto toDto(UserSettings settings) {
		return new UserThemeSettingsDto(settings.getSelectedTheme(),
				settings.isCustomThemeEnabled(), readTheme(settings.getCustomThemeJson()));
	}

	private Map<String, String> readTheme(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		} catch (Exception e) {
			return Map.of();
		}
	}

	private String writeTheme(Map<String, String> theme) {
		try {
			return objectMapper.writeValueAsString(theme);
		} catch (JsonProcessingException e) {
			throw new InvalidUserSettingsException(
					HttpResponseMessage.HTTP_USER_THEME_INVALID_RESPONSE_MESSAGE.getMessage());
		}
	}
}
