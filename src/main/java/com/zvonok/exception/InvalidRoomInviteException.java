package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class InvalidRoomInviteException extends RuntimeException {

	public InvalidRoomInviteException(String message) {
		super(message);
	}
}
