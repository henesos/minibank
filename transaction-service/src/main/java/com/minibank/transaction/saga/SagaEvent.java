package com.minibank.transaction.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga Event - Base class for all saga events.
 * 
 * Events are exchanged between Saga Orchestrator and participants via Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event ID
     */
    private UUID eventId;

    /**
     * Saga correlation ID
     */
    private UUID sagaId;

    /**
     * Transaction ID
     */
    private UUID transactionId;

    /**
     * Event type
     */
    private String eventType;

    /**
     * Source account ID
     */
    private UUID fromAccountId;

    /**
     * Destination account ID
     */
    private UUID toAccountId;

    /**
     * Transfer amount
     */
    private BigDecimal amount;

    /**
     * Currency
     */
    private String currency;

    /**
     * Timestamp when event was created
     */
    private LocalDateTime timestamp;

    /**
     * Error message (for failure events)
     */
    private String errorMessage;

    /**
     * Retry count
     */
    private Integer retryCount;

    /**
     * Creates a new SagaEvent with generated IDs and timestamp.
     */
    public static SagaEvent create(UUID sagaId, UUID transactionId, String eventType) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(sagaId)
                .transactionId(transactionId)
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    /**
     * Event types for the transfer saga
     */
    public static class EventType {
        // Saga lifecycle
        public static final String SAGA_START = "SAGA_START";
        public static final String SAGA_COMPLETE = "SAGA_COMPLETE";
        public static final String SAGA_FAIL = "SAGA_FAIL";

        // Debit operations
        public static final String DEBIT_REQUEST = "DEBIT_REQUEST";
        public static final String DEBIT_SUCCESS = "DEBIT_SUCCESS";
        public static final String DEBIT_FAILURE = "DEBIT_FAILURE";

        // Credit operations
        public static final String CREDIT_REQUEST = "CREDIT_REQUEST";
        public static final String CREDIT_SUCCESS = "CREDIT_SUCCESS";
        public static final String CREDIT_FAILURE = "CREDIT_FAILURE";

        // Compensation
        public static final String COMPENSATE_DEBIT = "COMPENSATE_DEBIT";
        public static final String COMPENSATE_SUCCESS = "COMPENSATE_SUCCESS";
        public static final String COMPENSATE_FAILURE = "COMPENSATE_FAILURE";
    }

    /**
     * Kafka topics
     */
    public static class Topics {
        // Orchestrator publishes to these
        public static final String SAGA_COMMANDS = "saga-commands";
        
        // Participants publish to this
        public static final String SAGA_EVENTS = "saga-events";
        
        // Specific service topics
        public static final String ACCOUNT_COMMANDS = "account-commands";
        public static final String ACCOUNT_EVENTS = "account-events";
    }
}
