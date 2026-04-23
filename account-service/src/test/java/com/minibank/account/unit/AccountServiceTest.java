package com.minibank.account.unit;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
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
import java.util.Collections;
import java.util.List;
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

        @Test
        @DisplayName("Should throw exception when suspending non-existent account")
        void suspend_NotFound() {
            // Arrange
            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.suspendAccount(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Close Account Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Close Account")
    class CloseAccountTests {

        @Test
        @DisplayName("Should close account with zero balance")
        void closeAccount_Success() {
            // Arrange
            testAccount.setBalance(BigDecimal.ZERO);
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            // Act
            accountService.closeAccount(testAccountId);

            // Assert
            verify(accountRepository).save(argThat(acc ->
                acc.getStatus() == Account.AccountStatus.CLOSED
            ));
        }

        @Test
        @DisplayName("Should throw exception when closing account with balance")
        void closeAccount_HasBalance_ThrowsException() {
            // Arrange
            testAccount.setBalance(new BigDecimal("100.00"));
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThrows(AccountServiceException.class,
                () -> accountService.closeAccount(testAccountId));
        }

        @Test
        @DisplayName("Should throw exception when closing non-existent account")
        void closeAccount_NotFound() {
            // Arrange
            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.closeAccount(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Find Account Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Find Account")
    class FindAccountTests {

        @Test
        @DisplayName("Should find account by ID")
        void findAccountById_Success() {
            // Arrange
            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            // Act
            Account result = accountService.findAccountById(testAccountId);

            // Assert
            assertNotNull(result);
            assertEquals(testAccountId, result.getId());
        }

        @Test
        @DisplayName("Should throw exception when account not found by ID")
        void findAccountById_NotFound() {
            // Arrange
            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.findAccountById(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Should find account by account number")
        void findAccountByNumber_Success() {
            // Arrange
            String accountNumber = "MB1234567890";
            when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(testAccount));

            // Act
            Account result = accountService.findAccountByNumber(accountNumber);

            // Assert
            assertNotNull(result);
            assertEquals(accountNumber, result.getAccountNumber());
        }

        @Test
        @DisplayName("Should throw exception when account not found by number")
        void findAccountByNumber_NotFound() {
            // Arrange
            when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.findAccountByNumber("NOTFOUND123"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Account Owner Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Account Owner")
    class AccountOwnerTests {

        @Test
        @DisplayName("Should return true when user owns account")
        void isAccountOwner_True() {
            // Arrange
            when(accountRepository.existsByIdAndUserId(testAccountId, testUserId)).thenReturn(true);

            // Act
            boolean result = accountService.isAccountOwner(testAccountId, testUserId);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user does not own account")
        void isAccountOwner_False() {
            // Arrange
            UUID otherUserId = UUID.randomUUID();
            when(accountRepository.existsByIdAndUserId(testAccountId, otherUserId)).thenReturn(false);

            // Act
            boolean result = accountService.isAccountOwner(testAccountId, otherUserId);

            // Assert
            assertFalse(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inactive Account Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Inactive Account")
    class InactiveAccountTests {

        @Test
        @DisplayName("Should throw exception when depositing to inactive account")
        void deposit_InactiveAccount_ThrowsException() {
            // Arrange
            testAccount.setStatus(Account.AccountStatus.SUSPENDED);
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThrows(InactiveAccountException.class,
                () -> accountService.deposit(testAccountId, request));
        }

        @Test
        @DisplayName("Should throw exception when withdrawing from inactive account")
        void withdraw_InactiveAccount_ThrowsException() {
            // Arrange
            testAccount.setStatus(Account.AccountStatus.CLOSED);
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThrows(InactiveAccountException.class,
                () -> accountService.withdraw(testAccountId, request));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Available Balance Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Available Balance")
    class GetAvailableBalanceTests {

        @Test
        @DisplayName("Should return available balance from database")
        void getAvailableBalance_Success() {
            // Arrange
            when(accountRepository.getAvailableBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("900.00")));

            // Act
            BigDecimal availableBalance = accountService.getAvailableBalance(testAccountId);

            // Assert
            assertEquals(new BigDecimal("900.00"), availableBalance);
            verify(accountRepository).getAvailableBalanceById(testAccountId);
        }

        @Test
        @DisplayName("Should throw exception when account not found for available balance")
        void getAvailableBalance_NotFound() {
            // Arrange
            when(accountRepository.getAvailableBalanceById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.getAvailableBalance(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Account By Number Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Account By Number")
    class GetAccountByNumberTests {

        @Test
        @DisplayName("Should return account when found by account number")
        void getAccountByNumber_Success() {
            // Arrange
            String accountNumber = "MB1234567890";
            when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(testAccount));

            // Act
            AccountResponse response = accountService.getAccountByNumber(accountNumber);

            // Assert
            assertNotNull(response);
            assertEquals(accountNumber, response.getAccountNumber());
            verify(accountRepository).findByAccountNumber(accountNumber);
        }

        @Test
        @DisplayName("Should throw exception when account not found by number")
        void getAccountByNumber_NotFound() {
            // Arrange
            when(accountRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.getAccountByNumber("NOTFOUND123"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get Accounts By UserId Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Accounts By UserId")
    class GetAccountsByUserIdTests {

        @Test
        @DisplayName("Should return list of accounts for user")
        void getAccountsByUserId_Success() {
            // Arrange
            Account account2 = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .accountNumber("MB9876543210")
                    .accountType(Account.AccountType.CHECKING)
                    .balance(new BigDecimal("500.00"))
                    .availableBalance(new BigDecimal("500.00"))
                    .currency("TRY")
                    .status(Account.AccountStatus.ACTIVE)
                    .build();

            when(accountRepository.findByUserId(testUserId)).thenReturn(List.of(testAccount, account2));

            // Act
            List<AccountResponse> responses = accountService.getAccountsByUserId(testUserId);

            // Assert
            assertNotNull(responses);
            assertEquals(2, responses.size());
            verify(accountRepository).findByUserId(testUserId);
        }

        @Test
        @DisplayName("Should return empty list when user has no accounts")
        void getAccountsByUserId_EmptyList() {
            // Arrange
            when(accountRepository.findByUserId(testUserId)).thenReturn(Collections.emptyList());

            // Act
            List<AccountResponse> responses = accountService.getAccountsByUserId(testUserId);

            // Assert
            assertNotNull(responses);
            assertTrue(responses.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Deposit Error Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deposit Error Path")
    class DepositErrorPathTests {

        @Test
        @DisplayName("Should throw exception when addBalance returns 0")
        void deposit_AddBalanceFails_ThrowsException() {
            // Arrange
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
            when(accountRepository.addBalance(testAccountId, new BigDecimal("500.00"))).thenReturn(0);

            // Act & Assert
            assertThrows(AccountServiceException.class,
                () -> accountService.deposit(testAccountId, request));
        }

        @Test
        @DisplayName("Should throw exception when account not found for deposit")
        void deposit_AccountNotFound_ThrowsException() {
            // Arrange
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.deposit(UUID.randomUUID(), request));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Activate Account Error Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Activate Account Error Path")
    class ActivateAccountErrorPathTests {

        @Test
        @DisplayName("Should throw exception when account not found for activation")
        void activateAccount_NotFound() {
            // Arrange
            when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(AccountNotFoundException.class,
                () -> accountService.activateAccount(UUID.randomUUID()));
        }
    }
}
