package com.minibank.user.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for User Service.
 * 
 * All custom exceptions in the user service should extend this class.
 * Provides consistent error handling with HTTP status and error code.
 */
@Getter
public class UserServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public UserServiceException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public UserServiceException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
