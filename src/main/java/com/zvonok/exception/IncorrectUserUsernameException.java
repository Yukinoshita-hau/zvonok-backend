package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class IncorrectUserUsernameException extends RuntimeException {
    public IncorrectUserUsernameException(String message) {
        super(message);
    }
}

