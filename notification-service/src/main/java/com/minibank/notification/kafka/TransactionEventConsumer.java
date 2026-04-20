package com.minibank.notification.kafka;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Kafka consumer for transaction events.
 *
 * <p>Consumes transaction events from transaction-service and creates
 * notifications for affected users. Uses Redis for idempotency tracking
 * to prevent duplicate notification delivery across restarts and
 * multiple service instances.</p>
 *
 * <p>Redis key format: {@code notification:event:{eventId}}
 * TTL: Configurable via {@code notification.idempotency.ttl-hours} (default: 24h)</p>
 *
 * <p>Defense-in-depth: Redis provides O(1) fast first-pass check before
 * the DB-based idempotency in {@link NotificationService} (findByIdempotencyKey).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "notification:event:";

    @Value("${notification.idempotency.ttl-hours:24}")
    private int idempotencyTtlHours;

    /**
     * Consumes transaction events and creates notifications.
     *
     * <p>Idempotency flow:
     * <ol>
     *   <li>Check Redis for existing key ({@code notification:event:{eventId}})</li>
     *   <li>If key exists → duplicate detected, skip and acknowledge</li>
     *   <li>If key missing → process event, then set Redis key with TTL</li>
     * </ol></p>
     *
     * @param event the transaction event
     * @param key the Kafka message key
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
            // Check for duplicate processing via Redis
            String eventId = event.getEventId().toString();
            String redisKey = redisKey(eventId);
            Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);

            if (Boolean.TRUE.equals(alreadyProcessed)) {
                log.warn("Duplicate event detected, skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Process the event based on type
            processEvent(event);

            // Mark as processed in Redis with TTL (auto-expiry prevents memory leak)
            ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
            valueOps.set(redisKey, "1", Duration.ofHours(idempotencyTtlHours));

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
     * Builds the Redis key for a given event ID.
     *
     * @param eventId the event ID string
     * @return Redis key in format: notification:event:{eventId}
     */
    String redisKey(String eventId) {
        return IDEMPOTENCY_KEY_PREFIX + eventId;
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
     * Clears all idempotency keys from Redis (for testing only).
     *
     * <p>Uses SCAN instead of KEYS to avoid blocking Redis in production.
     * KEYS is O(N) and blocks the server — SCAN is incremental and non-blocking.</p>
     *
     * <p>Deletes all keys matching the pattern {@code notification:event:*}.</p>
     */
    public void clearProcessedEvents() {
        Collection<String> keys = redisTemplate.execute((RedisCallback<Collection<String>>) connection -> {
            List<String> matchedKeys = new ArrayList<>();
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(IDEMPOTENCY_KEY_PREFIX + "*")
                    .count(1000)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return matchedKeys;
        });

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} idempotency keys from Redis", keys.size());
        }
    }
}
