package com.minibank.transaction.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for Transaction Service.
 */
@Getter
public class TransactionServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public TransactionServiceException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public TransactionServiceException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
