package com.minibank.notification.exception;

/**
 * Exception thrown when a notification is not found.
 */
public class NotificationNotFoundException extends RuntimeException {

    /**
     * Constructs a NotificationNotFoundException with a message.
     *
     * @param message the detail message
     */
    public NotificationNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a NotificationNotFoundException with a notification ID.
     *
     * @param notificationId the ID of the notification that was not found
     */
    public NotificationNotFoundException(java.util.UUID notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}
