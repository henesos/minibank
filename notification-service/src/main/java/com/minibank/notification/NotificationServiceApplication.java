package com.minibank.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MiniBank Notification Service Application
 * 
 * Handles notifications for MiniBank users.
 * Consumes transaction events from Kafka and sends notifications.
 * Runs on port 8084 by default.
 * 
 * Key Features:
 * - Multi-channel notifications (Email, SMS, Push, In-App)
 * - Kafka integration for event-driven notifications
 * - Idempotency support for duplicate prevention
 * - Retry mechanism for failed notifications
 * 
 * @author MiniBank Team
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
