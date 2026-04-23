package com.minibank.transaction.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.outbox.OutboxEvent;
import com.minibank.transaction.outbox.OutboxRepository;
import com.minibank.transaction.repository.TransactionRepository;
import com.minibank.transaction.saga.SagaEvent;
import com.minibank.transaction.saga.SagaOrchestrator;
import com.minibank.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for SagaOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;

    private Transaction testTransaction;
    private UUID sagaId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        sagaId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        testTransaction = Transaction.builder()
                .id(transactionId)
                .sagaId(sagaId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("TRY")
                .status(Transaction.TransactionStatus.PENDING)
                .build();

        ReflectionTestUtils.setField(sagaOrchestrator, "transactionService", transactionService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Start Saga Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Start Saga")
    class StartSagaTests {

        @Test
        @DisplayName("Should start saga successfully")
        void startSaga_Success() throws Exception {
            // Arrange
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            // Act
            sagaOrchestrator.startSaga(testTransaction);

            // Assert
            verify(transactionRepository).save(any(Transaction.class));
            verify(outboxRepository).save(any(OutboxEvent.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Handle Success Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Handle Debit Success")
    class HandleDebitSuccessTests {

        @Test
        @DisplayName("Should progress to credit step after debit success")
        void handleDebitSuccess_ProgressesToCredit() throws Exception {
            // Arrange
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.DEBIT_SUCCESS)
                    .build();

            testTransaction.setStatus(Transaction.TransactionStatus.PROCESSING);
            testTransaction.setSagaStep(Transaction.SagaStep.DEBIT_PENDING);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            // Act
            sagaOrchestrator.handleDebitSuccess(event);

            // Assert
            verify(transactionRepository).save(argThat(tx -> 
                tx.getSagaStep() == Transaction.SagaStep.CREDIT_PENDING
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Handle Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Handle Failures")
    class HandleFailureTests {

        @Test
        @DisplayName("Should mark transaction as failed on debit failure")
        void handleDebitFailure_MarksFailed() throws Exception {
            // Arrange
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.DEBIT_FAILURE)
                    .errorMessage("Insufficient balance")
                    .build();

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            // Act
            sagaOrchestrator.handleDebitFailure(event);

            // Assert
            verify(transactionRepository).save(argThat(tx -> 
                tx.getStatus() == Transaction.TransactionStatus.FAILED &&
                "Insufficient balance".equals(tx.getFailureReason())
            ));
        }

        @Test
        @DisplayName("Should start compensation on credit failure")
        void handleCreditFailure_StartsCompensation() throws Exception {
            // Arrange
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.CREDIT_FAILURE)
                    .errorMessage("Account suspended")
                    .build();

            testTransaction.setStatus(Transaction.TransactionStatus.DEBITED);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            // Act
            sagaOrchestrator.handleCreditFailure(event);

            // Assert
            verify(transactionRepository).save(argThat(tx -> 
                tx.getStatus() == Transaction.TransactionStatus.COMPENSATING
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Handle Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Handle Compensation")
    class HandleCompensationTests {

        @Test
        @DisplayName("Should mark as compensated after compensation success")
        void handleCompensateSuccess_MarksCompensated() {
            // Arrange
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.COMPENSATE_SUCCESS)
                    .build();

            testTransaction.setStatus(Transaction.TransactionStatus.COMPENSATING);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

            // Act
            sagaOrchestrator.handleCompensateSuccess(event);

            // Assert
            verify(transactionRepository).save(argThat(tx -> 
                tx.getStatus() == Transaction.TransactionStatus.COMPENSATED
            ));
        }

        @Test
        @DisplayName("Should require manual intervention on compensation failure")
        void handleCompensateFailure_RequiresManualIntervention() {
            // Arrange
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.COMPENSATE_FAILURE)
                    .errorMessage("System error")
                    .build();

            testTransaction.setStatus(Transaction.TransactionStatus.COMPENSATING);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

            // Act
            sagaOrchestrator.handleCompensateFailure(event);

            // Assert
            verify(transactionRepository).save(argThat(tx -> 
                tx.getStatus() == Transaction.TransactionStatus.FAILED &&
                tx.getFailureReason().contains("CRITICAL")
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Idempotency Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency Key Management")
    class IdempotencyTests {

        @Test
        @DisplayName("Should mark idempotency complete on debit failure")
        void handleDebitFailure_MarksIdempotencyComplete() throws Exception {
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.DEBIT_FAILURE)
                    .errorMessage("Insufficient balance")
                    .build();

            testTransaction.setIdempotencyKey("test-idemp-key");

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            sagaOrchestrator.handleDebitFailure(event);

            verify(transactionService).markIdempotencyComplete(
                    eq("test-idemp-key"), eq(transactionId));
        }

        @Test
        @DisplayName("Should mark idempotency complete on credit success")
        void handleCreditSuccess_MarksIdempotencyComplete() throws Exception {
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.CREDIT_SUCCESS)
                    .build();

            testTransaction.setIdempotencyKey("test-idemp-key");
            testTransaction.setStatus(Transaction.TransactionStatus.DEBITED);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(null);

            sagaOrchestrator.handleCreditSuccess(event);

            verify(transactionService).markIdempotencyComplete(
                    eq("test-idemp-key"), eq(transactionId));
        }

        @Test
        @DisplayName("Should mark idempotency complete on compensation success")
        void handleCompensateSuccess_MarksIdempotencyComplete() {
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.COMPENSATE_SUCCESS)
                    .build();

            testTransaction.setIdempotencyKey("test-idemp-key");
            testTransaction.setStatus(Transaction.TransactionStatus.COMPENSATING);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

            sagaOrchestrator.handleCompensateSuccess(event);

            verify(transactionService).markIdempotencyComplete(
                    eq("test-idemp-key"), eq(transactionId));
        }

        @Test
        @DisplayName("Should mark idempotency complete on compensation failure")
        void handleCompensateFailure_MarksIdempotencyComplete() {
            SagaEvent event = SagaEvent.builder()
                    .sagaId(sagaId)
                    .eventType(SagaEvent.EventType.COMPENSATE_FAILURE)
                    .errorMessage("System error")
                    .build();

            testTransaction.setIdempotencyKey("test-idemp-key");
            testTransaction.setStatus(Transaction.TransactionStatus.COMPENSATING);

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(testTransaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

            sagaOrchestrator.handleCompensateFailure(event);

            verify(transactionService).markIdempotencyComplete(
                    eq("test-idemp-key"), eq(transactionId));
        }
    }
}
