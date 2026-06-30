package com.zvonok.controller;

import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.zvonok.controller.dto.MyUser;
import com.zvonok.controller.dto.UpdateUserThemeSettingsRequest;
import com.zvonok.controller.dto.UserMiniProfileDto;
import com.zvonok.controller.dto.UserThemeSettingsDto;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.UserSettingsService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.UpdateUserDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final UserSettingsService userSettingsService;

	@GetMapping("/{userId}")
	public User getUser(@PathVariable Long userId) {
		User resultUser = userService.getUser(userId);
		return resultUser;
	}

	@GetMapping("/{userId}/mini-profile")
	public UserMiniProfileDto getMiniProfile(@PathVariable Long userId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return userService.getMiniProfile(userId, principal.getUsername());
	}

	@GetMapping("/my")
	public MyUser getMyUser(@AuthenticationPrincipal UserPrincipal principal) {
		return userService.getMyUser(principal.getUsername());
	}

	@PutMapping("/my")
	public MyUser updateMyUser(@AuthenticationPrincipal UserPrincipal principal,
			@RequestBody UpdateUserDto dto) {
		return userService.updateMyUser(principal.getUsername(), dto);
	}

	@GetMapping("/me/settings")
	public UserThemeSettingsDto getSettings(@AuthenticationPrincipal UserPrincipal principal) {
		return userSettingsService.getSettings(principal.getUsername());
	}

	@PatchMapping("/me/settings/theme")
	public UserThemeSettingsDto updateThemeSettings(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestBody UpdateUserThemeSettingsRequest request) {
		return userSettingsService.updateTheme(principal.getUsername(), request);
	}

	@PostMapping("/upload-avatar")
	public ResponseEntity<Void> updateAvatar(@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam MultipartFile file) throws IOException {
		String originalFilename = file.getOriginalFilename();
		String extension = StringUtils.getFilenameExtension(originalFilename);

		if (extension != null && !extension.isBlank()) {
			extension = "." + extension;
		} else {
			extension = "";
		}

		userService.uploadAvatar(
			principal.getUsername(), 
			file.getInputStream(), 
			file.getSize(),
			file.getContentType(), 
			extension);

		return ResponseEntity.ok().build();
	}
}
