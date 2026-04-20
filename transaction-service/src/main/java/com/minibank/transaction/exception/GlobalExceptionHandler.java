package com.minibank.transaction.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for Transaction Service.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Handles TransactionServiceException. */
    @ExceptionHandler(TransactionServiceException.class)
    public ResponseEntity<ErrorResponse> handleTransactionServiceException(TransactionServiceException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    /** Handles validation exceptions. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .errorCode("VALIDATION_ERROR")
                .message("Invalid request parameters")
                .details(errors)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /** Handles generic exceptions. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.internalServerError().body(error);
    }

    /** Error response DTO. */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String errorCode;
        private String message;
        private Map<String, String> details;
    }
}
