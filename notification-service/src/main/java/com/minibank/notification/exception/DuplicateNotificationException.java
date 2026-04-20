package com.minibank.notification.exception;

/**
 * Exception thrown when a duplicate notification is detected.
 */
public class DuplicateNotificationException extends RuntimeException {

    /**
     * Constructs a DuplicateNotificationException with a message.
     *
     * @param message the detail message
     */
    public DuplicateNotificationException(String message) {
        super(message);
    }

    /**
     * Constructs a DuplicateNotificationException with idempotency key and existing notification ID.
     *
     * @param idempotencyKey the idempotency key that caused the conflict
     * @param existingId     the ID of the existing notification
     */
    public DuplicateNotificationException(String idempotencyKey, java.util.UUID existingId) {
        super("Duplicate notification detected for idempotency key: " + idempotencyKey +
              ". Existing notification ID: " + existingId);
    }
}
