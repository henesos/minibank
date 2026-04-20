package com.minibank.account.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccountNotFoundException;
import com.minibank.account.exception.InsufficientBalanceException;
import com.minibank.account.service.AccountService;

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
    private static final String DEFAULT_CURRENCY = "TRY";

    /**
     * Consumes saga commands from saga-commands topic.
     */
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
        BigDecimal amount = parseAmount(event.get("amount"));

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
        BigDecimal amount = parseAmount(event.get("amount"));

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
        BigDecimal amount = parseAmount(event.get("amount"));

        log.info("Processing COMPENSATE_DEBIT: account={}, amount={}", fromAccountId, amount);

        try {
            // Refund to source account
            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);

            accountService.deposit(fromAccountId, request);

            log.info("COMPENSATE_SUCCESS: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_SUCCESS,
                    fromAccountId, null, amount, null));

        } catch (Exception e) {
            log.error("COMPENSATE_FAILURE - Error: {}", e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_FAILURE, fromAccountId, null, amount,
                    "Compensation failed: " + e.getMessage()));
        }
    }

    /**
     * Publishes an event to saga-events topic.
     * Uses async send with error callback to detect delivery failures.
     */
    private void publishEvent(Map<String, Object> event) {
        String key = event.get("sagaId").toString();
        kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event: type={}, sagaId={}, error={}",
                        event.get("eventType"), key, ex.getMessage(), ex);
            } else {
                log.info("Published event: type={}, sagaId={}, offset={}",
                        event.get("eventType"), key, result.getRecordMetadata().offset());
            }
        });
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
        event.put("currency", DEFAULT_CURRENCY);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("errorMessage", errorMessage);
        return event;
    }

    private UUID parseUUID(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID value is null");
        }
        if (value instanceof UUID) return (UUID) value;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID value: " + value, e);
        }
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) throw new IllegalArgumentException("Amount value is null");
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount value: " + value, e);
        }
    }
}
