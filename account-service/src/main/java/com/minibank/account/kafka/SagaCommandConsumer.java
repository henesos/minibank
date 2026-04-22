package com.minibank.account.kafka;

import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccountNotFoundException;
import com.minibank.account.exception.InsufficientBalanceException;
import com.minibank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saga Command Consumer for Account Service.
 *
 * <p>Listens to saga-commands topic and processes:
 * <ul>
 *   <li>DEBIT_REQUEST: Withdraw money from source account</li>
 *   <li>CREDIT_REQUEST: Deposit money to destination account</li>
 *   <li>COMPENSATE_DEBIT: Refund money to source account (rollback)</li>
 * </ul>
 *
 * <h3>ADR-014 — @Transactional + Manual Ack Coordination</h3>
 * <p>{@code @Transactional} ensures DB operations are atomic. Manual ack ensures
 * the Kafka message is acknowledged <strong>only after</strong> the DB transaction
 * commits successfully. On rollback the message is NOT acknowledged, so Kafka
 * redelivers it — this prevents DOUBLE DEBIT and SAGA STATE INCONSISTENCY.
 *
 * <h3>ADR-015 — String-Based BigDecimal Conversion</h3>
 * <p>{@link #parseAmount} uses {@code toString()} → {@code new BigDecimal(String)}
 * instead of {@code doubleValue()}. Combined with the consumer {@code ObjectMapper}
 * configured with {@code USE_BIG_DECIMAL_FOR_FLOATS}, this eliminates
 * floating-point precision loss for financial amounts.
 *
 * <p>Publishes results to saga-events topic:
 * DEBIT_SUCCESS / DEBIT_FAILURE, CREDIT_SUCCESS / CREDIT_FAILURE,
 * COMPENSATE_SUCCESS / COMPENSATE_FAILURE.
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

    // ========================================================================
    // Kafka Consumer Entry Point
    // ========================================================================

    /**
     * Consumes saga commands from saga-commands topic.
     *
     * <p><strong>ADR-014 Transaction &amp; Ack Coordination:</strong>
     * <ul>
     *   <li>Business exceptions (InsufficientBalance, AccountNotFound): handler
     *       publishes a failure event, transaction commits normally, message is acked.</li>
     *   <li>Unexpected exceptions: transaction rolls back, message is NOT acked,
     *       Kafka redelivers the message for reprocessing.</li>
     * </ul>
     * <p>Ack is registered via {@link TransactionSynchronization#afterCommit()} callback,
     * ensuring the message is acknowledged ONLY after the DB transaction commits.
     * A defensive check for missing transaction context acks immediately
     * (safety net for unit tests / unexpected scenarios).
     *
     * @param event the saga command event map (deserialized from Kafka JSON)
     * @param ack   Kafka manual acknowledgment
     */
    @Transactional
    @KafkaListener(topics = SAGA_COMMANDS_TOPIC, groupId = "account-service-group")
    public void consumeSagaCommand(Map<String, Object> event, Acknowledgment ack) {
        String eventType = (String) event.get("eventType");

        // --- Pre-validation: skip malformed messages without retrying ---
        if (eventType == null) {
            log.warn("Received saga command with null eventType — acknowledging and skipping");
            ack.acknowledge();
            return;
        }

        UUID sagaId;
        UUID transactionId;
        try {
            sagaId = parseUUID(event.get("sagaId"));
            transactionId = parseUUID(event.get("transactionId"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid saga identifiers in command: eventType={}, error={} — skipping",
                    eventType, e.getMessage());
            ack.acknowledge();
            return;
        }

        log.info("Received saga command: type={}, sagaId={}, transactionId={}",
                eventType, sagaId, transactionId);

        try {
            switch (eventType) {
                case DEBIT_REQUEST:
                    handleDebitRequest(event, sagaId, transactionId);
                    break;
                case CREDIT_REQUEST:
                    handleCreditRequest(event, sagaId, transactionId);
                    break;
                case COMPENSATE_DEBIT:
                    handleCompensateDebit(event, sagaId, transactionId);
                    break;
                default:
                    log.warn("Unknown event type: {} for sagaId={}", eventType, sagaId);
            }

            // ADR-014: Ack ONLY after successful DB commit
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                ack.acknowledge();
                                log.debug("Acknowledged saga command after commit: type={}, sagaId={}",
                                        eventType, sagaId);
                            }
                        });
            } else {
                // Defensive: no active transaction (e.g. unit-test without Spring context)
                log.warn("No active transaction — acknowledging immediately: type={}, sagaId={}",
                        eventType, sagaId);
                ack.acknowledge();
            }

        } catch (Exception e) {
            log.error("Error processing saga command: type={}, sagaId={}, error={}",
                    eventType, sagaId, e.getMessage(), e);
            // Do NOT ack — @Transactional will roll back, Kafka will redeliver (ADR-014)
            throw e;
        }
    }

    // ========================================================================
    // Saga Command Handlers
    // ========================================================================

    /**
     * Handles DEBIT_REQUEST — withdraw from source account.
     *
     * <p>Business exceptions ({@link InsufficientBalanceException},
     * {@link AccountNotFoundException}) are caught and converted to
     * {@code DEBIT_FAILURE} events. These do NOT cause transaction rollback.
     *
     * <p>Unexpected exceptions (e.g. Kafka publish failure after a successful
     * withdraw) propagate to trigger rollback, preventing SAGA STATE INCONSISTENCY.
     */
    private void handleDebitRequest(Map<String, Object> event, UUID sagaId, UUID transactionId) {
        try {
            UUID fromAccountId = parseUUID(event.get("fromAccountId"));
            BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

            log.info("Processing DEBIT_REQUEST: account={}, amount={}", fromAccountId, amount);

            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            accountService.withdraw(fromAccountId, request);

            log.info("DEBIT_SUCCESS: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_SUCCESS,
                    fromAccountId, null, amount, null));

        } catch (IllegalArgumentException e) {
            // Parse error — invalid parameters, publish failure and move on
            log.error("DEBIT_FAILURE - Invalid parameters: {}", e.getMessage());
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE,
                    null, null, BigDecimal.ZERO, "Invalid parameters: " + e.getMessage()));
        } catch (InsufficientBalanceException e) {
            log.warn("DEBIT_FAILURE - Insufficient balance: sagaId={}", sagaId);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE,
                    null, null, BigDecimal.ZERO, "Insufficient balance: " + e.getMessage()));
        } catch (AccountNotFoundException e) {
            log.error("DEBIT_FAILURE - Account not found: sagaId={}", sagaId);
            publishEvent(createResponseEvent(sagaId, transactionId, DEBIT_FAILURE,
                    null, null, BigDecimal.ZERO, "Account not found"));
        }
        // Note: RuntimeException from withdraw() or publishEvent() propagates
        // → @Transactional rollback + no ack → Kafka redelivers (ADR-014)
    }

    /**
     * Handles CREDIT_REQUEST — deposit to destination account.
     *
     * @see #handleDebitRequest(Map, UUID, UUID) for error handling strategy
     */
    private void handleCreditRequest(Map<String, Object> event, UUID sagaId, UUID transactionId) {
        try {
            UUID toAccountId = parseUUID(event.get("toAccountId"));
            BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

            log.info("Processing CREDIT_REQUEST: account={}, amount={}", toAccountId, amount);

            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            accountService.deposit(toAccountId, request);

            log.info("CREDIT_SUCCESS: account={}, amount={}", toAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_SUCCESS,
                    null, toAccountId, amount, null));

        } catch (IllegalArgumentException e) {
            log.error("CREDIT_FAILURE - Invalid parameters: {}", e.getMessage());
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_FAILURE,
                    null, null, BigDecimal.ZERO, "Invalid parameters: " + e.getMessage()));
        } catch (AccountNotFoundException e) {
            log.error("CREDIT_FAILURE - Account not found: sagaId={}", sagaId);
            publishEvent(createResponseEvent(sagaId, transactionId, CREDIT_FAILURE,
                    null, null, BigDecimal.ZERO, "Account not found"));
        }
    }

    /**
     * Handles COMPENSATE_DEBIT — refund to source account (saga rollback).
     *
     * @see #handleDebitRequest(Map, UUID, UUID) for error handling strategy
     */
    private void handleCompensateDebit(Map<String, Object> event, UUID sagaId, UUID transactionId) {
        try {
            UUID fromAccountId = parseUUID(event.get("fromAccountId"));
            BigDecimal amount = parseAmount(event.get("amount"), event.get("scale"));

            log.info("Processing COMPENSATE_DEBIT: account={}, amount={}", fromAccountId, amount);

            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(amount);
            accountService.deposit(fromAccountId, request);

            log.info("COMPENSATE_SUCCESS: account={}, amount={}", fromAccountId, amount);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_SUCCESS,
                    fromAccountId, null, amount, null));

        } catch (IllegalArgumentException e) {
            log.error("COMPENSATE_FAILURE - Invalid parameters: {}", e.getMessage());
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_FAILURE,
                    null, null, BigDecimal.ZERO, "Invalid parameters: " + e.getMessage()));
        } catch (Exception e) {
            log.error("COMPENSATE_FAILURE - Error: sagaId={}, error={}", sagaId, e.getMessage(), e);
            publishEvent(createResponseEvent(sagaId, transactionId, COMPENSATE_FAILURE,
                    null, null, BigDecimal.ZERO, "Compensation failed: " + e.getMessage()));
        }
        // Note: RuntimeException from deposit() or publishEvent() propagates
        // → @Transactional rollback + no ack → Kafka redelivers (ADR-014)
    }

    // ========================================================================
    // Event Publishing
    // ========================================================================

    /**
     * Publishes an event to saga-events topic.
     */
    private void publishEvent(Map<String, Object> event) {
        String key = event.get("sagaId").toString();
        kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event);
        log.info("Published event: type={}, sagaId={}", event.get("eventType"), key);
    }

    /**
     * Creates a response event map to be published on saga-events topic.
     *
     * @param sagaId        saga correlation ID
     * @param transactionId transaction ID
     * @param eventType     response event type (e.g. DEBIT_SUCCESS)
     * @param fromAccountId source account (may be null)
     * @param toAccountId   destination account (may be null)
     * @param amount        transaction amount (BigDecimal, never null in practice)
     * @param errorMessage  failure reason (null for success events)
     * @return event map ready for Kafka publishing
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

    // ========================================================================
    // Parsing Utilities
    // ========================================================================

    /**
     * Parses a UUID value from a Kafka event field.
     *
     * <p>Throws {@link IllegalArgumentException} for null or malformed values.
     * In a banking domain, missing identifiers must be treated as errors —
     * silently returning null would risk processing invalid messages.
     *
     * @param value the raw value from the event map
     * @return parsed UUID
     * @throws IllegalArgumentException if value is null or not a valid UUID
     */
    private UUID parseUUID(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID value is null");
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID value: " + value, e);
        }
    }

    /**
     * Parses an amount value from Kafka event to {@link BigDecimal} with proper scale.
     *
     * <p><strong>ADR-015 — String-Based BigDecimal Conversion:</strong>
     * Uses {@code toString()} → {@code new BigDecimal(String)} instead of
     * {@code doubleValue()} to avoid floating-point precision loss.
     *
     * <p>Kafka's Jackson {@link com.fasterxml.jackson.databind.ObjectMapper} may
     * deserialize JSON numbers as {@code Integer}, {@code Long}, or {@code Double}.
     * Using {@code doubleValue()} on a {@code Double} like {@code 99.99} produces
     * {@code 99.98999999999999...}. Converting via {@code toString()} preserves
     * the original decimal precision: {@code "99.99"} → {@code BigDecimal("99.99")}.
     *
     * <p>Combined with the consumer {@code ObjectMapper} configured with
     * {@code DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS}, most amounts
     * arrive as {@code BigDecimal} directly, avoiding the conversion entirely.
     *
     * @param value    the raw amount from the event
     * @param scaleObj optional scale from the event (defaults to 4 for DECIMAL(19,4))
     * @return BigDecimal with proper scale
     * @throws IllegalArgumentException if value is null
     */
    private BigDecimal parseAmount(Object value, Object scaleObj) {
        if (value == null) {
            throw new IllegalArgumentException("Amount value is null");
        }

        int scale = parseScale(scaleObj);

        // If already BigDecimal, just apply scale
        if (value instanceof BigDecimal bd) {
            return bd.setScale(scale, RoundingMode.HALF_UP);
        }

        // String-based conversion — preserves exact decimal representation
        // This avoids the precision loss of doubleValue()
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

        return result.setScale(scale, RoundingMode.HALF_UP);
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
