package com.minibank.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when trying to login with a locked account.
 */
public class AccountLockedException extends UserServiceException {

    /** Constructor. */
    public AccountLockedException() {
        super("Account is locked due to multiple failed login attempts",
              HttpStatus.LOCKED,
              "ACCOUNT_LOCKED");
    }

    /** Constructor with custom message. */
    public AccountLockedException(String message) {
        super(message,
              HttpStatus.LOCKED,
              "ACCOUNT_LOCKED");
    }
}
