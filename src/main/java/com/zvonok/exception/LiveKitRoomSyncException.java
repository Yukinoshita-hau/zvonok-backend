package com.zvonok.exception;

import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.CONFLICT)
public class LiveKitRoomSyncException extends RuntimeException {
    public LiveKitRoomSyncException(String message) {
        super(message);
    }
}
