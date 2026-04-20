package com.minibank.transaction.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.outbox.OutboxEvent;
import com.minibank.transaction.outbox.OutboxRepository;
import com.minibank.transaction.repository.TransactionRepository;

/**
 * Saga Orchestrator - Coordinates the distributed transaction.
 *
 * SAGA WORKFLOW:
 *
 * 1. START
 *    └── Send DEBIT_REQUEST to Account Service
 *
 * 2. DEBIT_SUCCESS received
 *    └── Send CREDIT_REQUEST to Account Service
 *
 * 3. CREDIT_SUCCESS received
 *    └── Mark transaction as COMPLETED
 *
 * FAILURE HANDLING:
 *
 * - If DEBIT_FAILURE → Mark transaction as FAILED
 * - If CREDIT_FAILURE → Send COMPENSATE_DEBIT, mark as COMPENSATED
 *
 * OUTBOX PATTERN:
 * All events are first written to outbox table, then published by background process.
 * This ensures events are never lost.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.saga.retry-count:3}")
    private int maxRetryCount;

    /**
     * Starts a new saga for money transfer.
     *
     * @param transaction the transaction to process
     */
    @Transactional
    public void startSaga(Transaction transaction) {
        log.info("Starting saga for transaction: {}", transaction.getId());

        // Mark transaction as processing
        transaction.markAsProcessing();
        transaction.markDebitPending();
        transactionRepository.save(transaction);

        // Create and save outbox event for DEBIT_REQUEST
        SagaEvent debitRequest = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(transaction.getSagaId())
                .transactionId(transaction.getId())
                .eventType(SagaEvent.EventType.DEBIT_REQUEST)
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(transaction, debitRequest, OutboxEvent.EventType.DEBIT_REQUESTED);

        log.info("Saga started: sagaId={}, step=DEBIT_PENDING", transaction.getSagaId());
    }

    /**
     * Handles DEBIT_SUCCESS event from Account Service.
     *
     * @param event the success event
     */
    @Transactional
    public void handleDebitSuccess(SagaEvent event) {
        log.info("Handling DEBIT_SUCCESS for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Update transaction state
        transaction.markDebitCompleted();
        transaction.markCreditPending();
        transactionRepository.save(transaction);

        // Create CREDIT_REQUEST event
        SagaEvent creditRequest = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(event.getSagaId())
                .transactionId(transaction.getId())
                .eventType(SagaEvent.EventType.CREDIT_REQUEST)
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(transaction, creditRequest, OutboxEvent.EventType.CREDIT_REQUESTED);

        log.info("Debit completed, sending CREDIT_REQUEST: sagaId={}", event.getSagaId());
    }

    /**
     * Handles DEBIT_FAILURE event from Account Service.
     *
     * @param event the failure event
     */
    @Transactional
    public void handleDebitFailure(SagaEvent event) {
        log.warn("Handling DEBIT_FAILURE for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Mark transaction as failed
        transaction.markAsFailed(event.getErrorMessage());
        transactionRepository.save(transaction);

        // Create SAGA_FAILED event
        SagaEvent sagaFailed = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(event.getSagaId())
                .transactionId(transaction.getId())
                .eventType(SagaEvent.EventType.SAGA_FAIL)
                .errorMessage(event.getErrorMessage())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(transaction, sagaFailed, OutboxEvent.EventType.SAGA_FAILED);

        log.warn("Saga failed at DEBIT step: sagaId={}, reason={}", event.getSagaId(), event.getErrorMessage());
    }

    /**
     * Handles CREDIT_SUCCESS event from Account Service.
     *
     * @param event the success event
     */
    @Transactional
    public void handleCreditSuccess(SagaEvent event) {
        log.info("Handling CREDIT_SUCCESS for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Update transaction state
        transaction.markCreditCompleted();
        transaction.markAsCompleted();
        transactionRepository.save(transaction);

        // Create SAGA_COMPLETED event
        SagaEvent sagaCompleted = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(event.getSagaId())
                .transactionId(transaction.getId())
                .eventType(SagaEvent.EventType.SAGA_COMPLETE)
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(transaction, sagaCompleted, OutboxEvent.EventType.SAGA_COMPLETED);

        log.info("Saga completed successfully: sagaId={}", event.getSagaId());
    }

    /**
     * Handles CREDIT_FAILURE event from Account Service.
     * Triggers compensation.
     *
     * @param event the failure event
     */
    @Transactional
    public void handleCreditFailure(SagaEvent event) {
        log.warn("Handling CREDIT_FAILURE for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Start compensation
        transaction.markCompensationStarted();
        transactionRepository.save(transaction);

        // Create COMPENSATE_DEBIT event (add money back to source)
        SagaEvent compensateRequest = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(event.getSagaId())
                .transactionId(transaction.getId())
                .eventType(SagaEvent.EventType.COMPENSATE_DEBIT)
                .fromAccountId(transaction.getFromAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(transaction, compensateRequest, OutboxEvent.EventType.COMPENSATION_REQUESTED);

        log.warn("Credit failed, starting compensation: sagaId={}", event.getSagaId());
    }

    /**
     * Handles COMPENSATE_SUCCESS event.
     *
     * @param event the success event
     */
    @Transactional
    public void handleCompensateSuccess(SagaEvent event) {
        log.info("Handling COMPENSATE_SUCCESS for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Mark transaction as compensated
        transaction.markCompensationCompleted();
        transaction.setFailureReason("Transaction compensated after credit failure");
        transactionRepository.save(transaction);

        log.info("Compensation completed: sagaId={}", event.getSagaId());
    }

    /**
     * Handles COMPENSATE_FAILURE event.
     * This is a critical situation - requires manual intervention.
     *
     * @param event the failure event
     */
    @Transactional
    public void handleCompensateFailure(SagaEvent event) {
        log.error("CRITICAL: COMPENSATE_FAILURE for saga: {}", event.getSagaId());

        Transaction transaction = transactionRepository.findBySagaId(event.getSagaId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getSagaId()));

        // Mark as failed - requires manual intervention
        transaction.markAsFailed("CRITICAL: Compensation failed - manual intervention required: "
                + event.getErrorMessage());
        transactionRepository.save(transaction);

        log.error("CRITICAL: Compensation failed, manual intervention required: sagaId={}", event.getSagaId());
    }

    /**
     * Saves an event to the outbox table.
     * This ensures the event will be published even if Kafka is temporarily unavailable.
     */
    private void saveOutboxEvent(Transaction transaction, SagaEvent sagaEvent, OutboxEvent.EventType eventType) {
        try {
            String payload = objectMapper.writeValueAsString(sagaEvent);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .sagaId(transaction.getSagaId())
                    .transactionId(transaction.getId())
                    .eventType(eventType)
                    .aggregateType("Transaction")
                    .aggregateId(transaction.getId())
                    .payload(payload)
                    .status(OutboxEvent.EventStatus.PENDING)
                    .build();

            outboxRepository.save(outboxEvent);

            log.debug("Saved outbox event: type={}, sagaId={}", eventType, transaction.getSagaId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize saga event: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize saga event", e);
        }
    }
}
