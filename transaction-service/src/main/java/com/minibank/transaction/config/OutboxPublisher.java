package com.minibank.transaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import com.minibank.transaction.outbox.OutboxEvent;
import com.minibank.transaction.outbox.OutboxRepository;
import com.minibank.transaction.saga.SagaEvent;

/**
 * Outbox Publisher - Background process that publishes events to Kafka.
 *
 * Implements the Outbox Pattern:
 * 1. Reads pending events from outbox table
 * 2. Publishes to Kafka
 * 3. Marks events as SENT
 *
 * This ensures reliable event delivery even if Kafka is temporarily unavailable.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int CLEANUP_RETENTION_DAYS = 7;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, SagaEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.commands:saga-commands}")
    private String commandsTopic;

    @Value("${app.kafka.topic.events:saga-events}")
    private String eventsTopic;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    /**
     * Publishes pending outbox events to Kafka.
     * Runs every 1 second.
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEventsWithLimit(batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                event.markAsSent();
                outboxRepository.save(event);
                log.debug("Published outbox event: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, error={}", event.getId(), e.getMessage());
                event.markAsFailed(e.getMessage());
                outboxRepository.save(event);
            }
        }
    }

    /**
     * Retries failed outbox events.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxRepository.findRetryableEvents(MAX_RETRY_COUNT);

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed outbox events", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            try {
                event.incrementRetry();
                publishEvent(event);
                event.markAsSent();
                outboxRepository.save(event);
                log.info("Retry successful for outbox event: id={}", event.getId());
            } catch (Exception e) {
                log.error("Retry failed for outbox event: id={}, error={}", event.getId(), e.getMessage());
                event.markAsFailed(e.getMessage());
                outboxRepository.save(event);
            }
        }
    }

    /**
     * Cleans up old processed events.
     * Runs every hour.
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(CLEANUP_RETENTION_DAYS);
        int deleted = outboxRepository.deleteOldEvents(threshold);
        if (deleted > 0) {
            log.info("Cleaned up {} old outbox events", deleted);
        }
    }

    /**
     * Publishes an event to Kafka.
     */
    private void publishEvent(OutboxEvent outboxEvent) {
        try {
            SagaEvent sagaEvent = objectMapper.readValue(outboxEvent.getPayload(), SagaEvent.class);

            String topic = determineTopic(outboxEvent.getEventType());
            kafkaTemplate.send(topic, outboxEvent.getSagaId().toString(), sagaEvent).get();

            log.debug("Event sent to Kafka: topic={}, sagaId={}", topic, outboxEvent.getSagaId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event to Kafka", e);
        }
    }

    /**
     * Determines the Kafka topic based on event type.
     */
    private String determineTopic(OutboxEvent.EventType eventType) {
        // Commands go to account-service
        if (eventType == OutboxEvent.EventType.DEBIT_REQUESTED ||
            eventType == OutboxEvent.EventType.CREDIT_REQUESTED ||
            eventType == OutboxEvent.EventType.COMPENSATION_REQUESTED) {
            return commandsTopic;
        }
        // Other events go to events topic
        return eventsTopic;
    }
}
