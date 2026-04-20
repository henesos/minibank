package com.minibank.notification.service;

import com.minibank.notification.entity.Notification;

/**
 * Service for sending email notifications.
 *
 * In production, this would integrate with an email provider (SendGrid, AWS SES, etc.)
 * For now, it simulates sending emails.
 */
public interface EmailService {

    /**
     * Sends an email notification.
     *
     * @param notification the notification to send
     * @return true if sent successfully, false otherwise
     */
    boolean send(Notification notification);

    /**
     * Validates an email address format.
     *
     * @param email the email to validate
     * @return true if valid format
     */
    boolean isValidEmail(String email);
}
