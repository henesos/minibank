package com.minibank.notification.kafka;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer for transaction events.
 *
 * Listens to transaction events from transaction-service and
 * creates notifications for affected users (both sender and receiver).
 *
 * Idempotency is handled via Redis SET NX EX instead of an in-memory
 * ConcurrentHashMap so that duplicate detection survives restarts and
 * works correctly in a multi-instance deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    /** Redis key prefix for processed-event tracking */
    private static final String PROCESSED_KEY_PREFIX = "notification:processed:";

    /** TTL for idempotency keys — 24 hours */
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

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
            // Idempotency check via Redis SET NX EX
            String eventId = event.getEventId().toString();
            String redisKey = PROCESSED_KEY_PREFIX + eventId;

            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);

            if (wasSet == null || !wasSet) {
                log.warn("Duplicate event detected (Redis idempotency), skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Process the event based on type
            processEvent(event);

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
     * Processes a transaction event and creates appropriate notifications
     * for both the sender (fromUserId) and the receiver (toUserId).
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
     * Creates notification for completed transactions — both sender and receiver.
     */
    private void createCompletionNotification(TransactionEvent event) {
        log.info("Creating completion notification for transaction: {}", event.getSagaId());

        // Notify sender (fromUserId)
        NotificationResponse senderResponse = notificationService.createFromTransactionEvent(event, true);
        log.info("Created sender notification: {} for completed transaction", senderResponse.getId());
        notificationService.sendNotification(senderResponse.getId());

        // Notify receiver (toUserId) — previously missing
        if (event.getToUserId() != null) {
            NotificationResponse receiverResponse = notificationService.createFromTransactionEvent(event, false);
            log.info("Created receiver notification: {} for completed transaction", receiverResponse.getId());
            notificationService.sendNotification(receiverResponse.getId());
        }
    }

    /**
     * Creates notification for failed transactions — sender only.
     */
    private void createFailureNotification(TransactionEvent event) {
        log.info("Creating failure notification for transaction: {}", event.getSagaId());

        NotificationResponse response = notificationService.createFromTransactionEvent(event, true);
        log.info("Created notification: {} for failed transaction", response.getId());

        notificationService.sendNotification(response.getId());
    }

    /**
     * Creates notification for compensated (reversed) transactions — sender only.
     */
    private void createCompensationNotification(TransactionEvent event) {
        log.info("Creating compensation notification for transaction: {}", event.getSagaId());

        NotificationResponse response = notificationService.createFromTransactionEvent(event, true);
        log.info("Created notification: {} for compensated transaction", response.getId());

        notificationService.sendNotification(response.getId());
    }

    /**
     * Creates notification for initiated transactions — sender only.
     */
    private void createInitiatedNotification(TransactionEvent event) {
        log.info("Creating initiated notification for transaction: {}", event.getSagaId());

        NotificationResponse response = notificationService.createFromTransactionEvent(event, true);
        log.info("Created notification: {} for initiated transaction", response.getId());

        notificationService.sendNotification(response.getId());
    }
}
