package com.minibank.transaction.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Entity for MiniBank
 * 
 * Implements the Outbox Pattern for reliable event publishing.
 * 
 * OUTBOX PATTERN FLOW:
 * 1. Transaction is created in DB
 * 2. Outbox event is created in the SAME transaction
 * 3. Background process reads outbox events
 * 4. Events are published to Kafka
 * 5. Outbox event is marked as SENT
 * 
 * This ensures that events are never lost, even if Kafka is temporarily unavailable.
 * 
 * CRITICAL: Outbox and Transaction must be in the same database transaction!
 */
@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_saga_id", columnList = "saga_id"),
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Correlation ID - links to the saga
     */
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    /**
     * Transaction ID this event belongs to
     */
    @Column(name = "transaction_id")
    private UUID transactionId;

    /**
     * Event type for routing
     */
    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    /**
     * Kafka topic to publish to
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /**
     * Aggregate ID (usually transaction ID)
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /**
     * Event payload as JSON
     */
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    /**
     * Event status
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    /**
     * Number of publish attempts
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Error message if publishing failed
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * When the event was sent successfully
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Event types for the Saga
     */
    public enum EventType {
        // Saga lifecycle events
        SAGA_STARTED,
        DEBIT_REQUESTED,
        DEBIT_COMPLETED,
        DEBIT_FAILED,
        CREDIT_REQUESTED,
        CREDIT_COMPLETED,
        CREDIT_FAILED,
        COMPENSATION_REQUESTED,
        COMPENSATION_COMPLETED,
        SAGA_COMPLETED,
        SAGA_FAILED
    }

    /**
     * Event status
     */
    public enum EventStatus {
        PENDING,    // Waiting to be sent
        SENT,       // Successfully sent to Kafka
        FAILED      // Failed to send after retries
    }

    /**
     * Marks event as sent.
     */
    public void markAsSent() {
        this.status = EventStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Marks event as failed.
     */
    public void markAsFailed(String errorMessage) {
        this.status = EventStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Increments retry count.
     */
    public void incrementRetry() {
        this.retryCount++;
    }

    /**
     * Checks if event can be retried.
     */
    public boolean canRetry(int maxRetries) {
        return this.retryCount < maxRetries && this.status == EventStatus.FAILED;
    }
}
