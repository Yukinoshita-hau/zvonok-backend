package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.UNAUTHORIZED)
public class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String message) {
        super(message);
    }
}
