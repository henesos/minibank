package com.minibank.notification.exception;

/**
 * Base exception for notification service errors.
 */
public class NotificationServiceException extends RuntimeException {

    public NotificationServiceException(String message) {
        super(message);
    }

    public NotificationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
