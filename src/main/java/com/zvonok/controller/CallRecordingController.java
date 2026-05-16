package com.zvonok.controller;

import java.security.Principal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.model.CallRecording;
import com.zvonok.service.CallRecordingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/calls/{callId}/recording")
@RequiredArgsConstructor
public class CallRecordingController {
	
	private final CallRecordingService callRecordingService;
	
	@PostMapping("/start")
	public CallRecording start(@PathVariable Long callId, Principal principal) {
		return callRecordingService.startRecording(callId, principal.getName());
	}

	@PostMapping("/stop")
	public CallRecording stop(@PathVariable Long callId, Principal principal) {
		return callRecordingService.stopRecording(callId, principal.getName());
	}
}
