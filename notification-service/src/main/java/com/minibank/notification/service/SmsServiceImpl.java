package com.minibank.notification.service;

import com.minibank.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Mock implementation of SmsService.
 * 
 * Simulates sending SMS messages for development and testing purposes.
 * In production, replace with actual SMS gateway integration.
 */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    // Turkish phone number pattern: +90XXXXXXXXXX or 05XXXXXXXXX
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(\\+90|0)?[5][0-9]{9}$"
    );

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.mock:true}")
    private boolean mockMode;

    @Override
    public boolean send(Notification notification) {
        if (!smsEnabled) {
            log.info("SMS notifications are disabled. Skipping notification: {}", notification.getId());
            return true;
        }

        String recipient = notification.getRecipient();
        
        if (recipient == null || recipient.isEmpty()) {
            log.warn("No phone number provided for SMS notification: {}", notification.getId());
            return false;
        }

        if (!isValidPhoneNumber(recipient)) {
            log.error("Invalid phone number: {} for notification: {}", recipient, notification.getId());
            return false;
        }

        if (mockMode) {
            return sendMock(notification, recipient);
        }

        // In production, integrate with actual SMS gateway here
        log.info("Would send SMS to: {} with content: {}", recipient, 
                truncateContent(notification.getContent()));
        return true;
    }

    /**
     * Simulates sending an SMS.
     */
    private boolean sendMock(Notification notification, String recipient) {
        log.info("=== MOCK SMS ===");
        log.info("To: {}", recipient);
        log.info("Content: {}", truncateContent(notification.getContent()));
        log.info("Notification ID: {}", notification.getId());
        log.info("================");
        
        return true;
    }

    /**
     * Truncates content for SMS (max 160 chars).
     */
    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 160 ? content.substring(0, 157) + "..." : content;
    }

    @Override
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // Remove spaces and dashes
        String cleanNumber = phoneNumber.replaceAll("[\\s-]", "");
        return PHONE_PATTERN.matcher(cleanNumber).matches();
    }
}
