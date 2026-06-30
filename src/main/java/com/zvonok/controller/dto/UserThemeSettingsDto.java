package com.zvonok.controller.dto;

import java.util.Map;

public record UserThemeSettingsDto(
		String selectedTheme,
		boolean customThemeEnabled,
		Map<String, String> customTheme
) {
}
