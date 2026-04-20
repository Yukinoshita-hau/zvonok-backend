package com.zvonok.controller;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.model.User;
import com.zvonok.service.LiveKitTokenService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.LiveKitTokenResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/livekit")
@RequiredArgsConstructor
public class LiveKitController {

	private final LiveKitTokenService liveKitTokenService;
	private final UserService userService;

	@GetMapping("/token")
	public LiveKitTokenResponse getToken(@RequestParam("room") String roomName,
			Principal principal) {
		String identity = principal.getName();
		User user = userService.getUser(identity);
		return liveKitTokenService.generateToken(roomName, identity, user.getDisplayName());
	}
}
