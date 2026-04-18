package com.minibank.user.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a user is not found.
 */
public class UserNotFoundException extends UserServiceException {

    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id, 
              HttpStatus.NOT_FOUND, 
              "USER_NOT_FOUND");
    }

    public UserNotFoundException(String email) {
        super("User not found with email: " + email, 
              HttpStatus.NOT_FOUND, 
              "USER_NOT_FOUND");
    }
}
