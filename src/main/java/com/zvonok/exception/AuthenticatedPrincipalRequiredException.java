package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.UNAUTHORIZED)
public class AuthenticatedPrincipalRequiredException extends RuntimeException {
    public AuthenticatedPrincipalRequiredException(String message) {
        super(message);
    }
}