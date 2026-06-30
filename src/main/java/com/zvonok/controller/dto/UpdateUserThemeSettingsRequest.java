package com.zvonok.controller.dto;

import java.util.Map;

public record UpdateUserThemeSettingsRequest(
		String selectedTheme,
		Boolean customThemeEnabled,
		Map<String, String> customTheme
) {
}
