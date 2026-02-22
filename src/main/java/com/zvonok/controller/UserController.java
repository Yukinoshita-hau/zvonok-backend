package com.zvonok.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	
	@GetMapping("/{userId}")
	public User getUser(@PathVariable Long userId) {
		User resultUser =  userService.getUser(userId);	
		resultUser.setEmail(null);
		return resultUser;
	}

	@GetMapping("/my")
	public User getMyUser(@AuthenticationPrincipal UserPrincipal principal) {
		return userService.getUser(principal.getName());
	}
}
