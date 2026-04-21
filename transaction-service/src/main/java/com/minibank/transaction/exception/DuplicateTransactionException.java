package com.minibank.transaction.exception;

/**
 * Exception thrown when a duplicate transaction is detected via idempotency key.
 */
public class DuplicateTransactionException extends TransactionServiceException {

    public DuplicateTransactionException(String idempotencyKey) {
        super("Duplicate transaction detected for idempotency key: " + idempotencyKey, 
              org.springframework.http.HttpStatus.CONFLICT, 
              "DUPLICATE_TRANSACTION");
    }
}
