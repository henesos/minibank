package com.minibank.account.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exception thrown when there's insufficient balance for a withdrawal.
 * 
 * CRITICAL: This exception indicates a failed atomic balance update.
 */
public class InsufficientBalanceException extends AccountServiceException {

    public InsufficientBalanceException(UUID accountId, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient balance. Account: %s, Requested: %s, Available: %s", 
              accountId, requested, available), 
              HttpStatus.BAD_REQUEST, 
              "INSUFFICIENT_BALANCE");
    }

    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE");
    }
}
