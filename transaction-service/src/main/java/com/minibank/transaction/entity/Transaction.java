package com.minibank.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Entity for MiniBank
 *
 * Represents a money transfer transaction between accounts.
 * Part of the Saga pattern for distributed transactions.
 *
 * TRANSACTION LIFECYCLE:
 * 1. PENDING - Initial state when transaction is created
 * 2. PROCESSING - Saga orchestrator is processing
 * 3. COMPLETED - Successfully completed
 * 4. FAILED - Failed with reason
 * 5. COMPENSATED - Compensation completed
 *
 * SAGA STATES:
 * - DEBIT_PENDING -> DEBIT_COMPLETED -> CREDIT_PENDING -> CREDIT_COMPLETED -> COMPLETED
 * - DEBIT_PENDING -> DEBIT_FAILED -> FAILED
 * - CREDIT_PENDING -> CREDIT_FAILED -> COMPENSATION_PENDING -> COMPENSATED
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_from_account", columnList = "from_account_id"),
    @Index(name = "idx_transaction_to_account", columnList = "to_account_id"),
    @Index(name = "idx_transaction_status", columnList = "status"),
    @Index(name = "idx_transaction_saga_id", columnList = "saga_id"),
    @Index(name = "idx_transaction_idempotency", columnList = "idempotency_key"),
    @Index(name = "idx_transaction_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Saga correlation ID - unique identifier for the entire saga
     */
    @Column(name = "saga_id", nullable = false, unique = true)
    private UUID sagaId;

    /**
     * Idempotency key for duplicate prevention
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(name = "from_user_id")
    private UUID fromUserId;

    @Column(name = "to_user_id")
    private UUID toUserId;

    /**
     * Transaction amount - always positive
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /**
     * Current step in the saga workflow
     */
    @Column(name = "saga_step", length = 30)
    @Enumerated(EnumType.STRING)
    private SagaStep sagaStep;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Transaction status lifecycle
     */
    public enum TransactionStatus {
        PENDING,        // Transaction created, waiting for processing
        PROCESSING,     // Saga orchestrator is processing
        DEBITED,        // Source account debited successfully
        COMPLETED,      // Transaction completed successfully
        FAILED,         // Transaction failed
        COMPENSATING,   // Compensation in progress
        COMPENSATED     // Compensation completed
    }

    /**
     * Saga workflow steps
     */
    public enum SagaStep {
        STARTED,
        DEBIT_PENDING,
        DEBIT_COMPLETED,
        DEBIT_FAILED,
        CREDIT_PENDING,
        CREDIT_COMPLETED,
        CREDIT_FAILED,
        COMPENSATION_PENDING,
        COMPENSATION_COMPLETED,
        COMPLETED,
        FAILED
    }

    /**
     * Marks transaction as processing.
     */
    public void markAsProcessing() {
        this.status = TransactionStatus.PROCESSING;
        this.sagaStep = SagaStep.STARTED;
    }

    /**
     * Marks that debit from source is pending.
     */
    public void markDebitPending() {
        this.sagaStep = SagaStep.DEBIT_PENDING;
    }

    /**
     * Marks that debit from source completed.
     */
    public void markDebitCompleted() {
        this.sagaStep = SagaStep.DEBIT_COMPLETED;
        this.status = TransactionStatus.DEBITED;
    }

    /**
     * Marks that credit to destination is pending.
     */
    public void markCreditPending() {
        this.sagaStep = SagaStep.CREDIT_PENDING;
    }

    /**
     * Marks that credit to destination completed.
     */
    public void markCreditCompleted() {
        this.sagaStep = SagaStep.CREDIT_COMPLETED;
    }

    /**
     * Marks transaction as completed.
     */
    public void markAsCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.sagaStep = SagaStep.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks transaction as failed.
     */
    public void markAsFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.sagaStep = SagaStep.FAILED;
        this.failureReason = reason;
    }

    /**
     * Marks compensation as started.
     */
    public void markCompensationStarted() {
        this.status = TransactionStatus.COMPENSATING;
        this.sagaStep = SagaStep.COMPENSATION_PENDING;
    }

    /**
     * Marks compensation as completed.
     */
    public void markCompensationCompleted() {
        this.status = TransactionStatus.COMPENSATED;
        this.sagaStep = SagaStep.COMPENSATION_COMPLETED;
    }

    /**
     * Increments retry count.
     */
    public void incrementRetry() {
        this.retryCount++;
    }

    /**
     * Checks if transaction can be retried.
     */
    public boolean canRetry(int maxRetries) {
        return this.retryCount < maxRetries &&
               (this.status == TransactionStatus.PROCESSING ||
                this.status == TransactionStatus.FAILED);
    }

    /**
     * Soft deletes the transaction.
     */
    public void softDelete() {
        this.deleted = true;
    }
}
