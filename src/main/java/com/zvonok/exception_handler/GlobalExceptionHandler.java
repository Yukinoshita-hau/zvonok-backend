package com.zvonok.exception_handler;

import com.zvonok.exception.InvalidBooleanFormatException;
import com.zvonok.exception_handler.annotation.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final Map<Class<? extends RuntimeException>, HttpStatus> exceptionStatusMap = new HashMap<>();

    public GlobalExceptionHandler() {
        long startTime = System.currentTimeMillis();
        Reflections reflections = new Reflections("com.zvonok.exception");
        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(ApiException.class);

        for (Class<?> clazz: annotated) {
            if (RuntimeException.class.isAssignableFrom(clazz)) {
                ApiException annotation = clazz.getAnnotation(ApiException.class);
                exceptionStatusMap.put((Class<? extends RuntimeException>) clazz, annotation.status());
                log.debug("✅ Registered exception: {} -> {}", clazz.getSimpleName(), annotation.status());
            } else {
                log.warn("⚠️ Class {} has @ApiException but is not RuntimeException",
                        clazz.getName());
            }
        }
        long duration = System.currentTimeMillis() - startTime;
        log.info("📦 Exception registry initialized: {} exceptions in {}ms", exceptionStatusMap.size(), duration);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<JsonErrorResponse> handleException(RuntimeException e) {
        HttpStatus status = exceptionStatusMap.get(e.getClass());

        if (status != null) {
            log.debug("Handled {} with status {}", e.getClass().getSimpleName(), status);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("Unregistered exception: {}", e.getClass().getName(), e);
        }

        JsonErrorResponse errorResponse = new JsonErrorResponse(
                e.getMessage(),
                status.value()
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<JsonErrorResponse> handleValidationErrors(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String fieldName = ((FieldError) error).getField();
                    String message = error.getDefaultMessage();
                    return fieldName + ": " + message;
                })
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("❌ Validation error: {}", errorMessage);

        JsonErrorResponse errorResponse = new JsonErrorResponse(
                errorMessage,
                HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable rootCause = ex.getRootCause();
        if (rootCause instanceof InvalidBooleanFormatException) {
            JsonErrorResponse errorResponse = new JsonErrorResponse(rootCause.getMessage(), HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new JsonErrorResponse("Invalid request format", 400));
    }
}