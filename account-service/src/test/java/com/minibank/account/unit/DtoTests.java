package com.minibank.account.unit;

import com.minibank.account.dto.AccountCreateRequest;
import com.minibank.account.dto.AccountIdResponse;
import com.minibank.account.dto.BalanceUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoTests {

    @Nested
    @DisplayName("AccountCreateRequest")
    class AccountCreateRequestTests {

        @Test
        @DisplayName("Should create with all fields")
        void create_WithAllFields() {
            UUID userId = UUID.randomUUID();
            AccountCreateRequest request = AccountCreateRequest.builder()
                    .userId(userId)
                    .accountType("SAVINGS")
                    .name("Test Account")
                    .description("Test Description")
                    .initialDeposit(new BigDecimal("1000.00"))
                    .currency("USD")
                    .build();

            assertEquals(userId, request.getUserId());
            assertEquals("SAVINGS", request.getAccountType());
            assertEquals("Test Account", request.getName());
            assertEquals("Test Description", request.getDescription());
            assertEquals(new BigDecimal("1000.00"), request.getInitialDeposit());
            assertEquals("USD", request.getCurrency());
        }

        @Test
        @DisplayName("Should create with minimal fields")
        void create_MinimalFields() {
            AccountCreateRequest request = new AccountCreateRequest();
            request.setAccountType("CHECKING");

            assertEquals("CHECKING", request.getAccountType());
            assertNull(request.getUserId());
            assertNull(request.getName());
            assertNull(request.getDescription());
            assertNull(request.getInitialDeposit());
            assertNull(request.getCurrency());
        }

        @Test
        @DisplayName("Should handle null initial deposit")
        void create_NullInitialDeposit() {
            AccountCreateRequest request = AccountCreateRequest.builder()
                    .accountType("SAVINGS")
                    .initialDeposit(null)
                    .build();

            assertNull(request.getInitialDeposit());
        }

        @Test
        @DisplayName("Should handle zero initial deposit")
        void create_ZeroInitialDeposit() {
            AccountCreateRequest request = AccountCreateRequest.builder()
                    .accountType("SAVINGS")
                    .initialDeposit(BigDecimal.ZERO)
                    .build();

            assertEquals(BigDecimal.ZERO, request.getInitialDeposit());
        }
    }

    @Nested
    @DisplayName("BalanceUpdateRequest")
    class BalanceUpdateRequestTests {

        @Test
        @DisplayName("Should create with amount")
        void create_WithAmount() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("500.00"))
                    .build();

            assertEquals(new BigDecimal("500.00"), request.getAmount());
        }

        @Test
        @DisplayName("Should handle negative amount")
        void create_NegativeAmount() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("-100.00"))
                    .build();

            assertEquals(new BigDecimal("-100.00"), request.getAmount());
        }

        @Test
        @DisplayName("Should handle null amount")
        void create_NullAmount() {
            BalanceUpdateRequest request = new BalanceUpdateRequest();
            request.setAmount(null);

            assertNull(request.getAmount());
        }

        @Test
        @DisplayName("Should handle large amounts")
        void create_LargeAmount() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(new BigDecimal("999999999.9999"))
                    .build();

            assertEquals(new BigDecimal("999999999.9999"), request.getAmount());
        }
    }

    @Nested
    @DisplayName("AccountIdResponse")
    class AccountIdResponseTests {

        @Test
        @DisplayName("Should create with accountId")
        void create_WithAccountId() {
            UUID accountId = UUID.randomUUID();
            AccountIdResponse response = AccountIdResponse.builder()
                    .accountId(accountId)
                    .build();

            assertEquals(accountId, response.getAccountId());
        }

        @Test
        @DisplayName("Should handle null accountId")
        void create_NullAccountId() {
            AccountIdResponse response = AccountIdResponse.builder()
                    .accountId(null)
                    .build();

            assertNull(response.getAccountId());
        }
    }
}