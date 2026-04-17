package com.zvonok.exception;
import org.springframework.http.HttpStatus;
import com.zvonok.exception_handler.annotation.ApiException;

@ApiException(status = HttpStatus.NOT_FOUND)
public class FileStorageNotFoundException extends RuntimeException {
    public FileStorageNotFoundException(String message) {
        super(message);
    }
}
