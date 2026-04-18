package com.minibank.account.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when trying to perform operations on an inactive account.
 */
public class InactiveAccountException extends AccountServiceException {

    public InactiveAccountException(UUID accountId) {
        super("Account is not active: " + accountId, 
              HttpStatus.FORBIDDEN, 
              "ACCOUNT_INACTIVE");
    }

    public InactiveAccountException(UUID accountId, String status) {
        super("Account is not active. Status: " + status + ", Account: " + accountId, 
              HttpStatus.FORBIDDEN, 
              "ACCOUNT_INACTIVE");
    }
}
