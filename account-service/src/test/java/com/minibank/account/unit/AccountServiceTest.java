package com.minibank.account.unit;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.exception.AccountNotFoundException;
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
            when(accountRepository.getBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("1500.00")));
            when(accountRepository.getAvailableBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("1500.00")));

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
            when(accountRepository.getBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("500.00")));
            when(accountRepository.getAvailableBalanceById(testAccountId))
                    .thenReturn(Optional.of(new BigDecimal("500.00")));

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
            verify(accountRepository, times(2)).addBalance(testAccountId, new BigDecimal("500.00"));  // Once for compensation
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
}
