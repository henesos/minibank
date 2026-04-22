package com.minibank.notification.kafka;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.repository.NotificationRepository;
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

import java.time.Duration;

/**
 * Kafka consumer for transaction events.
 *
 * Listens to transaction events from transaction-service and
 * creates notifications for affected users (both sender and receiver).
 *
 * <p><b>Idempotency strategy (ADR-013):</b></p>
 * <ol>
 *   <li>Redis SET NX EX — atomic check-and-set with 24h TTL</li>
 *   <li>Redis miss → DB double-check via idempotencyKey (fallback if Redis lost data)</li>
 *   <li>Exception → Redis key cleanup (rollback so the event can be retried)</li>
 * </ol>
 *
 * <p>This three-layer strategy ensures:</p>
 * <ul>
 *   <li>No duplicate notifications across restarts (DB persists)</li>
 *   <li>No duplicate notifications in multi-instance deployment (Redis is shared)</li>
 *   <li>No memory leak (Redis TTL auto-cleanup, unlike ConcurrentHashMap)</li>
 *   <li>At-least-once processing guarantee (Redis key rollback on failure)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /** Redis key prefix for processed-event tracking */
    private static final String PROCESSED_KEY_PREFIX = "notification:processed:";

    /** TTL for idempotency keys — 24 hours */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /**
     * Consumes transaction events and creates notifications.
     *
     * <p>Idempotency flow:</p>
     * <ol>
     *   <li>Redis SET NX EX with 24h TTL — prevents duplicate processing</li>
     *   <li>If Redis hit (key exists) → skip, log warning, acknowledge</li>
     *   <li>If Redis miss → DB double-check via idempotencyKey</li>
     *   <li>If DB hit → skip (event already processed before Redis was set)</li>
     *   <li>If DB miss → process event, create notifications</li>
     *   <li>If exception → rollback Redis key (delete) so event can be retried</li>
     * </ol>
     *
     * @param event          the transaction event
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

        String eventId = event.getEventId().toString();
        String redisKey = PROCESSED_KEY_PREFIX + eventId;

        log.info("Received transaction event: {}, type: {}",
                eventId, event.getEventType());

        try {
            // ── Step 1: Redis SET NX EX — atomic check-and-set ──
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", IDEMPOTENCY_TTL);

            if (wasSet == null || !wasSet) {
                log.warn("Duplicate event detected (Redis idempotency), skipping: {}", eventId);
                return;
            }

            // ── Step 2: DB double-check (fallback if Redis lost data) ──
            boolean alreadyProcessedInDb = checkDbForProcessedEvent(event);
            if (alreadyProcessedInDb) {
                log.warn("Event already processed in DB (Redis miss fallback), skipping: {}", eventId);
                return;
            }

            // ── Step 3: Process the event ──
            processEvent(event);

            log.info("Successfully processed transaction event: {}", eventId);

        } catch (Exception e) {
            log.error("Error processing transaction event {}: {}",
                    event.getEventId(), e.getMessage(), e);

            // ── Step 4: Rollback Redis key so the event can be retried ──
            try {
                redisTemplate.delete(redisKey);
                log.info("Rolled back Redis idempotency key for retry: {}", eventId);
            } catch (Exception redisEx) {
                log.error("Failed to rollback Redis key {}: {}", eventId, redisEx.getMessage());
            }
            // In production, send to dead-letter queue
        } finally {
            acknowledgment.acknowledge();
        }
    }

    /**
     * Checks if the event has already been processed by querying the database
     * using idempotency keys.
     *
     * <p>This is a safety net for the case where Redis loses the idempotency key
     * (restart, eviction, flush) but the notification was already created in DB.
     * The idempotency keys used in {@link NotificationService#createFromTransactionEvent}
     * are: {@code tx-{eventId}-sender} and {@code tx-{eventId}-receiver}.</p>
     *
     * @param event the transaction event to check
     * @return true if any notification for this event already exists in DB
     */
    private boolean checkDbForProcessedEvent(TransactionEvent event) {
        String senderKey = "tx-" + event.getEventId() + "-sender";
        if (notificationRepository.existsByIdempotencyKey(senderKey)) {
            log.debug("DB double-check hit: sender notification exists for event: {}",
                    event.getEventId());
            return true;
        }

        String receiverKey = "tx-" + event.getEventId() + "-receiver";
        if (notificationRepository.existsByIdempotencyKey(receiverKey)) {
            log.debug("DB double-check hit: receiver notification exists for event: {}",
                    event.getEventId());
            return true;
        }

        return false;
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

        // Notify receiver (toUserId)
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
