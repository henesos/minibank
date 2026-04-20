package com.minibank.notification.exception;

/**
 * Base exception for notification service errors.
 */
public class NotificationServiceException extends RuntimeException {

    /**
     * Constructs a NotificationServiceException with a message.
     *
     * @param message the detail message
     */
    public NotificationServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a NotificationServiceException with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public NotificationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
