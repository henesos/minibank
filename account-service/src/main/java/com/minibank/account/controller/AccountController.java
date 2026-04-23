package com.minibank.account.controller;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.exception.AccountServiceException;
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
 * SECURITY: All endpoints enforce ownership via X-User-ID header
 * set by API Gateway after JWT verification. IDOR protection
 * ensures users can only access their own resources.
 * 
 * Endpoints:
 * - POST / - Create new account
 * - GET /{id} - Get account by ID
 * - GET /number/{accountNumber} - Get account by number (restricted)
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
        String userId = getAuthenticatedUserId(request);
        log.debug("Get accounts for authenticated user: {}", userId);
        
        List<AccountResponse> accounts = accountService.getAccountsByUserId(UUID.fromString(userId));
        return ResponseEntity.ok(accounts);
    }

    /**
     * Create a new account for the authenticated user.
     * Uses X-User-ID header from API Gateway (extracted from JWT).
     * Client must NOT send userId — it is always taken from the header.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            HttpServletRequest httpRequest,
            @Valid @RequestBody AccountCreateRequest request) {
        
        String userId = getAuthenticatedUserId(httpRequest);
        request.setUserId(UUID.fromString(userId)); // Enforce userId from header, not client
        
        log.info("Create account request for user: {}, type: {}", userId, request.getAccountType());
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get account by ID — IDOR protected.
     * Only the account owner can view their account details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            HttpServletRequest request,
            @PathVariable UUID id) {
        validateAccountOwnership(id, request);
        
        AccountResponse response = accountService.getAccountById(id);
        log.debug("Get account request: {} by user: {}", id, getAuthenticatedUserId(request));
        return ResponseEntity.ok(response);
    }

    /**
     * Get account by account number — restricted to account owner only.
     * Prevents enumeration of other users' accounts by account number.
     * Only returns ID — no balance or sensitive info.
     */
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountIdResponse> getAccountByNumber(
            HttpServletRequest request,
            @PathVariable String accountNumber) {
        String userId = getAuthenticatedUserId(request);
        log.debug("Get account by number: {} by user: {}", accountNumber, userId);
        
        Account account = accountService.findAccountByNumber(accountNumber);
        if (!account.getUserId().toString().equals(userId)) {
            log.warn("IDOR attempt: user {} tried to access account {} owned by user {}",
                    userId, account.getId(), account.getUserId());
            throw new AccountServiceException(
                "You do not have permission to access this account",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        return ResponseEntity.ok(AccountIdResponse.builder()
                .accountId(account.getId())
                .build());
    }

    /**
     * Get all accounts for a user — IDOR protected.
     * X-User-ID must match the path userId.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(
            HttpServletRequest request,
            @PathVariable UUID userId) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        
        // IDOR check: authenticated user can only query their own accounts
        if (!userId.toString().equals(authenticatedUserId)) {
            log.warn("IDOR attempt: user {} tried to access accounts of user {}", authenticatedUserId, userId);
            throw new AccountServiceException(
                "You can only view your own accounts",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        log.debug("Get accounts for user: {}", userId);
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get current balance - ALWAYS fresh from database. IDOR protected.
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            HttpServletRequest request,
            @PathVariable UUID id) {
        String userId = getAuthenticatedUserId(request);
        log.debug("Get balance for account: {} by user: {}", id, userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to access balance of account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        BigDecimal balance = accountService.getBalance(id);
        BigDecimal availableBalance = accountService.getAvailableBalance(id);
        return ResponseEntity.ok(BalanceResponse.builder()
                .accountId(id)
                .balance(balance)
                .availableBalance(availableBalance)
                .build());
    }

    /**
     * Deposit money to account — IDOR protected.
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            HttpServletRequest httpRequest,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        String userId = getAuthenticatedUserId(httpRequest);
        log.info("Deposit request for account: {}, amount: {} by user: {}", id, request.getAmount(), userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to deposit to account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        AccountResponse response = accountService.deposit(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Withdraw money from account — IDOR protected.
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(
            HttpServletRequest httpRequest,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest request) {
        String userId = getAuthenticatedUserId(httpRequest);
        log.info("Withdraw request for account: {}, amount: {} by user: {}", id, request.getAmount(), userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to withdraw from account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        AccountResponse response = accountService.withdraw(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Activate account — IDOR protected.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AccountResponse> activateAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {
        String userId = getAuthenticatedUserId(request);
        log.info("Activate account request: {} by user: {}", id, userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to activate account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        AccountResponse response = accountService.activateAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Suspend account — IDOR protected.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AccountResponse> suspendAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {
        String userId = getAuthenticatedUserId(request);
        log.info("Suspend account request: {} by user: {}", id, userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to suspend account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
        AccountResponse response = accountService.suspendAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Close account (soft delete) — IDOR protected.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> closeAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {
        String userId = getAuthenticatedUserId(request);
        log.info("Close account request: {} by user: {}", id, userId);
        
        if (!accountService.isAccountOwner(id, UUID.fromString(userId))) {
            log.warn("IDOR attempt: user {} tried to close account {}", userId, id);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
        
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

    // ============ SECURITY HELPER METHODS ============

    /**
     * Extracts authenticated user ID from X-User-ID header.
     * This header is set by API Gateway after JWT validation.
     * 
     * @param request HTTP request
     * @return user ID string
     * @throws AccountServiceException if header is missing (401 UNAUTHORIZED)
     */
    private String getAuthenticatedUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-ID");
        
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            log.warn("X-User-ID header missing — unauthorized access attempt");
            throw new AccountServiceException(
                "Missing X-User-ID header",
                HttpStatus.UNAUTHORIZED,
                "MISSING_USER_ID"
            );
        }
        
        try {
            UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-User-ID header format: {}", userIdHeader);
            throw new AccountServiceException(
                "Invalid X-User-ID header format",
                HttpStatus.UNAUTHORIZED,
                "INVALID_USER_ID"
            );
        }
        
        return userIdHeader;
    }

    /**
     * Validates that the authenticated user is the owner of the account using isAccountOwner service method.
     * Prevents IDOR (Insecure Direct Object Reference) attacks.
     * 
     * @param accountId the account ID to check ownership
     * @param request HTTP request containing X-User-ID header
     * @throws AccountServiceException if user does not own the account (403 FORBIDDEN)
     */
    private void validateAccountOwnership(UUID accountId, HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-ID");
        if (userIdHeader == null) {
            throw new AccountServiceException(
                "Authentication required: X-User-ID header missing",
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED"
            );
        }
        UUID userId = UUID.fromString(userIdHeader);
        if (!accountService.isAccountOwner(accountId, userId)) {
            log.warn("IDOR attempt: user {} tried to access account {}", userId, accountId);
            throw new AccountServiceException(
                "Access denied",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
    }

    // ============ INNER DTOs ============

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
