package com.minibank.transaction.unit;

import com.minibank.transaction.dto.TransactionResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.exception.DailyLimitExceededException;
import com.minibank.transaction.exception.TransactionNotFoundException;
import com.minibank.transaction.repository.TransactionRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for TransactionService.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TransactionService transactionService;

    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID fromUserId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        fromUserId = UUID.randomUUID();
        idempotencyKey = "test-idempotency-key-" + UUID.randomUUID();

        // Set field values
        ReflectionTestUtils.setField(transactionService, "maxDailyTransfer", new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(transactionService, "idempotencyTtlSeconds", 86400);

        // Mock redis operations
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transfer Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transfer Operations")
    class TransferTests {

        @Test
        @DisplayName("Should initiate transfer successfully")
        void initiateTransfer_Success() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromUserId(fromUserId)
                    .amount(new BigDecimal("100.00"))
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(valueOperations.get(anyString())).thenReturn(null);
            when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
            when(transactionRepository.getDailyTransferTotal(eq(fromUserId), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.ZERO);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
                Transaction tx = inv.getArgument(0);
                ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(tx, "createdAt", LocalDateTime.now());
                return tx;
            });

            // Act
            TransactionResponse response = transactionService.initiateTransfer(request);

            // Assert
            assertNotNull(response);
            assertEquals(new BigDecimal("100.00"), response.getAmount());
            verify(transactionRepository).save(any(Transaction.class));
            verify(sagaOrchestrator).startSaga(any(Transaction.class));
        }

        @Test
        @DisplayName("Should reject transfer to same account")
        void initiateTransfer_SameAccount_ThrowsException() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromAccountId(fromAccountId)
                    .toAccountId(fromAccountId)  // Same account
                    .amount(new BigDecimal("100.00"))
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(valueOperations.get(anyString())).thenReturn(null);
            when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);

            // Act & Assert
            assertThrows(Exception.class, () -> transactionService.initiateTransfer(request));
        }

        @Test
        @DisplayName("Should detect duplicate transaction")
        void initiateTransfer_Duplicate_Detected() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(new BigDecimal("100.00"))
                    .idempotencyKey(idempotencyKey)
                    .build();

            Transaction existingTransaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .idempotencyKey(idempotencyKey)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(new BigDecimal("100.00"))
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(valueOperations.get(anyString())).thenReturn(null);
            when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true);
            when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(existingTransaction));

            // Act
            TransactionResponse response = transactionService.initiateTransfer(request);

            // Assert
            assertNotNull(response);
            assertEquals(existingTransaction.getId(), response.getId());
            verify(transactionRepository, never()).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Should reject transfer exceeding daily limit")
        void initiateTransfer_DailyLimitExceeded_ThrowsException() {
            // Arrange
            TransferRequest request = TransferRequest.builder()
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromUserId(fromUserId)
                    .amount(new BigDecimal("60000.00"))  // Over limit
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(valueOperations.get(anyString())).thenReturn(null);
            when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
            when(transactionRepository.getDailyTransferTotal(eq(fromUserId), any(LocalDateTime.class)))
                    .thenReturn(new BigDecimal("40000.00"));  // Already transferred 40k
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);

            // Act & Assert
            assertThrows(DailyLimitExceededException.class, 
                () -> transactionService.initiateTransfer(request));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Transaction Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Transaction")
    class GetTransactionTests {

        @Test
        @DisplayName("Should return transaction when found by ID")
        void getTransactionById_Success() {
            // Arrange
            UUID transactionId = UUID.randomUUID();
            Transaction transaction = Transaction.builder()
                    .id(transactionId)
                    .sagaId(UUID.randomUUID())
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(new BigDecimal("100.00"))
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

            // Act
            TransactionResponse response = transactionService.getTransactionById(transactionId);

            // Assert
            assertNotNull(response);
            assertEquals(transactionId, response.getId());
        }

        @Test
        @DisplayName("Should throw exception when transaction not found")
        void getTransactionById_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(transactionRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(TransactionNotFoundException.class, 
                () -> transactionService.getTransactionById(nonExistentId));
        }

        @Test
        @DisplayName("Should return transaction by saga ID")
        void getTransactionBySagaId_Success() {
            // Arrange
            UUID sagaId = UUID.randomUUID();
            Transaction transaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .sagaId(sagaId)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(new BigDecimal("100.00"))
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findBySagaId(sagaId)).thenReturn(Optional.of(transaction));

            // Act
            TransactionResponse response = transactionService.getTransactionBySagaId(sagaId);

            // Assert
            assertNotNull(response);
            assertEquals(sagaId, response.getSagaId());
        }
    }
}
