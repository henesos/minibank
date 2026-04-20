package com.minibank.account.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for Account Service.
 */
@Getter
public class AccountServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    /** Constructor. */
    public AccountServiceException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /** Constructor with cause. */
    public AccountServiceException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
