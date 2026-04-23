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
import java.math.RoundingMode;
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
    }

    /**
     * Handles DEBIT_REQUEST - withdraw from source account.
     * Note: Transaction is managed by consumeSagaCommand's @Transactional.
     * Business exceptions (InsufficientBalance, AccountNotFound) are handled gracefully.
     * Unexpected errors trigger rollback via RuntimeException.
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

        } catch (RuntimeException e) {
            log.error("DEBIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEventFailure(sagaId, transactionId, DEBIT_FAILURE, fromAccountId, null, amount, 
                    "Unexpected error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("DEBIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE, fromAccountId, null, amount, 
                    "Unexpected error: " + e.getMessage()));
            throw new RuntimeException("DEBIT_REQUEST failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles CREDIT_REQUEST - deposit to destination account.
     * Note: Transaction is managed by consumeSagaCommand's @Transactional.
     * AccountNotFound is handled gracefully. Unexpected errors trigger rollback.
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

        } catch (RuntimeException e) {
            log.error("CREDIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEventFailure(sagaId, transactionId, CREDIT_FAILURE, null, toAccountId, amount, 
                    "Unexpected error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("CREDIT_FAILURE - Unexpected error: {}", e.getMessage(), e);
            publishEventFailure(sagaId, transactionId, CREDIT_FAILURE, null, toAccountId, amount, 
                    "Unexpected error: " + e.getMessage());
            throw new RuntimeException("CREDIT_REQUEST failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles COMPENSATE_DEBIT - refund to source account (rollback).
     * Note: Transaction is managed by consumeSagaCommand's @Transactional.
     * Errors trigger rollback via RuntimeException.
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

        } catch (RuntimeException e) {
            log.error("COMPENSATE_FAILURE - Error: {}", e.getMessage(), e);
            publishEventFailure(sagaId, transactionId, COMPENSATE_FAILURE, fromAccountId, null, amount, 
                    "Compensation failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("COMPENSATE_FAILURE - Error: {}", e.getMessage(), e);
            publishEventFailure(sagaId, transactionId, COMPENSATE_FAILURE, fromAccountId, null, amount, 
                    "Compensation failed: " + e.getMessage());
            throw new RuntimeException("COMPENSATE_DEBIT failed: " + e.getMessage(), e);
        }
    }

    /**
     * Publishes a failure event to saga-events topic.
     * Used when we need to publish failure response then throw to trigger rollback.
     */
    private void publishEventFailure(
            UUID sagaId, UUID transactionId, String eventType,
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String errorMessage) {
        try {
            Map<String, Object> event = createResponseEvent(
                    sagaId, transactionId, eventType, fromAccountId, toAccountId, amount, errorMessage);
            String key = sagaId.toString();
            kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event);
            kafkaTemplate.flush();
            log.info("Published failure event: type={}, sagaId={}", eventType, key);
        } catch (Exception e) {
            log.error("Failed to publish failure event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish failure event: " + e.getMessage(), e);
        }
    }

    /**
     * Publishes an event to saga-events topic.
     * Throws RuntimeException on failure to trigger transaction rollback.
     */
    private void publishEvent(Map<String, Object> event) {
        try {
            String key = event.get("sagaId").toString();
            kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event);
            kafkaTemplate.flush();
            log.info("Published event: type={}, sagaId={}", event.get("eventType"), key);
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish event: " + e.getMessage(), e);
        }
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
    private BigDecimal parseAmount(Object value) {
        return parseAmount(value, null);
    }

    private BigDecimal parseAmount(Object value, Object scaleObj) {
        if (value == null) return BigDecimal.ZERO;

        int scale = parseScale(scaleObj);
        String stringValue;

        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(scale, RoundingMode.HALF_UP);
        }

        if (value instanceof Number num) {
            if (num instanceof Double || num instanceof Float) {
                stringValue = new java.text.DecimalFormat("#.####################").format(num);
            } else {
                stringValue = num.toString();
            }
        } else {
            stringValue = value.toString();
        }

        return new BigDecimal(stringValue).setScale(scale, RoundingMode.HALF_UP);
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
