package com.minibank.notification.exception;

/**
 * Exception thrown when a duplicate notification is detected.
 */
public class DuplicateNotificationException extends RuntimeException {

    public DuplicateNotificationException(String message) {
        super(message);
    }

    public DuplicateNotificationException(String idempotencyKey, java.util.UUID existingId) {
        super("Duplicate notification detected for idempotency key: " + idempotencyKey + 
              ". Existing notification ID: " + existingId);
    }
}
