package com.zvonok.exception;

import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.FORBIDDEN)
public class UserBannedException extends RuntimeException {
    public UserBannedException(String message) {
        super(message);
    }
}

