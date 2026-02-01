package com.zvonok.exception;

import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.NOT_FOUND)
public class ServerBanNotFoundException extends RuntimeException {
    public ServerBanNotFoundException(String message) {
        super(message);
    }
}

