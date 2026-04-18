package com.minibank.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when trying to register with an existing email.
 */
public class EmailAlreadyExistsException extends UserServiceException {

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email, 
              HttpStatus.CONFLICT, 
              "USER_EMAIL_EXISTS");
    }
}
