package com.minibank.account.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.minibank.account.dto.AccountCreateRequest;
import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccessDeniedException;
import com.minibank.account.exception.AccountServiceException;
import com.minibank.account.service.AccountService;

/**
 * Account Controller - REST API endpoints for account management.
 *
 * <p><strong>Authorization:</strong> All endpoints (except /health) require the
 * {@code X-User-ID} header, which is set by the API Gateway from the JWT token.
 * Ownership checks are performed on every endpoint that operates on a specific
 * account ID — a user can only access their own accounts.</p>
 *
 * <p><strong>Internal service calls (Kafka saga commands) are NOT affected</strong>
 * by these authorization checks. Saga commands flow through
 * {@link com.minibank.account.kafka.SagaCommandConsumer} directly to the service
 * layer, bypassing this controller entirely.</p>
 *
 * <p>Base path: /api/v1/accounts</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    private static final String X_USER_ID_HEADER = "X-User-ID";

    // ═══════════════════════════════════════════════════════════════════════
    // Collection Endpoints (already user-scoped via X-User-ID)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all accounts for the authenticated user.
     * Uses X-User-ID header from API Gateway — inherently scoped to the caller.
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(HttpServletRequest request) {
        UUID userId = extractUserId(request);
        log.debug("Get accounts for authenticated user: {}", userId);

        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Create a new account for the authenticated user.
     * Uses X-User-ID header — the userId in the request body is overridden by the header.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            HttpServletRequest httpRequest,
            @Valid @RequestBody AccountCreateRequest request) {

        UUID userId = extractUserId(httpRequest);
        request.setUserId(userId); // Override — trust the gateway, not the client

        log.info("Create account request for user: {}, type: {}", userId, request.getAccountType());
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all accounts for a specific user.
     * Authorization: X-User-ID must match the userId path variable.
     * A user can only view their own account list through this endpoint.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(
            HttpServletRequest request,
            @PathVariable UUID userId) {

        UUID callerId = extractUserId(request);
        if (!callerId.equals(userId)) {
            log.warn("User {} attempted to access accounts of user {}", callerId, userId);
            throw new AccessDeniedException(null, callerId);
        }

        log.debug("Get accounts for user: {}", userId);
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Single Account Endpoints (ownership check required)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get account by ID.
     * Authorization: caller must own the account.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        log.debug("Get account request: {} by user: {}", id, userId);

        AccountResponse response = accountService.getAccountByIdForUser(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get account by account number.
     * Authorization: caller must own the account.
     */
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(
            HttpServletRequest request,
            @PathVariable String accountNumber) {

        UUID userId = extractUserId(request);
        log.debug("Get account by number: {} by user: {}", accountNumber, userId);

        AccountResponse response = accountService.getAccountByNumberForUser(accountNumber, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current balance — ALWAYS fresh from database, NEVER cached.
     * Authorization: caller must own the account.
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        log.debug("Get balance for account: {} by user: {}", id, userId);

        validateOwnership(id, userId);

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
     * Authorization: caller must own the account.
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest body) {

        UUID userId = extractUserId(request);
        validateOwnership(id, userId);

        log.info("Deposit request for account: {}, amount: {}", id, body.getAmount());
        AccountResponse response = accountService.deposit(id, body);
        return ResponseEntity.ok(response);
    }

    /**
     * Withdraw money from account.
     * Authorization: caller must own the account.
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceUpdateRequest body) {

        UUID userId = extractUserId(request);
        validateOwnership(id, userId);

        log.info("Withdraw request for account: {}, amount: {}", id, body.getAmount());
        AccountResponse response = accountService.withdraw(id, body);
        return ResponseEntity.ok(response);
    }

    /**
     * Activate account.
     * Authorization: caller must own the account.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AccountResponse> activateAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        validateOwnership(id, userId);

        log.info("Activate account request: {} by user: {}", id, userId);
        AccountResponse response = accountService.activateAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Suspend account.
     * Authorization: caller must own the account.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AccountResponse> suspendAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        validateOwnership(id, userId);

        log.info("Suspend account request: {} by user: {}", id, userId);
        AccountResponse response = accountService.suspendAccount(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Close account (soft delete).
     * Authorization: caller must own the account.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> closeAccount(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        validateOwnership(id, userId);

        log.info("Close account request: {} by user: {}", id, userId);
        accountService.closeAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Health Check (no auth)
    // ═══════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════
    // Authorization Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extracts the authenticated user's ID from the X-User-ID header.
     *
     * <p>This header is set by the API Gateway after JWT validation.
     * If the header is missing, the request did not go through proper authentication.</p>
     *
     * @param request the HTTP request
     * @return the authenticated user's UUID
     * @throws AccountServiceException (401) if header is missing or empty
     */
    private UUID extractUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader(X_USER_ID_HEADER);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            log.warn("Missing {} header on request: {} {}",
                    X_USER_ID_HEADER, request.getMethod(), request.getRequestURI());
            throw new AccountServiceException(
                    "Missing X-User-ID header. Request must be authenticated.",
                    HttpStatus.UNAUTHORIZED,
                    "MISSING_USER_ID"
            );
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid {} header value: {}", X_USER_ID_HEADER, userIdHeader);
            throw new AccountServiceException(
                    "Invalid X-User-ID header format.",
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_USER_ID"
            );
        }
    }

    /**
     * Validates that the authenticated user owns the given account.
     *
     * <p>Uses {@link AccountService#isAccountOwner(UUID, UUID)} which queries
     * the database via {@code existsByIdAndUserId} — efficient single-query check.</p>
     *
     * @param accountId the account to check
     * @param userId    the authenticated user's ID
     * @throws AccessDeniedException if the user does not own the account
     */
    private void validateOwnership(UUID accountId, UUID userId) {
        if (!accountService.isAccountOwner(accountId, userId)) {
            log.warn("Ownership check failed — user {} does not own account {}", userId, accountId);
            throw new AccessDeniedException(accountId, userId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner DTO Classes
    // ═══════════════════════════════════════════════════════════════════════

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
