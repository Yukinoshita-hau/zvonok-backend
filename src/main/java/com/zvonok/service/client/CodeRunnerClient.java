package com.zvonok.service.client;

import com.zvonok.config.properties.CodeRunnerProperties;
import com.zvonok.exception.CodeRunnerUnavailableException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.service.dto.code.CodeRunRequestDto;
import com.zvonok.service.dto.code.CodeRunResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CodeRunnerClient {

	private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
	private static final String CODE_RUNS_PATH = "/internal/code-runs";

	private final RestClient.Builder restClientBuilder;
	private final CodeRunnerProperties properties;

	public CodeRunResponseDto runCode(CodeRunRequestDto request) {
		try {
			CodeRunResponseDto response = restClientBuilder.baseUrl(properties.getUrl()).build()
					.post()
					.uri(CODE_RUNS_PATH)
					.header(INTERNAL_API_KEY_HEADER, properties.getApiKey())
					.body(request)
					.retrieve()
					.body(CodeRunResponseDto.class);

			if (response == null) {
				throw new CodeRunnerUnavailableException(
						HttpResponseMessage.HTTP_CODE_RUNNER_UNAVAILABLE_RESPONSE_MESSAGE
								.getMessage());
			}
			return response;
		} catch (CodeRunnerUnavailableException e) {
			throw e;
		} catch (RestClientException e) {
			log.warn("Code runner request failed: {}", e.getClass().getSimpleName());
			throw new CodeRunnerUnavailableException(
					HttpResponseMessage.HTTP_CODE_RUNNER_UNAVAILABLE_RESPONSE_MESSAGE
							.getMessage());
		}
	}
}
