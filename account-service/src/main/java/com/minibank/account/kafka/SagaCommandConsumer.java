package com.minibank.account.kafka;

import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccountNotFoundException;
import com.minibank.account.exception.InsufficientBalanceException;
import com.minibank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saga Command Consumer for Account Service.
 * 
 * Listens to saga-commands topic and processes:
 * - DEBIT_REQUEST: Withdraw money from source account
 * - CREDIT_REQUEST: Deposit money to destination account
 * - COMPENSATE_DEBIT: Refund money to source account (rollback)
 * 
 * Publishes results to saga-events topic:
 * - DEBIT_SUCCESS / DEBIT_FAILURE
 * - CREDIT_SUCCESS / CREDIT_FAILURE
 * - COMPENSATE_SUCCESS / COMPENSATE_FAILURE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCommandConsumer {

    private final AccountService accountService;
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    private static final String SAGA_COMMANDS_TOPIC = "saga-commands";
    private static final String SAGA_EVENTS_TOPIC = "saga-events";

    /** Default scale for financial amounts — matches DECIMAL(19,4) */
    private static final int DEFAULT_AMOUNT_SCALE = 4;

    // Event Types - Commands
    private static final String DEBIT_REQUEST = "DEBIT_REQUEST";
    private static final String CREDIT_REQUEST = "CREDIT_REQUEST";
    private static final String COMPENSATE_DEBIT = "COMPENSATE_DEBIT";

    // Event Types - Responses
    private static final String DEBIT_SUCCESS = "DEBIT_SUCCESS";
    private static final String DEBIT_FAILURE = "DEBIT_FAILURE";
    private static final String CREDIT_SUCCESS = "CREDIT_SUCCESS";
    private static final String CREDIT_FAILURE = "CREDIT_FAILURE";
    private static final String COMPENSATE_SUCCESS = "COMPENSATE_SUCCESS";
    private static final String COMPENSATE_FAILURE = "COMPENSATE_FAILURE";

    /**
     * Consumes saga commands from saga-commands topic.
     * 
     * @Transactional ensures database operations within each handler
     * are atomic — if any step fails, the entire operation rolls back.
     */
    @Transactional
    @KafkaListener(topics = SAGA_COMMANDS_TOPIC, groupId = "account-service-group")
    public void consumeSagaCommand(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        UUID sagaId = parseUUID(event.get("sagaId"));
        UUID transactionId = parseUUID(event.get("transactionId"));

        log.info("Received saga command: type={}, sagaId={}, transactionId={}", 
                eventType, sagaId, transactionId);

        try {
            switch (eventType) {
                case DEBIT_REQUEST:
                    handleDebitRequest(event);
                    break;
                case CREDIT_REQUEST:
                    handleCreditRequest(event);
                    break;
                case COMPENSATE_DEBIT:
                    handleCompensateDebit(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing saga command: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles DEBIT_REQUEST - withdraw from source account.
     */
    private void handleDebitRequest(Map<String, Object> event) {
        UUID sagaId = parseUUID(event.get("sagaId"));
        UUID transactionId = parseUUID(event.get("transactionId"));
        UUID fromAccountId = parseUUID(event.get("fromAccountId"));
        BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

        log.info("Processing DEBIT_REQUEST: account={}, amount={}", fromAccountId, amount);

        try {
            // Withdraw from source account
            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            
            accountService.withdraw(fromAccountId, request);

            log.info("DEBIT_SUCCESS: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_SUCCESS, fromAccountId, null, amount, null));

        } catch (InsufficientBalanceException e) {
            log.warn("DEBIT_FAILURE - Insufficient balance: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE, fromAccountId, null, amount, 
                    "Insufficient balance: " + e.getMessage()));

        } catch (AccountNotFoundException e) {
            log.error("DEBIT_FAILURE - Account not found: {}", fromAccountId);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE, fromAccountId, null, amount, 
                    "Account not found: " + fromAccountId));

        } catch (Exception e) {
            log.error("DEBIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE, fromAccountId, null, amount, 
                    "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Handles CREDIT_REQUEST - deposit to destination account.
     */
    private void handleCreditRequest(Map<String, Object> event) {
        UUID sagaId = parseUUID(event.get("sagaId"));
        UUID transactionId = parseUUID(event.get("transactionId"));
        UUID toAccountId = parseUUID(event.get("toAccountId"));
        BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

        log.info("Processing CREDIT_REQUEST: account={}, amount={}", toAccountId, amount);

        try {
            // Deposit to destination account
            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            
            accountService.deposit(toAccountId, request);

            log.info("CREDIT_SUCCESS: account={}, amount={}", toAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_SUCCESS, null, toAccountId, amount, null));

        } catch (AccountNotFoundException e) {
            log.error("CREDIT_FAILURE - Account not found: {}", toAccountId);
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_FAILURE, null, toAccountId, amount, 
                    "Account not found: " + toAccountId));

        } catch (Exception e) {
            log.error("CREDIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_FAILURE, null, toAccountId, amount, 
                    "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Handles COMPENSATE_DEBIT - refund to source account (rollback).
     */
    private void handleCompensateDebit(Map<String, Object> event) {
        UUID sagaId = parseUUID(event.get("sagaId"));
        UUID transactionId = parseUUID(event.get("transactionId"));
        UUID fromAccountId = parseUUID(event.get("fromAccountId"));
        BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

        log.info("Processing COMPENSATE_DEBIT: account={}, amount={}", fromAccountId, amount);

        try {
            // Refund to source account
            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            
            accountService.deposit(fromAccountId, request);

            log.info("COMPENSATE_SUCCESS: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_SUCCESS, fromAccountId, null, amount, null));

        } catch (Exception e) {
            log.error("COMPENSATE_FAILURE - Error: {}", e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_FAILURE, fromAccountId, null, amount, 
                    "Compensation failed: " + e.getMessage()));
        }
    }

    /**
     * Publishes an event to saga-events topic.
     */
    private void publishEvent(Map<String, Object> event) {
        String key = event.get("sagaId").toString();
        kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event);
        log.info("Published event: type={}, sagaId={}", event.get("eventType"), key);
    }

    /**
     * Creates a response event map.
     */
    private Map<String, Object> createResponseEvent(
            UUID sagaId, 
            UUID transactionId, 
            String eventType,
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            String errorMessage) {
        
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("sagaId", sagaId != null ? sagaId.toString() : null);
        event.put("transactionId", transactionId != null ? transactionId.toString() : null);
        event.put("eventType", eventType);
        event.put("fromAccountId", fromAccountId != null ? fromAccountId.toString() : null);
        event.put("toAccountId", toAccountId != null ? toAccountId.toString() : null);
        event.put("amount", amount);
        event.put("currency", "TRY");
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("errorMessage", errorMessage);
        return event;
    }

    private UUID parseUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID) return (UUID) value;
        return UUID.fromString(value.toString());
    }

    /**
     * Parses an amount value from Kafka event to BigDecimal with proper precision.
     * 
     * CRITICAL FIX: Avoids doubleValue() which introduces floating-point errors.
     * Uses String-based conversion to preserve exact decimal representation.
     * 
     * Kafka deserializes JSON numbers as Integer, Long, or Double depending on magnitude.
     * Using doubleValue() on a Double like 99.99 produces 99.98999999999999...
     * Instead, we convert via toString() to preserve the original decimal precision.
     * 
     * @param value the raw amount from the event
     * @param scaleObj optional scale from the event (defaults to 4 for DECIMAL(19,4))
     * @return BigDecimal with proper scale
     */
    private BigDecimal parseAmount(Object value, Object scaleObj) {
        if (value == null) return BigDecimal.ZERO;
        
        // If already BigDecimal, just apply scale
        if (value instanceof BigDecimal) {
            int scale = parseScale(scaleObj);
            return ((BigDecimal) value).setScale(scale, java.math.RoundingMode.HALF_UP);
        }
        
        // String-based conversion — preserves exact decimal representation
        // This avoids the precision loss of doubleValue()
        int scale = parseScale(scaleObj);
        BigDecimal result;
        
        if (value instanceof Number) {
            // Convert Number via its string representation to avoid floating-point errors
            // e.g., Double 99.99 → "99.99" → BigDecimal("99.99") (exact)
            String stringValue = value.toString();
            result = new BigDecimal(stringValue);
        } else {
            // Fallback: use string representation directly
            result = new BigDecimal(value.toString());
        }
        
        return result.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Parses the scale from the event, defaulting to DECIMAL(19,4) scale.
     * 
     * @param scaleObj the scale value from the event
     * @return scale integer (default 4)
     */
    private int parseScale(Object scaleObj) {
        if (scaleObj == null) return DEFAULT_AMOUNT_SCALE;
        try {
            return Integer.parseInt(scaleObj.toString());
        } catch (NumberFormatException e) {
            return DEFAULT_AMOUNT_SCALE;
        }
    }
}
