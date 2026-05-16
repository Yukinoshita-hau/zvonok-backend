package com.zvonok.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zvonok.controller.dto.RestoreCallSessionResponse;
import com.zvonok.service.CallSessionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {
	
	private final CallSessionService callSessionService;
}
