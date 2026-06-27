package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.SERVICE_UNAVAILABLE)
public class CodeRunnerUnavailableException extends RuntimeException {

	public CodeRunnerUnavailableException(String message) {
		super(message);
	}
}
