package com.zvonok.controller;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.service.dto.LiveKitTokenResponse;

@RestController
@RequestMapping("/livekit")
public class LiveKitController {
	/**
	 * @deprecated Use POST /api/calls/{callId}/token for call flows.
	 */
	@Deprecated
	@GetMapping("/token")
	public LiveKitTokenResponse getToken(@RequestParam("room") String roomName,
			Principal principal) {
		throw new InsufficientPermissionsException("Use the authorized call or conference token endpoint");
	}
}
