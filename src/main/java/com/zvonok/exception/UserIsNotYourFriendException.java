package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.FORBIDDEN)
public class UserIsNotYourFriendException extends RuntimeException {
    public UserIsNotYourFriendException(String message) {
        super(message);
    }
}
