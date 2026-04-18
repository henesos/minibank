package com.minibank.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction event DTO received from Kafka.
 * 
 * This DTO represents transaction events published by transaction-service
 * that need to trigger notifications to users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    /**
     * Unique event identifier
     */
    private UUID eventId;

    /**
     * Saga correlation ID
     */
    private UUID sagaId;

    /**
     * Type of transaction event
     */
    private TransactionEventType eventType;

    /**
     * ID of the user initiating the transaction
     */
    private UUID fromUserId;

    /**
     * ID of the receiving user
     */
    private UUID toUserId;

    /**
     * Source account ID
     */
    private UUID fromAccountId;

    /**
     * Destination account ID
     */
    private UUID toAccountId;

    /**
     * Transaction amount
     */
    private BigDecimal amount;

    /**
     * Currency code (TRY, USD, EUR, etc.)
     */
    private String currency;

    /**
     * Transaction description
     */
    private String description;

    /**
     * Transaction status
     */
    private String status;

    /**
     * Failure reason if applicable
     */
    private String failureReason;

    /**
     * Event timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Idempotency key for deduplication
     */
    private String idempotencyKey;

    /**
     * Transaction event types that trigger notifications
     */
    public enum TransactionEventType {
        // Transaction initiated
        TRANSACTION_INITIATED,
        
        // Debit from source account completed
        DEBIT_COMPLETED,
        
        // Credit to destination completed
        CREDIT_COMPLETED,
        
        // Transaction fully completed
        TRANSACTION_COMPLETED,
        
        // Transaction failed
        TRANSACTION_FAILED,
        
        // Compensation started
        COMPENSATION_STARTED,
        
        // Compensation completed
        COMPENSATION_COMPLETED
    }
}
