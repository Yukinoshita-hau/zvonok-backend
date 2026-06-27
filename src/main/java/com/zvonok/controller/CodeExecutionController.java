package com.zvonok.controller;

import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CodeExecutionService;
import com.zvonok.service.dto.code.AvailableLanguageDto;
import com.zvonok.service.dto.code.CodeRunRequestDto;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/code-runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class CodeExecutionController {

	private final CodeExecutionService codeExecutionService;

	@GetMapping("/languages")
	public List<AvailableLanguageDto> getAvailableLanguages() {
		return codeExecutionService.getAvailableLanguages();
	}

	@PostMapping
	public ResponseEntity<CodeRunResponseDto> runCode(@Valid @RequestBody CodeRunRequestDto request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(codeExecutionService.runCode(request, principal.getName()));
	}
}
