package com.minibank.notification.service;

import com.minibank.notification.entity.Notification;

/**
 * Service for sending SMS notifications.
 *
 * In production, this would integrate with an SMS gateway (Twilio, AWS SNS, etc.)
 * For now, it simulates sending SMS messages.
 */
public interface SmsService {

    /**
     * Sends an SMS notification.
     *
     * @param notification the notification to send
     * @return true if sent successfully, false otherwise
     */
    boolean send(Notification notification);

    /**
     * Validates a phone number format.
     *
     * @param phoneNumber the phone number to validate
     * @return true if valid format
     */
    boolean isValidPhoneNumber(String phoneNumber);
}
