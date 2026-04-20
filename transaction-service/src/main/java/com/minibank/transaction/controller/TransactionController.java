package com.minibank.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import com.minibank.transaction.dto.TransactionResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.service.TransactionService;

/**
 * Transaction Controller - REST API endpoints for money transfers.
 *
 * Base path: /api/v1/transactions
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

        String userIdHeader = request.getHeader("X-User-ID");

        if (userIdHeader == null || userIdHeader.isEmpty()) {
            log.warn("X-User-ID header missing");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(userIdHeader);
        log.debug("Get transactions for authenticated user: {}, page: {}, size: {}", userId, page, size);

        Page<TransactionResponse> transactions = transactionService
                .getTransactionsByUserIdPaginated(userId, page, size);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Initiate a new money transfer.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        log.info("Transfer request: from={}, to={}, amount={}",
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        TransactionResponse response = transactionService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get transaction by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable UUID id) {
        log.debug("Get transaction: {}", id);
        TransactionResponse response = transactionService.getTransactionById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transaction by saga ID.
     */
    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<TransactionResponse> getTransactionBySagaId(@PathVariable UUID sagaId) {
        log.debug("Get transaction by saga: {}", sagaId);
        TransactionResponse response = transactionService.getTransactionBySagaId(sagaId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all transactions for a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUserId(@PathVariable UUID userId) {
        log.debug("Get transactions for user: {}", userId);
        List<TransactionResponse> transactions = transactionService.getTransactionsByUserId(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get all transactions for an account.
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAccountId(@PathVariable UUID accountId) {
        log.debug("Get transactions for account: {}", accountId);
        List<TransactionResponse> transactions = transactionService.getTransactionsByAccountId(accountId);
        return ResponseEntity.ok(transactions);
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
