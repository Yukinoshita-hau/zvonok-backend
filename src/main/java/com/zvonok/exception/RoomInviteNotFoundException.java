package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.NOT_FOUND)
public class RoomInviteNotFoundException extends RuntimeException {

	public RoomInviteNotFoundException(String message) {
		super(message);
	}
}
