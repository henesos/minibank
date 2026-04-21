package com.minibank.notification.service;

import com.minibank.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Mock implementation of EmailService.
 * 
 * Simulates sending emails for development and testing purposes.
 * In production, replace with actual email provider integration.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.email.from:noreply@minibank.com}")
    private String defaultFrom;

    @Value("${notification.email.mock:true}")
    private boolean mockMode;

    @Override
    public boolean send(Notification notification) {
        if (!emailEnabled) {
            log.info("Email notifications are disabled. Skipping notification: {}", notification.getId());
            return true;
        }

        String recipient = notification.getRecipient();
        
        // If no recipient specified, simulate sending
        if (recipient == null || recipient.isEmpty()) {
            recipient = "user-" + notification.getUserId() + "@minibank.mock";
        }

        if (!isValidEmail(recipient)) {
            log.error("Invalid email address: {} for notification: {}", recipient, notification.getId());
            return false;
        }

        if (mockMode) {
            return sendMock(notification, recipient);
        }

        // In production, integrate with actual email provider here
        // For now, just log
        log.info("Would send email to: {} with subject: {}", recipient, notification.getSubject());
        return true;
    }

    /**
     * Simulates sending an email.
     */
    private boolean sendMock(Notification notification, String recipient) {
        log.info("=== MOCK EMAIL ===");
        log.info("To: {}", recipient);
        log.info("From: {}", defaultFrom);
        log.info("Subject: {}", notification.getSubject());
        log.info("Content: {}", notification.getContent());
        log.info("Notification ID: {}", notification.getId());
        log.info("==================");
        
        // Simulate potential failure (1% chance)
        if (Math.random() < 0.01) {
            log.warn("Simulated email failure for notification: {}", notification.getId());
            return false;
        }

        return true;
    }

    @Override
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
