package com.minibank.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when trying to login with a locked account.
 */
public class AccountLockedException extends UserServiceException {

    public AccountLockedException() {
        super("Account is locked due to multiple failed login attempts", 
              HttpStatus.LOCKED, 
              "ACCOUNT_LOCKED");
    }

    public AccountLockedException(String message) {
        super(message, 
              HttpStatus.LOCKED, 
              "ACCOUNT_LOCKED");
    }
}
