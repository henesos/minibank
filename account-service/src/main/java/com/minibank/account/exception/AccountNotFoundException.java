package com.minibank.account.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when an account is not found.
 */
public class AccountNotFoundException extends AccountServiceException {

    /** Constructor with account ID. */
    public AccountNotFoundException(UUID id) {
        super("Account not found with id: " + id,
              HttpStatus.NOT_FOUND,
              "ACCOUNT_NOT_FOUND");
    }

    /** Constructor with account number. */
    public AccountNotFoundException(String accountNumber) {
        super("Account not found with account number: " + accountNumber,
              HttpStatus.NOT_FOUND,
              "ACCOUNT_NOT_FOUND");
    }
}
