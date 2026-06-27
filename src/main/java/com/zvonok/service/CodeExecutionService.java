package com.zvonok.service;

import com.zvonok.exception.UnsupportedExecutionLanguageException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.service.client.CodeRunnerClient;
import com.zvonok.service.dto.code.AvailableLanguageDto;
import com.zvonok.service.dto.code.CodeRunRequestDto;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeExecutionService {

	private static final List<AvailableLanguageDto> AVAILABLE_LANGUAGES = List.of(
			new AvailableLanguageDto("java", "Java"),
			new AvailableLanguageDto("javascript", "JavaScript")
	);
	private static final Set<String> AVAILABLE_LANGUAGE_KEYS = Set.of("java", "javascript");

	private final CodeRunnerClient codeRunnerClient;

	public List<AvailableLanguageDto> getAvailableLanguages() {
		return AVAILABLE_LANGUAGES;
	}

	public CodeRunResponseDto runCode(CodeRunRequestDto request, String username) {
		String language = normalizeLanguage(request.language());
		CodeRunRequestDto runnerRequest = new CodeRunRequestDto(language, request.code(),
				request.stdin() == null ? "" : request.stdin());

		CodeRunResponseDto response = codeRunnerClient.runCode(runnerRequest);
		log.info("Code run completed: username={}, language={}, status={}, executionTimeMs={}",
				username, language, response.status(), response.executionTimeMs());
		return response;
	}

	public String normalizeLanguage(String language) {
		String normalizedLanguage = language.trim().toLowerCase();
		if (!AVAILABLE_LANGUAGE_KEYS.contains(normalizedLanguage)) {
			throw new UnsupportedExecutionLanguageException(
					HttpResponseMessage.HTTP_UNSUPPORTED_EXECUTION_LANGUAGE_RESPONSE_MESSAGE
							.getMessage());
		}
		return normalizedLanguage;
	}
}
