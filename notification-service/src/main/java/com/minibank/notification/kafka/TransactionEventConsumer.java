package com.minibank.notification.kafka;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka consumer for transaction events.
 * 
 * Listens to transaction events from transaction-service and
 * creates notifications for affected users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;

    /**
     * Track processed event IDs for idempotency.
     * In production, this should use Redis or database.
     */
    private final ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>();

    /**
     * Consumes transaction events and creates notifications.
     * 
     * @param event the transaction event
     * @param acknowledgment manual acknowledgment for at-least-once delivery
     */
    @KafkaListener(
            topics = "${kafka.topics.transaction-events:transaction-events}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionEvent(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        log.info("Received transaction event: {}, type: {}", 
                event.getEventId(), event.getEventType());

        try {
            // Check for duplicate processing
            String eventId = event.getEventId().toString();
            if (processedEvents.containsKey(eventId)) {
                log.warn("Duplicate event detected, skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Process the event based on type
            processEvent(event);

            // Mark as processed
            processedEvents.put(eventId, Boolean.TRUE);

            log.info("Successfully processed transaction event: {}", eventId);

        } catch (Exception e) {
            log.error("Error processing transaction event {}: {}", 
                    event.getEventId(), e.getMessage(), e);
            // In production, send to dead-letter queue
        } finally {
            acknowledgment.acknowledge();
        }
    }

    /**
     * Processes a transaction event and creates appropriate notifications.
     */
    private void processEvent(TransactionEvent event) {
        switch (event.getEventType()) {
            case TRANSACTION_COMPLETED:
                createCompletionNotification(event);
                break;
            case TRANSACTION_FAILED:
                createFailureNotification(event);
                break;
            case COMPENSATION_COMPLETED:
                createCompensationNotification(event);
                break;
            case TRANSACTION_INITIATED:
                createInitiatedNotification(event);
                break;
            default:
                log.debug("No notification needed for event type: {}", event.getEventType());
        }
    }

    /**
     * Creates notification for completed transactions.
     */
    private void createCompletionNotification(TransactionEvent event) {
        log.info("Creating completion notification for transaction: {}", event.getSagaId());
        
        NotificationResponse response = notificationService.createFromTransactionEvent(event);
        log.info("Created notification: {} for completed transaction", response.getId());
        
        // Immediately attempt to send
        notificationService.sendNotification(response.getId());
    }

    /**
     * Creates notification for failed transactions.
     */
    private void createFailureNotification(TransactionEvent event) {
        log.info("Creating failure notification for transaction: {}", event.getSagaId());
        
        NotificationResponse response = notificationService.createFromTransactionEvent(event);
        log.info("Created notification: {} for failed transaction", response.getId());
        
        notificationService.sendNotification(response.getId());
    }

    /**
     * Creates notification for compensated (reversed) transactions.
     */
    private void createCompensationNotification(TransactionEvent event) {
        log.info("Creating compensation notification for transaction: {}", event.getSagaId());
        
        NotificationResponse response = notificationService.createFromTransactionEvent(event);
        log.info("Created notification: {} for compensated transaction", response.getId());
        
        notificationService.sendNotification(response.getId());
    }

    /**
     * Creates notification for initiated transactions.
     */
    private void createInitiatedNotification(TransactionEvent event) {
        log.info("Creating initiated notification for transaction: {}", event.getSagaId());
        
        NotificationResponse response = notificationService.createFromTransactionEvent(event);
        log.info("Created notification: {} for initiated transaction", response.getId());
        
        notificationService.sendNotification(response.getId());
    }

    /**
     * Clears processed events cache (for testing).
     */
    public void clearProcessedEvents() {
        processedEvents.clear();
    }
}
