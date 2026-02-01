package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.CONFLICT)
public class ServerMemberAlreadyWasKicked extends RuntimeException {
    public ServerMemberAlreadyWasKicked(String message) {
        super(message);
    }
}


