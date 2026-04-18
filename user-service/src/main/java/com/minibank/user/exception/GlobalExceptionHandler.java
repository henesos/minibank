package com.minibank.user.exception;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for User Service.
 * 
 * Catches all exceptions and converts them to consistent API error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle UserServiceException and its subclasses.
     */
    @ExceptionHandler(UserServiceException.class)
    public ResponseEntity<ErrorResponse> handleUserServiceException(UserServiceException ex) {
        log.warn("UserServiceException: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(ex.getStatus().value())
                        .error(ex.getStatus().getReasonPhrase())
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .build());
    }

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Validation Failed")
                        .errorCode("VALIDATION_ERROR")
                        .message("Input validation failed")
                        .errors(errors)
                        .build());
    }

    /**
     * Handle Spring Security BadCredentialsException.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error("Unauthorized")
                        .errorCode("INVALID_CREDENTIALS")
                        .message("Invalid email or password")
                        .build());
    }

    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("Internal Server Error")
                        .errorCode("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build());
    }

    /**
     * Standard error response structure.
     */
    @Data
    @Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String errorCode;
        private String message;
        private Map<String, String> errors;
    }
}
