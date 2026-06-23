package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.CONFLICT)
public class ConferenceEndException extends RuntimeException {
    public ConferenceEndException(String message) {
        super(message);
    }
}
