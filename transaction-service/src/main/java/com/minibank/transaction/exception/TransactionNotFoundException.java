package com.minibank.transaction.exception;

import java.util.UUID;

/**
 * Exception thrown when a transaction is not found.
 */
public class TransactionNotFoundException extends TransactionServiceException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found: " + transactionId, 
              org.springframework.http.HttpStatus.NOT_FOUND, 
              "TRANSACTION_NOT_FOUND");
    }

    public TransactionNotFoundException(String identifier) {
        super("Transaction not found: " + identifier, 
              org.springframework.http.HttpStatus.NOT_FOUND, 
              "TRANSACTION_NOT_FOUND");
    }
}
