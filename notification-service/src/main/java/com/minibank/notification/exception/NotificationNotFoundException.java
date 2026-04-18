package com.minibank.notification.exception;

/**
 * Exception thrown when a notification is not found.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String message) {
        super(message);
    }

    public NotificationNotFoundException(java.util.UUID notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}
