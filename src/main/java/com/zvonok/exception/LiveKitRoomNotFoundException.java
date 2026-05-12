package com.zvonok.exception;

import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.NOT_FOUND)
public class LiveKitRoomNotFoundException extends RuntimeException {
	public LiveKitRoomNotFoundException(String message) {
		super(message);
	}
}
