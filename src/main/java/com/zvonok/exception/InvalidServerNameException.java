package com.zvonok.exception;

import org.springframework.http.HttpStatus;

import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class InvalidServerNameException extends RuntimeException {
    public InvalidServerNameException(String message) {
        super(message);
    }    
}