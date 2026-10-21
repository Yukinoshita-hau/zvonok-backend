package com.zvonok.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import com.zvonok.exception_handler.JsonErrorResponse;

@Order(0)
@RestControllerAdvice(assignableTypes = BrowserActivityController.class)
public class BrowserActivityErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<JsonErrorResponse> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(new JsonErrorResponse(error.getReason(), error.getStatusCode().value()));
    }
}
