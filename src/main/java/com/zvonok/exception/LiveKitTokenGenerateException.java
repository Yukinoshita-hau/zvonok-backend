package com.zvonok.exception;

import com.zvonok.exception_handler.annotation.ApiException;
import org.springframework.http.HttpStatus;

@ApiException(status = HttpStatus.INTERNAL_SERVER_ERROR)
public class LiveKitTokenGenerateException extends RuntimeException {
    public LiveKitTokenGenerateException(String message) {
        super(message);
    }
}
