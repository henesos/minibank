package com.minibank.transaction.controller;

import com.minibank.transaction.dto.TransactionResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.exception.TransactionServiceException;
import com.minibank.transaction.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Transaction Controller - REST API endpoints for money transfers.
 * 
 * Base path: /api/v1/transactions
 * 
 * SECURITY: All endpoints enforce ownership via X-User-ID header
 * set by API Gateway after JWT verification. IDOR protection
 * ensures users can only access their own transactions.
 * 
 * Endpoints:
 * - POST / - Initiate new transfer
 * - GET /{id} - Get transaction by ID
 * - GET /saga/{sagaId} - Get transaction by saga ID
 * - GET /user/{userId} - Get transactions by user
 * - GET /account/{accountId} - Get transactions by account
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Get all transactions for the authenticated user with pagination.
     * Uses X-User-ID header from API Gateway (extracted from JWT).
     * 
     * @param request HTTP request to get X-User-ID header
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return page of transactions
     */
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String userId = getAuthenticatedUserId(request);
        log.debug("Get transactions for authenticated user: {}, page: {}, size: {}", userId, page, size);
        
        Page<TransactionResponse> transactions = transactionService.getTransactionsByUserIdPaginated(
                UUID.fromString(userId), page, size);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Initiate a new money transfer — IDOR protected.
     * fromUserId is always taken from X-User-ID header, not from the request body.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransfer(
            HttpServletRequest httpRequest,
            @Valid @RequestBody TransferRequest request) {
        String userId = getAuthenticatedUserId(httpRequest);
        
        // SECURITY: Enforce fromUserId from authenticated header, not from client
        request.setFromUserId(UUID.fromString(userId));
        
        log.info("Transfer request: from={}, to={}, amount={} by user: {}", 
                request.getFromAccountId(), request.getToAccountId(), request.getAmount(), userId);
        
        TransactionResponse response = transactionService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get transaction by ID — IDOR protected.
     * Only the sender or receiver can view the transaction.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable UUID id,
            HttpServletRequest request) {
        String userId = getAuthenticatedUserId(request);
        log.debug("Get transaction: {} by user: {}", id, userId);
        
        Transaction transaction = transactionService.findTransactionById(id);
        validateTransactionOwnership(transaction, userId);
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    /**
     * Get transaction by saga ID — IDOR protected.
     * Only the sender or receiver can view the transaction.
     */
    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<TransactionResponse> getTransactionBySagaId(
            @PathVariable UUID sagaId,
            HttpServletRequest request) {
        String userId = getAuthenticatedUserId(request);
        log.debug("Get transaction by saga: {} by user: {}", sagaId, userId);
        
        Transaction transaction = transactionService.findTransactionBySagaId(sagaId);
        validateTransactionOwnership(transaction, userId);
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    /**
     * Get all transactions for a user — IDOR protected.
     * X-User-ID must match the path userId.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUserId(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        
        // IDOR check: authenticated user can only query their own transactions
        if (!userId.toString().equals(authenticatedUserId)) {
            log.warn("IDOR attempt: user {} tried to access transactions of user {}", authenticatedUserId, userId);
            throw new TransactionServiceException(
                "You can only view your own transactions",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        log.debug("Get transactions for user: {}", userId);
        List<TransactionResponse> transactions = transactionService.getTransactionsByUserId(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get all transactions for an account — IDOR protected.
     * Filters transactions to only those where the authenticated user
     * is the sender or receiver, preventing unauthorized access to
     * other users' transactions on shared accounts.
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAccountId(
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        String userId = getAuthenticatedUserId(request);
        log.debug("Get transactions for account: {} by user: {}", accountId, userId);
        
        List<TransactionResponse> transactions = transactionService.getTransactionsByAccountId(accountId);
        
        // Filter: only return transactions where the authenticated user is sender or receiver
        List<TransactionResponse> filtered = transactions.stream()
                .filter(t -> userId.equals(t.getFromUserId() != null ? t.getFromUserId().toString() : null)
                          || userId.equals(t.getToUserId() != null ? t.getToUserId().toString() : null))
                .toList();
        
        return ResponseEntity.ok(filtered);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("transaction-service")
                .build());
    }

    // ============ SECURITY HELPER METHODS ============

    /**
     * Extracts authenticated user ID from X-User-ID header.
     * This header is set by API Gateway after JWT validation.
     * 
     * @param request HTTP request
     * @return user ID string
     * @throws TransactionServiceException if header is missing (401 UNAUTHORIZED)
     */
    private String getAuthenticatedUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-ID");
        
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            log.warn("X-User-ID header missing — unauthorized access attempt");
            throw new TransactionServiceException(
                "Authentication required: X-User-ID header missing",
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED"
            );
        }
        
        return userIdHeader;
    }

    /**
     * Validates that the authenticated user is a party to the transaction.
     * Only the sender (fromUserId) or receiver (toUserId) can view it.
     * Prevents IDOR (Insecure Direct Object Reference) attacks.
     * 
     * @param transaction the transaction to check ownership
     * @param userId the authenticated user ID from X-User-ID header
     * @throws TransactionServiceException if user is not a party to the transaction (403 FORBIDDEN)
     */
    private void validateTransactionOwnership(Transaction transaction, String userId) {
        boolean isSender = transaction.getFromUserId() != null 
                && transaction.getFromUserId().toString().equals(userId);
        boolean isReceiver = transaction.getToUserId() != null 
                && transaction.getToUserId().toString().equals(userId);
        
        if (!isSender && !isReceiver) {
            log.warn("IDOR attempt: user {} tried to access transaction {} owned by from={}, to={}",
                    userId, transaction.getId(), transaction.getFromUserId(), transaction.getToUserId());
            throw new TransactionServiceException(
                "You do not have permission to access this transaction",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
    }

    // ============ INNER DTOs ============

    /**
     * DTO for health check response.
     */
    @lombok.Data
    @lombok.Builder
    public static class HealthResponse {
        private String status;
        private String service;
    }
}
