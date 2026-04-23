package com.minibank.account.unit;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.entity.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountEntityTest {

    private UUID userId;
    private UUID accountId;

    @Nested
    @DisplayName("Account Entity Methods")
    class AccountEntityMethodsTests {

        @Test
        @DisplayName("Should activate account and set status to ACTIVE")
        void activate_SetsStatusToActive() {
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountNumber("MB1234567890")
                    .accountType(Account.AccountType.SAVINGS)
                    .status(Account.AccountStatus.PENDING)
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .build();

            account.activate();

            assertEquals(Account.AccountStatus.ACTIVE, account.getStatus());
        }

        @Test
        @DisplayName("Should suspend account and set status to SUSPENDED")
        void suspend_SetsStatusToSuspended() {
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountNumber("MB1234567890")
                    .accountType(Account.AccountType.SAVINGS)
                    .status(Account.AccountStatus.ACTIVE)
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .build();

            account.suspend();

            assertEquals(Account.AccountStatus.SUSPENDED, account.getStatus());
        }

        @Test
        @DisplayName("Should soft delete account and set deleted to true and status to CLOSED")
        void softDelete_SetsDeletedAndClosed() {
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountNumber("MB1234567890")
                    .accountType(Account.AccountType.SAVINGS)
                    .status(Account.AccountStatus.ACTIVE)
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .deleted(false)
                    .build();

            account.softDelete();

            assertTrue(account.getDeleted());
            assertEquals(Account.AccountStatus.CLOSED, account.getStatus());
        }

        @Test
        @DisplayName("Should return true when account status is ACTIVE")
        void isActive_ReturnsTrueForActiveStatus() {
            Account account = Account.builder()
                    .status(Account.AccountStatus.ACTIVE)
                    .build();

            assertTrue(account.isActive());
        }

        @Test
        @DisplayName("Should return false when account status is not ACTIVE")
        void isActive_ReturnsFalseForNonActiveStatus() {
            Account account = Account.builder()
                    .status(Account.AccountStatus.SUSPENDED)
                    .build();

            assertFalse(account.isActive());
        }

        @Test
        @DisplayName("Should return true when available balance is sufficient")
        void hasSufficientBalance_ReturnsTrue() {
            Account account = Account.builder()
                    .availableBalance(new BigDecimal("1000.00"))
                    .build();

            assertTrue(account.hasSufficientBalance(new BigDecimal("500.00")));
        }

        @Test
        @DisplayName("Should return false when available balance is insufficient")
        void hasSufficientBalance_ReturnsFalse() {
            Account account = Account.builder()
                    .availableBalance(new BigDecimal("100.00"))
                    .build();

            assertFalse(account.hasSufficientBalance(new BigDecimal("500.00")));
        }

        @Test
        @DisplayName("Should return true when available balance equals amount")
        void hasSufficientBalance_ReturnsTrueWhenEqual() {
            Account account = Account.builder()
                    .availableBalance(new BigDecimal("500.00"))
                    .build();

            assertTrue(account.hasSufficientBalance(new BigDecimal("500.00")));
        }
    }

    @Nested
    @DisplayName("AccountResponse.fromEntity")
    class AccountResponseFromEntityTests {

        @Test
        @DisplayName("Should map all fields from entity to response")
        void fromEntity_MapsAllFields() {
            LocalDateTime now = LocalDateTime.now();
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountNumber("MB1234567890")
                    .accountType(Account.AccountType.SAVINGS)
                    .balance(new BigDecimal("1000.00"))
                    .availableBalance(new BigDecimal("900.00"))
                    .currency("USD")
                    .status(Account.AccountStatus.ACTIVE)
                    .name("Test Account")
                    .description("Test Description")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            AccountResponse response = AccountResponse.fromEntity(account);

            assertEquals(account.getId(), response.getId());
            assertEquals(account.getUserId(), response.getUserId());
            assertEquals(account.getAccountNumber(), response.getAccountNumber());
            assertEquals("SAVINGS", response.getAccountType());
            assertEquals(account.getBalance(), response.getBalance());
            assertEquals(account.getAvailableBalance(), response.getAvailableBalance());
            assertEquals(account.getCurrency(), response.getCurrency());
            assertEquals("ACTIVE", response.getStatus());
            assertEquals(account.getName(), response.getName());
            assertEquals(account.getDescription(), response.getDescription());
            assertEquals(account.getCreatedAt(), response.getCreatedAt());
            assertEquals(account.getUpdatedAt(), response.getUpdatedAt());
        }

        @Test
        @DisplayName("Should convert AccountType enum to string")
        void fromEntity_ConvertsEnumToString() {
            Account account1 = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountType(Account.AccountType.CHECKING)
                    .accountNumber("MB1111111111")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .status(Account.AccountStatus.PENDING)
                    .build();

            AccountResponse response1 = AccountResponse.fromEntity(account1);
            assertEquals("CHECKING", response1.getAccountType());

            Account account2 = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountType(Account.AccountType.BUSINESS)
                    .accountNumber("MB2222222222")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .status(Account.AccountStatus.PENDING)
                    .build();

            AccountResponse response2 = AccountResponse.fromEntity(account2);
            assertEquals("BUSINESS", response2.getAccountType());
        }

        @Test
        @DisplayName("Should convert AccountStatus enum to string")
        void fromEntity_ConvertsStatusToString() {
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountType(Account.AccountType.SAVINGS)
                    .accountNumber("MB3333333333")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .status(Account.AccountStatus.DORMANT)
                    .build();

            AccountResponse response = AccountResponse.fromEntity(account);
            assertEquals("DORMANT", response.getStatus());
        }

        @Test
        @DisplayName("Should handle null createdAt and updatedAt")
        void fromEntity_HandlesNullTimestamps() {
            Account account = Account.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .accountType(Account.AccountType.SAVINGS)
                    .accountNumber("MB4444444444")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .currency("TRY")
                    .status(Account.AccountStatus.ACTIVE)
                    .createdAt(null)
                    .updatedAt(null)
                    .build();

            AccountResponse response = AccountResponse.fromEntity(account);

            assertNull(response.getCreatedAt());
            assertNull(response.getUpdatedAt());
        }
    }
}