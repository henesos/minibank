package com.minibank.account.unit;

import com.minibank.account.controller.AccountController;
import com.minibank.account.dto.AccountCreateRequest;
import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccessDeniedException;
import com.minibank.account.exception.AccountServiceException;
import com.minibank.account.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AccountController.
 *
 * <p>Tests all REST endpoints with mocked AccountService and HttpServletRequest.
 * Covers authorization (X-User-ID extraction), ownership validation, and all CRUD
 * operations. No Spring context is loaded — pure Mockito-based unit tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @Mock
    private HttpServletRequest request;

    private UUID userId;
    private UUID accountId;
    private AccountResponse testAccountResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        testAccountResponse = AccountResponse.builder()
                .id(accountId)
                .userId(userId)
                .accountNumber("MB1234567890")
                .accountType("SAVINGS")
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .currency("TRY")
                .status("ACTIVE")
                .name("Test Account")
                .build();

        // Lenient stub for X-User-ID header — used by most tests but not all
        lenient().when(request.getHeader("X-User-ID")).thenReturn(userId.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Get My Accounts
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get My Accounts")
    class GetMyAccountsTests {

        @Test
        @DisplayName("Should return list of accounts for authenticated user")
        void getMyAccounts_Success() {
            // Arrange
            List<AccountResponse> accounts = List.of(testAccountResponse);
            when(accountService.getAccountsByUserId(userId)).thenReturn(accounts);

            // Act
            ResponseEntity<List<AccountResponse>> response = accountController.getMyAccounts(request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals(accountId, response.getBody().get(0).getId());
            verify(accountService).getAccountsByUserId(userId);
        }

        @Test
        @DisplayName("Should return empty list when user has no accounts")
        void getMyAccounts_EmptyList() {
            // Arrange
            when(accountService.getAccountsByUserId(userId)).thenReturn(Collections.emptyList());

            // Act
            ResponseEntity<List<AccountResponse>> response = accountController.getMyAccounts(request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
            verify(accountService).getAccountsByUserId(userId);
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void getMyAccounts_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
            verify(accountService, never()).getAccountsByUserId(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Create Account
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Account")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account and return 201 with response")
        void createAccount_Success() {
            // Arrange
            AccountCreateRequest createRequest = AccountCreateRequest.builder()
                    .userId(UUID.randomUUID()) // Will be overridden
                    .accountType("SAVINGS")
                    .initialDeposit(new BigDecimal("500.00"))
                    .name("New Account")
                    .currency("TRY")
                    .build();

            when(accountService.createAccount(any(AccountCreateRequest.class))).thenReturn(testAccountResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.createAccount(request, createRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(accountId, response.getBody().getId());

            // Verify that userId was overridden to match the header
            assertEquals(userId, createRequest.getUserId());

            verify(accountService).createAccount(any(AccountCreateRequest.class));
        }

        @Test
        @DisplayName("Should override userId in request body with X-User-ID header value")
        void createAccount_UserIdOverride() {
            // Arrange
            UUID differentUserId = UUID.randomUUID();
            AccountCreateRequest createRequest = AccountCreateRequest.builder()
                    .userId(differentUserId) // Different from header — should be overridden
                    .accountType("CHECKING")
                    .name("Hacked Account")
                    .build();

            AccountResponse overriddenResponse = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId) // Controller uses header value
                    .accountNumber("MB9999999999")
                    .accountType("CHECKING")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .status("PENDING")
                    .name("Hacked Account")
                    .build();

            when(accountService.createAccount(any(AccountCreateRequest.class))).thenReturn(overriddenResponse);

            // Act
            accountController.createAccount(request, createRequest);

            // Assert — userId must be overridden
            assertNotEquals(differentUserId, createRequest.getUserId());
            assertEquals(userId, createRequest.getUserId());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void createAccount_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);
            AccountCreateRequest createRequest = AccountCreateRequest.builder()
                    .accountType("SAVINGS")
                    .name("Test")
                    .build();

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.createAccount(request, createRequest));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
            verify(accountService, never()).createAccount(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Get Accounts By UserId
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Accounts By UserId")
    class GetAccountsByUserIdTests {

        @Test
        @DisplayName("Should return accounts when callerId matches path userId")
        void getAccountsByUserId_Success() {
            // Arrange
            List<AccountResponse> accounts = List.of(testAccountResponse);
            when(accountService.getAccountsByUserId(userId)).thenReturn(accounts);

            // Act
            ResponseEntity<List<AccountResponse>> response = accountController.getAccountsByUserId(request, userId);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            verify(accountService).getAccountsByUserId(userId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when callerId does not match path userId")
        void getAccountsByUserId_CallerIdNotMatch_ThrowsAccessDenied() {
            // Arrange
            UUID otherUserId = UUID.randomUUID();

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.getAccountsByUserId(request, otherUserId));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).getAccountsByUserId(any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void getAccountsByUserId_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getAccountsByUserId(request, userId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Get Account By Id
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Account By Id")
    class GetAccountByIdTests {

        @Test
        @DisplayName("Should return account by ID via getAccountByIdForUser")
        void getAccountById_Success() {
            // Arrange
            when(accountService.getAccountByIdForUser(accountId, userId)).thenReturn(testAccountResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.getAccountById(request, accountId);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(accountId, response.getBody().getId());
            assertEquals(userId, response.getBody().getUserId());
            assertEquals("MB1234567890", response.getBody().getAccountNumber());
            verify(accountService).getAccountByIdForUser(accountId, userId);
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void getAccountById_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getAccountById(request, accountId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
            verify(accountService, never()).getAccountByIdForUser(any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Get Account By Number
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Account By Number")
    class GetAccountByNumberTests {

        @Test
        @DisplayName("Should return account by account number via getAccountByNumberForUser")
        void getAccountByNumber_Success() {
            // Arrange
            String accountNumber = "MB1234567890";
            when(accountService.getAccountByNumberForUser(accountNumber, userId)).thenReturn(testAccountResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.getAccountByNumber(request, accountNumber);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(accountNumber, response.getBody().getAccountNumber());
            assertEquals(userId, response.getBody().getUserId());
            verify(accountService).getAccountByNumberForUser(accountNumber, userId);
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void getAccountByNumber_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getAccountByNumber(request, "MB1234567890"));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
            verify(accountService, never()).getAccountByNumberForUser(anyString(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Get Balance
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Balance")
    class GetBalanceTests {

        @Test
        @DisplayName("Should return balance and availableBalance for account owner")
        void getBalance_Success() {
            // Arrange
            BigDecimal balance = new BigDecimal("1500.00");
            BigDecimal availableBalance = new BigDecimal("1200.00");
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            when(accountService.getBalance(accountId)).thenReturn(balance);
            when(accountService.getAvailableBalance(accountId)).thenReturn(availableBalance);

            // Act
            ResponseEntity<AccountController.BalanceResponse> response = accountController.getBalance(request, accountId);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(accountId, response.getBody().getAccountId());
            assertEquals(balance, response.getBody().getBalance());
            assertEquals(availableBalance, response.getBody().getAvailableBalance());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).getBalance(accountId);
            verify(accountService).getAvailableBalance(accountId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void getBalance_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.getBalance(request, accountId));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService, never()).getBalance(any());
            verify(accountService, never()).getAvailableBalance(any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void getBalance_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getBalance(request, accountId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Deposit
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deposit")
    class DepositTests {

        @Test
        @DisplayName("Should deposit successfully and return updated account")
        void deposit_Success() {
            // Arrange
            BalanceUpdateRequest depositRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            AccountResponse updatedResponse = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId)
                    .accountNumber("MB1234567890")
                    .accountType("SAVINGS")
                    .balance(new BigDecimal("1500.00"))
                    .availableBalance(new BigDecimal("1500.00"))
                    .currency("TRY")
                    .status("ACTIVE")
                    .name("Test Account")
                    .build();

            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            when(accountService.deposit(eq(accountId), any(BalanceUpdateRequest.class))).thenReturn(updatedResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.deposit(request, accountId, depositRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(new BigDecimal("1500.00"), response.getBody().getBalance());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).deposit(eq(accountId), eq(depositRequest));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void deposit_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            BalanceUpdateRequest depositRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.deposit(request, accountId, depositRequest));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).deposit(any(), any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void deposit_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);
            BalanceUpdateRequest depositRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.deposit(request, accountId, depositRequest));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. Withdraw
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Withdraw")
    class WithdrawTests {

        @Test
        @DisplayName("Should withdraw successfully and return updated account")
        void withdraw_Success() {
            // Arrange
            BalanceUpdateRequest withdrawRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("300.00"))
                    .build();

            AccountResponse updatedResponse = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId)
                    .accountNumber("MB1234567890")
                    .accountType("SAVINGS")
                    .balance(new BigDecimal("700.00"))
                    .availableBalance(new BigDecimal("700.00"))
                    .currency("TRY")
                    .status("ACTIVE")
                    .name("Test Account")
                    .build();

            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            when(accountService.withdraw(eq(accountId), any(BalanceUpdateRequest.class))).thenReturn(updatedResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.withdraw(request, accountId, withdrawRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(new BigDecimal("700.00"), response.getBody().getBalance());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).withdraw(eq(accountId), eq(withdrawRequest));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void withdraw_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            BalanceUpdateRequest withdrawRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.withdraw(request, accountId, withdrawRequest));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).withdraw(any(), any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void withdraw_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);
            BalanceUpdateRequest withdrawRequest = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.withdraw(request, accountId, withdrawRequest));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. Activate Account
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Activate Account")
    class ActivateAccountTests {

        @Test
        @DisplayName("Should activate account and return updated account")
        void activateAccount_Success() {
            // Arrange
            AccountResponse activatedResponse = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId)
                    .accountNumber("MB1234567890")
                    .accountType("SAVINGS")
                    .balance(new BigDecimal("1000.00"))
                    .availableBalance(new BigDecimal("1000.00"))
                    .currency("TRY")
                    .status("ACTIVE")
                    .name("Test Account")
                    .build();

            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            when(accountService.activateAccount(accountId)).thenReturn(activatedResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.activateAccount(request, accountId);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACTIVE", response.getBody().getStatus());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).activateAccount(accountId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void activateAccount_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.activateAccount(request, accountId));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).activateAccount(any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void activateAccount_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.activateAccount(request, accountId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. Suspend Account
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Suspend Account")
    class SuspendAccountTests {

        @Test
        @DisplayName("Should suspend account and return updated account")
        void suspendAccount_Success() {
            // Arrange
            AccountResponse suspendedResponse = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId)
                    .accountNumber("MB1234567890")
                    .accountType("SAVINGS")
                    .balance(new BigDecimal("1000.00"))
                    .availableBalance(new BigDecimal("1000.00"))
                    .currency("TRY")
                    .status("SUSPENDED")
                    .name("Test Account")
                    .build();

            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            when(accountService.suspendAccount(accountId)).thenReturn(suspendedResponse);

            // Act
            ResponseEntity<AccountResponse> response = accountController.suspendAccount(request, accountId);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("SUSPENDED", response.getBody().getStatus());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).suspendAccount(accountId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void suspendAccount_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.suspendAccount(request, accountId));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).suspendAccount(any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void suspendAccount_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.suspendAccount(request, accountId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 11. Close Account
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Close Account")
    class CloseAccountTests {

        @Test
        @DisplayName("Should close account and return 204 No Content")
        void closeAccount_Success() {
            // Arrange
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(true);
            doNothing().when(accountService).closeAccount(accountId);

            // Act
            ResponseEntity<Void> response = accountController.closeAccount(request, accountId);

            // Assert
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            assertNull(response.getBody());
            verify(accountService).isAccountOwner(accountId, userId);
            verify(accountService).closeAccount(accountId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user does not own account")
        void closeAccount_OwnershipFail_ThrowsAccessDenied() {
            // Arrange
            when(accountService.isAccountOwner(accountId, userId)).thenReturn(false);

            // Act & Assert
            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> accountController.closeAccount(request, accountId));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
            assertEquals("ACCESS_DENIED", exception.getErrorCode());
            verify(accountService, never()).closeAccount(any());
        }

        @Test
        @DisplayName("Should throw AccountServiceException when X-User-ID header is missing")
        void closeAccount_MissingUserIdHeader_ThrowsException() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.closeAccount(request, accountId));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 12. Health
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Health")
    class HealthTests {

        @Test
        @DisplayName("Should return UP status with account-service name")
        void health_ReturnsUp() {
            // Act
            ResponseEntity<AccountController.HealthResponse> response = accountController.health();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("UP", response.getBody().getStatus());
            assertEquals("account-service", response.getBody().getService());
        }

        @Test
        @DisplayName("Should not require X-User-ID header")
        void health_NoAuthRequired() {
            // Act — no HttpServletRequest needed, no header extraction
            ResponseEntity<AccountController.HealthResponse> response = accountController.health();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            verifyNoInteractions(accountService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 13. Authorization - Missing/Invalid X-User-ID
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authorization - Missing/Invalid X-User-ID")
    class AuthorizationTests {

        @Test
        @DisplayName("Should throw UNAUTHORIZED with MISSING_USER_ID when header is null")
        void extractUserId_NullHeader_ThrowsMissingUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn(null);

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Missing X-User-ID header"));
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED with MISSING_USER_ID when header is blank")
        void extractUserId_BlankHeader_ThrowsMissingUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn("   ");

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED with MISSING_USER_ID when header is empty string")
        void extractUserId_EmptyHeader_ThrowsMissingUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn("");

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("MISSING_USER_ID", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED with INVALID_USER_ID when header is not a valid UUID")
        void extractUserId_InvalidUUID_ThrowsInvalidUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn("not-a-valid-uuid");

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("INVALID_USER_ID", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Invalid X-User-ID header format"));
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED with INVALID_USER_ID for partial UUID format")
        void extractUserId_PartialUUID_ThrowsInvalidUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn("abc-123-def");

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("INVALID_USER_ID", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED with INVALID_USER_ID for random text")
        void extractUserId_RandomText_ThrowsInvalidUserId() {
            // Arrange
            reset(request);
            when(request.getHeader("X-User-ID")).thenReturn("hello-world");

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountController.getMyAccounts(request));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertEquals("INVALID_USER_ID", exception.getErrorCode());
        }
    }
}
