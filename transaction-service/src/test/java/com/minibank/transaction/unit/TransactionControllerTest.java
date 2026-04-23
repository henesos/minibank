package com.minibank.transaction.unit;

import com.minibank.transaction.controller.TransactionController;
import com.minibank.transaction.dto.TransactionResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.exception.TransactionServiceException;
import com.minibank.transaction.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for TransactionController.
 *
 * Covers all REST endpoints:
 * - GET  /api/v1/transactions              — getMyTransactions (paginated, X-User-ID header)
 * - POST /api/v1/transactions              — initiateTransfer
 * - GET  /api/v1/transactions/{id}         — getTransactionById
 * - GET  /api/v1/transactions/saga/{sagaId}— getTransactionBySagaId
 * - GET  /api/v1/transactions/user/{userId}— getTransactionsByUserId
 * - GET  /api/v1/transactions/account/{accountId} — getTransactionsByAccountId
 * - GET  /api/v1/transactions/health       — health
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionController transactionController;

    private HttpServletRequest request;
    private UUID userId;
    private UUID transactionId;
    private UUID sagaId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID fromUserId;
    private UUID toUserId;
    private TransactionResponse sampleResponse;
    private TransferRequest sampleTransferRequest;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        fromUserId = userId;
        toUserId = UUID.randomUUID();

        sampleResponse = TransactionResponse.builder()
                .id(transactionId)
                .sagaId(sagaId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .type("TRANSFER")
                .status("COMPLETED")
                .sagaStep("COMPLETED")
                .description("Test transfer")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        sampleTransferRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .idempotencyKey("idem-key-" + UUID.randomUUID())
                .description("Test transfer")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get My Transactions
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get My Transactions")
    class GetMyTransactionsTests {

        @Test
        @DisplayName("Should return 200 with page of transactions when X-User-ID header is present")
        void getMyTransactions_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());

            Page<TransactionResponse> page = new PageImpl<>(List.of(sampleResponse));
            when(transactionService.getTransactionsByUserIdPaginated(eq(userId), eq(0), eq(20)))
                    .thenReturn(page);

            // Act
            ResponseEntity<Page<TransactionResponse>> response =
                    transactionController.getMyTransactions(request, 0, 20);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getContent().size());
            assertEquals(transactionId, response.getBody().getContent().get(0).getId());
            verify(transactionService).getTransactionsByUserIdPaginated(userId, 0, 20);
        }

        @Test
        @DisplayName("Should return 401 when X-User-ID header is missing")
        void getMyTransactions_MissingHeader_Returns401() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            TransactionServiceException exception = assertThrows(TransactionServiceException.class,
                    () -> transactionController.getMyTransactions(request, 0, 20));

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("UNAUTHORIZED", exception.getErrorCode());
            verify(transactionService, never()).getTransactionsByUserIdPaginated(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should return 401 when X-User-ID header is empty")
        void getMyTransactions_EmptyHeader_Returns401() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn("");

            // Act & Assert
            TransactionServiceException exception = assertThrows(TransactionServiceException.class,
                    () -> transactionController.getMyTransactions(request, 0, 20));

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("UNAUTHORIZED", exception.getErrorCode());
            verify(transactionService, never()).getTransactionsByUserIdPaginated(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should pass custom page and size parameters to service")
        void getMyTransactions_CustomPageParams() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());

            Page<TransactionResponse> emptyPage = new PageImpl<>(List.of());
            when(transactionService.getTransactionsByUserIdPaginated(eq(userId), eq(2), eq(50)))
                    .thenReturn(emptyPage);

            // Act
            ResponseEntity<Page<TransactionResponse>> response =
                    transactionController.getMyTransactions(request, 2, 50);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(transactionService).getTransactionsByUserIdPaginated(userId, 2, 50);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Initiate Transfer
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Initiate Transfer")
    class InitiateTransferTests {

        @Test
        @DisplayName("Should return 201 CREATED with transaction response on success")
        void initiateTransfer_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            when(transactionService.initiateTransfer(sampleTransferRequest))
                    .thenReturn(sampleResponse);

            // Act
            ResponseEntity<TransactionResponse> response =
                    transactionController.initiateTransfer(request, sampleTransferRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(transactionId, response.getBody().getId());
            assertEquals(new BigDecimal("150.00"), response.getBody().getAmount());
            assertEquals("COMPLETED", response.getBody().getStatus());
            verify(transactionService).initiateTransfer(sampleTransferRequest);
        }

        @Test
        @DisplayName("Should return correct fields in transfer response")
        void initiateTransfer_VerifyResponseFields() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            when(transactionService.initiateTransfer(sampleTransferRequest))
                    .thenReturn(sampleResponse);

            // Act
            ResponseEntity<TransactionResponse> response =
                    transactionController.initiateTransfer(request, sampleTransferRequest);

            // Assert
            TransactionResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(sagaId, body.getSagaId());
            assertEquals(fromAccountId, body.getFromAccountId());
            assertEquals(toAccountId, body.getToAccountId());
            assertEquals(fromUserId, body.getFromUserId());
            assertEquals(toUserId, body.getToUserId());
            assertEquals("USD", body.getCurrency());
            assertEquals("TRANSFER", body.getType());
            assertEquals("Test transfer", body.getDescription());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Transaction By Id
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Transaction By Id")
    class GetTransactionByIdTests {

        @Test
        @DisplayName("Should return 200 with transaction response")
        void getTransactionById_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            Transaction transaction = Transaction.builder()
                    .id(transactionId)
                    .sagaId(sagaId)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromUserId(userId)
                    .toUserId(toUserId)
                    .amount(new BigDecimal("150.00"))
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(transactionService.findTransactionById(transactionId)).thenReturn(transaction);

            // Act
            ResponseEntity<TransactionResponse> response =
                    transactionController.getTransactionById(transactionId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(transactionId, response.getBody().getId());
            assertEquals(sagaId, response.getBody().getSagaId());
            assertEquals(new BigDecimal("150.00"), response.getBody().getAmount());
            verify(transactionService).findTransactionById(transactionId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Transaction By Saga Id
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Transaction By Saga Id")
    class GetTransactionBySagaIdTests {

        @Test
        @DisplayName("Should return 200 with transaction response for valid saga ID")
        void getTransactionBySagaId_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            Transaction transaction = Transaction.builder()
                    .id(transactionId)
                    .sagaId(sagaId)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromUserId(userId)
                    .toUserId(toUserId)
                    .amount(new BigDecimal("150.00"))
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(transactionService.findTransactionBySagaId(sagaId)).thenReturn(transaction);

            // Act
            ResponseEntity<TransactionResponse> response =
                    transactionController.getTransactionBySagaId(sagaId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(transactionId, response.getBody().getId());
            assertEquals(sagaId, response.getBody().getSagaId());
            verify(transactionService).findTransactionBySagaId(sagaId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Transactions By User Id
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Transactions By User Id")
    class GetTransactionsByUserIdTests {

        @Test
        @DisplayName("Should return 200 with list of transactions")
        void getTransactionsByUserId_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            TransactionResponse anotherResponse = TransactionResponse.builder()
                    .id(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .fromUserId(userId)
                    .toUserId(toUserId)
                    .amount(new BigDecimal("200.00"))
                    .currency("EUR")
                    .type("TRANSFER")
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            List<TransactionResponse> transactions = Arrays.asList(sampleResponse, anotherResponse);
            when(transactionService.getTransactionsByUserId(userId)).thenReturn(transactions);

            // Act
            ResponseEntity<List<TransactionResponse>> response =
                    transactionController.getTransactionsByUserId(userId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            verify(transactionService).getTransactionsByUserId(userId);
        }

        @Test
        @DisplayName("Should return 200 with empty list when user has no transactions")
        void getTransactionsByUserId_EmptyList() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            when(transactionService.getTransactionsByUserId(userId)).thenReturn(List.of());

            // Act
            ResponseEntity<List<TransactionResponse>> response =
                    transactionController.getTransactionsByUserId(userId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
            verify(transactionService).getTransactionsByUserId(userId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Transactions By Account Id
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Transactions By Account Id")
    class GetTransactionsByAccountIdTests {

        @Test
        @DisplayName("Should return 200 with list of transactions for the account")
        void getTransactionsByAccountId_Success() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            TransactionResponse anotherResponse = TransactionResponse.builder()
                    .id(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .fromAccountId(fromAccountId)
                    .toAccountId(UUID.randomUUID())
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("300.00"))
                    .currency("USD")
                    .type("TRANSFER")
                    .status("COMPLETED")
                    .createdAt(LocalDateTime.now())
                    .build();

            List<TransactionResponse> transactions = Arrays.asList(sampleResponse, anotherResponse);
            when(transactionService.getTransactionsByAccountId(fromAccountId)).thenReturn(transactions);

            // Act
            ResponseEntity<List<TransactionResponse>> response =
                    transactionController.getTransactionsByAccountId(fromAccountId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            verify(transactionService).getTransactionsByAccountId(fromAccountId);
        }

        @Test
        @DisplayName("Should return 200 with empty list when account has no transactions")
        void getTransactionsByAccountId_EmptyList() {
            // Arrange
            when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
            when(transactionService.getTransactionsByAccountId(fromAccountId)).thenReturn(List.of());

            // Act
            ResponseEntity<List<TransactionResponse>> response =
                    transactionController.getTransactionsByAccountId(fromAccountId, request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
            verify(transactionService).getTransactionsByAccountId(fromAccountId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Health Check
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Health Check")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return UP with transaction-service name")
        void health_ReturnsUp() {
            // Act
            ResponseEntity<TransactionController.HealthResponse> response =
                    transactionController.health();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("UP", response.getBody().getStatus());
            assertEquals("transaction-service", response.getBody().getService());
        }
    }
}
