package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class CodeSessionAccessDeniedException extends RuntimeException {

	public CodeSessionAccessDeniedException(String message) {
		super(message);
	}
}
