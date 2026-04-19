package com.minibank.account.controller;

import com.minibank.account.dto.*;
import com.minibank.account.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Account Controller - REST API endpoints for account management.
 * 
 * Base path: /api/v1/accounts
 * 
 * Endpoints:
 * - POST / - Create new account
 * - GET /{id} - Get account by ID
 * - GET /number/{accountNumber} - Get account by number
 * - GET /user/{userId} - Get accounts by user
 * - GET /{id}/balance - Get current balance
 * - POST /{id}/deposit - Deposit money
 * - POST /{id}/withdraw - Withdraw money
 * - POST /{id}/activate - Activate account
 * - POST /{id}/suspend - Suspend account
 * - DELETE /{id} - Close account
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Get all accounts for the authenticated user.
     * Uses X-User-ID header from API Gateway (extracted from JWT).
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-ID");
        
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            log.warn("X-User-ID header missing");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UUID userId = UUID.fromString(userIdHeader);
        log.debug("Get accounts for authenticated user: {}", userId);
        
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Create a new account.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        log.info("Create account request for user: {}", request.getUserId());
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get account by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        log.debug("Get account request: {}", id);
        AccountResponse response = accountService.getAccountById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get account by account number.
     */
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        log.debug("Get account by number: {}", accountNumber);
        AccountResponse response = accountService.getAccountByNumber(accountNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all accounts for a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(@PathVariable UUID userId) {
        log.debug("Get accounts for user: {}", userId);
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get current balance - ALWAYS fresh from database.
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        log.debug("Get balance for account: {}", id);
        BigDecimal balance = accountService.getBalance(id);
        BigDecimal availableBalance = accountService.getAvailableBalance(id);
        return ResponseEntity.ok(BalanceResponse.builder()
                .accountId(id)
                .balance(balance)
                .availableBalance(availableBalance)
                .build());
    }

    /**
     * Deposit money to account.
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        log.info("Deposit request for account: {}, amount: {}", id, request.getAmount());
        AccountResponse response = accountService.deposit(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Withdraw money from account.
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        log.info("Withdraw request for account: {}, amount: {}", id, request.getAmount());
        AccountResponse response = accountService.withdraw(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Activate account.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AccountResponse> activateAccount(@PathVariable UUID id) {
        log.info("Activate account request: {}", id);
        AccountResponse response = accountService.activateAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Suspend account.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AccountResponse> suspendAccount(@PathVariable UUID id) {
        log.info("Suspend account request: {}", id);
        AccountResponse response = accountService.suspendAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Close account (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> closeAccount(@PathVariable UUID id) {
        log.info("Close account request: {}", id);
        accountService.closeAccount(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("account-service")
                .build());
    }

    /**
     * DTO for balance response.
     */
    @lombok.Data
    @lombok.Builder
    public static class BalanceResponse {
        private UUID accountId;
        private BigDecimal balance;
        private BigDecimal availableBalance;
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
