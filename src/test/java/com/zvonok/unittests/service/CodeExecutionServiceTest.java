package com.zvonok.unittests.service;

import com.zvonok.exception.UnsupportedExecutionLanguageException;
import com.zvonok.service.CodeExecutionService;
import com.zvonok.service.client.CodeRunnerClient;
import com.zvonok.service.dto.code.CodeRunRequestDto;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import com.zvonok.service.dto.code.ExecutionResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeExecutionServiceTest {

	@Mock
	private CodeRunnerClient codeRunnerClient;

	@InjectMocks
	private CodeExecutionService codeExecutionService;

	@Test
	void getAvailableLanguages_shouldReturnSupportedLanguages() {
		var languages = codeExecutionService.getAvailableLanguages();

		assertEquals(2, languages.size());
		assertEquals("java", languages.get(0).language());
		assertEquals("javascript", languages.get(1).language());
	}

	@Test
	void runCode_shouldNormalizeLanguageAndEmptyStdinBeforeCallingRunner() {
		CodeRunResponseDto runnerResponse = new CodeRunResponseDto(
				ExecutionResponseStatus.SUCCESS, "ok\n", 0, 10);
		when(codeRunnerClient.runCode(org.mockito.ArgumentMatchers.any()))
				.thenReturn(runnerResponse);

		CodeRunResponseDto response = codeExecutionService.runCode(
				new CodeRunRequestDto(" Java ", "class Main {}", null), "alice");

		ArgumentCaptor<CodeRunRequestDto> captor =
				ArgumentCaptor.forClass(CodeRunRequestDto.class);
		verify(codeRunnerClient).runCode(captor.capture());
		assertEquals(runnerResponse, response);
		assertEquals("java", captor.getValue().language());
		assertEquals("", captor.getValue().stdin());
	}

	@Test
	void runCode_shouldRejectUnsupportedLanguage() {
		assertThrows(UnsupportedExecutionLanguageException.class,
				() -> codeExecutionService.runCode(
						new CodeRunRequestDto("cpp", "int main() {}", ""), "alice"));
	}
}
