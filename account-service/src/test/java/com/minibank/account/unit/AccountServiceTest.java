package com.minibank.account.unit;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.exception.AccessDeniedException;
import com.minibank.account.exception.AccountNotFoundException;
import com.minibank.account.exception.AccountServiceException;
import com.minibank.account.exception.InactiveAccountException;
import com.minibank.account.exception.InsufficientBalanceException;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AccountService.
 * 
 * Tests balance operations with mocked repository.
 * Focus on atomic balance update behavior.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private UUID testAccountId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testAccountId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testAccount = Account.builder()
                .id(testAccountId)
                .userId(testUserId)
                .accountNumber("MB1234567890")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(Account.AccountStatus.ACTIVE)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Create Account Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Account")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account successfully")
        void createAccount_Success() {
            // Arrange
            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .name("Test Account")
                    .build();

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            // Act
            AccountResponse response = accountService.createAccount(request);

            // Assert
            assertNotNull(response);
            assertEquals(testUserId, response.getUserId());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should create account with initial deposit")
        void createAccount_WithInitialDeposit() {
            // Arrange
            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .initialDeposit(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
            when(accountRepository.addBalance(any(UUID.class), any(BigDecimal.class))).thenReturn(1);

            // Act
            AccountResponse response = accountService.createAccount(request);

            // Assert
            assertNotNull(response);
            verify(accountRepository).addBalance(any(UUID.class), eq(new BigDecimal("500.00")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Account Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Account")
    class GetAccountTests {

        @Test
        @DisplayName("Should return account when found by ID")
        void getAccountById_Success() {
            // Arrange
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            // Act
            AccountResponse response = accountService.getAccountById(testAccountId);

            // Assert
            assertNotNull(response);
            assertEquals(testAccountId, response.getId());
        }

        @Test
        @DisplayName("Should throw exception when account not found")
        void getAccountById_NotFound() {
            // Arrange
            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class, 
                () -> accountService.getAccountById(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Balance Tests - CRITICAL
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Balance Operations")
    class BalanceTests {

        @Test
        @DisplayName("Should get current balance from database")
        void getBalance_Success() {
            // Arrange
            when(accountRepository.getBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("1000.00")));

            // Act
            BigDecimal balance = accountService.getBalance(testAccountId);

            // Assert
            assertEquals(new BigDecimal("1000.00"), balance);
            verify(accountRepository).getBalanceById(testAccountId);
        }

        @Test
        @DisplayName("Should deposit successfully")
        void deposit_Success() {
            // Arrange
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.addBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(1);

            // Act
            AccountResponse response = accountService.deposit(testAccountId, request);

            // Assert
            assertNotNull(response);
            verify(accountRepository).addBalance(testAccountId, new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Should withdraw successfully with sufficient balance")
        void withdraw_Success() {
            // Arrange
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.deductBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(1);

            // Act
            AccountResponse response = accountService.withdraw(testAccountId, request);

            // Assert
            assertNotNull(response);
            verify(accountRepository).deductBalance(testAccountId, new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Should fail withdrawal with insufficient balance")
        void withdraw_InsufficientBalance() {
            // Arrange
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("2000.00"))  // More than available
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.deductBalance(testAccountId, new BigDecimal("2000.00"))).thenReturn(0);  // Failed
            when(accountRepository.getBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("1000.00")));

            // Act & Assert
            assertThrows(InsufficientBalanceException.class, 
                () -> accountService.withdraw(testAccountId, request));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transfer Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transfer Operations")
    class TransferTests {

        @Test
        @DisplayName("Should transfer successfully between accounts")
        void transfer_Success() {
            // Arrange
            UUID toAccountId = UUID.randomUUID();

            when(accountRepository.deductBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(1);
            when(accountRepository.addBalance(toAccountId, new BigDecimal("500.00"))).thenReturn(1);

            // Act
            boolean result = accountService.transfer(testAccountId, toAccountId, new BigDecimal("500.00"));

            // Assert
            assertTrue(result);
            verify(accountRepository).deductBalance(testAccountId, new BigDecimal("500.00"));
            verify(accountRepository).addBalance(toAccountId, new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Should compensate if add to destination fails")
        void transfer_Compensation() {
            // Arrange
            UUID toAccountId = UUID.randomUUID();

            when(accountRepository.deductBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(1);
            when(accountRepository.addBalance(toAccountId, new BigDecimal("500.00"))).thenReturn(0);  // Failed
            when(accountRepository.addBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(1);  // Compensation

            // Act
            boolean result = accountService.transfer(testAccountId, toAccountId, new BigDecimal("500.00"));

            // Assert
            assertFalse(result);
            // Verify: destination addBalance failed, then compensation to source
            verify(accountRepository).addBalance(toAccountId, new BigDecimal("500.00"));  // Destination attempt
            verify(accountRepository).addBalance(testAccountId, new BigDecimal("500.00"));  // Compensation
        }

        @Test
        @DisplayName("Should fail if source has insufficient balance")
        void transfer_InsufficientBalance() {
            // Arrange
            UUID toAccountId = UUID.randomUUID();

            when(accountRepository.deductBalance(testAccountId, new BigDecimal("2000.00"))).thenReturn(0);  // Failed

            // Act
            boolean result = accountService.transfer(testAccountId, toAccountId, new BigDecimal("2000.00"));

            // Assert
            assertFalse(result);
            verify(accountRepository, never()).addBalance(any(), any());  // Should not add
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Account Status Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Account Status")
    class AccountStatusTests {

        @Test
        @DisplayName("Should activate account")
        void activate_Success() {
            // Arrange
            testAccount.setStatus(Account.AccountStatus.PENDING);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            // Act
            AccountResponse response = accountService.activateAccount(testAccountId);

            // Assert
            assertNotNull(response);
            verify(accountRepository).save(argThat(acc -> 
                acc.getStatus() == Account.AccountStatus.ACTIVE
            ));
        }

        @Test
        @DisplayName("Should suspend account")
        void suspend_Success() {
            // Arrange
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            // Act
            AccountResponse response = accountService.suspendAccount(testAccountId);

            // Assert
            assertNotNull(response);
            verify(accountRepository).save(argThat(acc -> 
                acc.getStatus() == Account.AccountStatus.SUSPENDED
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Authorization Tests (S2 — Ownership Verification)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authorization — Ownership Checks")
    class AuthorizationTests {

        @Test
        @DisplayName("isAccountOwner returns true when account belongs to user")
        void isAccountOwner_True() {
            when(accountRepository.existsByIdAndUserId(testAccountId, testUserId)).thenReturn(true);
            assertTrue(accountService.isAccountOwner(testAccountId, testUserId));
        }

        @Test
        @DisplayName("isAccountOwner returns false when account belongs to another user")
        void isAccountOwner_False() {
            UUID otherUserId = UUID.randomUUID();
            when(accountRepository.existsByIdAndUserId(testAccountId, otherUserId)).thenReturn(false);
            assertFalse(accountService.isAccountOwner(testAccountId, otherUserId));
        }

        @Test
        @DisplayName("getAccountByIdForUser returns account when owner matches")
        void getAccountByIdForUser_Owner_Success() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            AccountResponse response = accountService.getAccountByIdForUser(testAccountId, testUserId);

            assertNotNull(response);
            assertEquals(testAccountId, response.getId());
            assertEquals(testUserId, response.getUserId());
        }

        @Test
        @DisplayName("getAccountByIdForUser throws AccessDeniedException when non-owner")
        void getAccountByIdForUser_NonOwner_ThrowsAccessDenied() {
            UUID otherUserId = UUID.randomUUID();
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThrows(AccessDeniedException.class,
                    () -> accountService.getAccountByIdForUser(testAccountId, otherUserId));
        }

        @Test
        @DisplayName("getAccountByIdForUser throws AccountNotFoundException when account missing")
        void getAccountByIdForUser_NotFound_ThrowsNotFound() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> accountService.getAccountByIdForUser(testAccountId, testUserId));
        }

        @Test
        @DisplayName("getAccountByNumberForUser returns account when owner matches")
        void getAccountByNumberForUser_Owner_Success() {
            when(accountRepository.findByAccountNumber("MB1234567890")).thenReturn(Optional.of(testAccount));

            AccountResponse response = accountService.getAccountByNumberForUser("MB1234567890", testUserId);

            assertNotNull(response);
            assertEquals("MB1234567890", response.getAccountNumber());
        }

        @Test
        @DisplayName("getAccountByNumberForUser throws AccessDeniedException when non-owner")
        void getAccountByNumberForUser_NonOwner_ThrowsAccessDenied() {
            UUID otherUserId = UUID.randomUUID();
            when(accountRepository.findByAccountNumber("MB1234567890")).thenReturn(Optional.of(testAccount));

            assertThrows(AccessDeniedException.class,
                    () -> accountService.getAccountByNumberForUser("MB1234567890", otherUserId));
        }

        @Test
        @DisplayName("validateAccountOwnership returns account when owner and active")
        void validateAccountOwnership_ActiveOwner_Success() {
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            Account result = accountService.validateAccountOwnership(testAccountId, testUserId);

            assertNotNull(result);
            assertEquals(testAccountId, result.getId());
        }

        @Test
        @DisplayName("validateAccountOwnership throws AccessDeniedException when non-owner")
        void validateAccountOwnership_NonOwner_ThrowsAccessDenied() {
            UUID otherUserId = UUID.randomUUID();
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThrows(AccessDeniedException.class,
                    () -> accountService.validateAccountOwnership(testAccountId, otherUserId));
        }

        @Test
        @DisplayName("validateAccountOwnership throws InactiveAccountException when account suspended")
        void validateAccountOwnership_Suspended_ThrowsInactive() {
            testAccount.setStatus(Account.AccountStatus.SUSPENDED);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            assertThrows(InactiveAccountException.class,
                    () -> accountService.validateAccountOwnership(testAccountId, testUserId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Account Number Generation Tests (S3 — Secure Generation + Luhn)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Account Number Generation")
    class AccountNumberGenerationTests {

        @Test
        @DisplayName("Generated number should be 13 characters (MB + 11 digits)")
        void accountNumber_Format() {
            // Arrange — no collision
            when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account acc = invocation.getArgument(0);
                acc.setId(testAccountId);
                return acc;
            });

            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .build();

            // Act
            AccountResponse response = accountService.createAccount(request);

            // Assert — format: MB + 11 digits = 13 characters
            assertNotNull(response);
            String accountNumber = response.getAccountNumber();
            assertEquals(13, accountNumber.length(),
                    "Account number must be 13 characters (MB + 10 base + 1 check digit)");
            assertTrue(accountNumber.startsWith("MB"),
                    "Account number must start with 'MB'");
            assertTrue(accountNumber.substring(2).matches("\\d{11}"),
                    "Account number must have exactly 11 digits after 'MB' prefix");
        }

        @Test
        @DisplayName("Generated number should pass Luhn check")
        void accountNumber_LuhnValid() {
            // Arrange — no collision
            when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account acc = invocation.getArgument(0);
                acc.setId(testAccountId);
                return acc;
            });

            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .build();

            // Act
            AccountResponse response = accountService.createAccount(request);

            // Assert — generated number must pass Luhn validation
            assertNotNull(response);
            assertTrue(accountService.isValidAccountNumber(response.getAccountNumber()),
                    "Generated account number must pass Luhn checksum validation");
        }

        @Test
        @DisplayName("Should retry on collision and succeed")
        void accountNumber_CollisionRetry() {
            // Arrange — 1st attempt: collision, 2nd attempt: success
            when(accountRepository.findByAccountNumber(anyString()))
                    .thenReturn(Optional.of(testAccount))   // 1st attempt: number exists
                    .thenReturn(Optional.empty());          // 2nd attempt: unique
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account acc = invocation.getArgument(0);
                acc.setId(testAccountId);
                return acc;
            });

            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .build();

            // Act
            AccountResponse response = accountService.createAccount(request);

            // Assert — should have retried once and succeeded
            assertNotNull(response);
            verify(accountRepository, times(2)).findByAccountNumber(anyString());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should throw after 5 collisions")
        void accountNumber_MaxRetriesExceeded() {
            // Arrange — all 5 attempts result in collision
            when(accountRepository.findByAccountNumber(anyString()))
                    .thenReturn(Optional.of(testAccount));

            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(testUserId)
                    .accountType("SAVINGS")
                    .build();

            // Act & Assert
            AccountServiceException exception = assertThrows(AccountServiceException.class,
                    () -> accountService.createAccount(request),
                    "Should throw AccountServiceException after 5 failed attempts");

            assertEquals("ACCOUNT_NUMBER_GENERATION_FAILED", exception.getErrorCode());
            assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
            verify(accountRepository, times(5)).findByAccountNumber(anyString());
            // save should never be called since generation failed
            verify(accountRepository, never()).save(any(Account.class));
        }
    }
}
