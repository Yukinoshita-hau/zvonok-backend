package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class CanvasBoardNotFoundException extends RuntimeException {
	public CanvasBoardNotFoundException(String message) {
		super(message);
	}
}
