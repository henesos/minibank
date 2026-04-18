package com.minibank.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends UserServiceException {

    public InvalidCredentialsException() {
        super("Invalid email or password", 
              HttpStatus.UNAUTHORIZED, 
              "INVALID_CREDENTIALS");
    }

    public InvalidCredentialsException(String message) {
        super(message, 
              HttpStatus.UNAUTHORIZED, 
              "INVALID_CREDENTIALS");
    }
}
