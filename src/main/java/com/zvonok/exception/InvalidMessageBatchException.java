package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.BAD_REQUEST)
public class InvalidMessageBatchException extends RuntimeException {
    public InvalidMessageBatchException(String message) {
        super(message);
    }
}
