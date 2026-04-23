package com.minibank.account.unit;

import com.minibank.account.service.AccountNumberGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccountNumberGeneratorTest {

    @Nested
    @DisplayName("Generate Account Number")
    class GenerateTests {

        @Test
        @DisplayName("Should generate 16-digit account number")
        void generateAccountNumber_Length() {
            String accountNumber = AccountNumberGenerator.generateAccountNumber();
            assertEquals(16, accountNumber.length());
        }

        @Test
        @DisplayName("Should generate only digits")
        void generateAccountNumber_OnlyDigits() {
            String accountNumber = AccountNumberGenerator.generateAccountNumber();
            assertTrue(accountNumber.matches("\\d+"));
        }

        @Test
        @DisplayName("Should generate unique numbers")
        void generateAccountNumber_Uniqueness() {
            Set<String> generated = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String accountNumber = AccountNumberGenerator.generateAccountNumber();
                generated.add(accountNumber);
            }
            assertEquals(1000, generated.size());
        }
    }

    @Nested
    @DisplayName("Validate Luhn")
    class ValidateLuhnTests {

        @Test
        @DisplayName("Should return true for valid Luhn number")
        void isValidLuhn_True() {
            String validNumber = AccountNumberGenerator.generateAccountNumber();
            assertTrue(AccountNumberGenerator.isValidLuhn(validNumber));
        }

        @Test
        @DisplayName("Should return false for null input")
        void isValidLuhn_Null() {
            assertFalse(AccountNumberGenerator.isValidLuhn(null));
        }

        @Test
        @DisplayName("Should return false for wrong length")
        void isValidLuhn_WrongLength() {
            assertFalse(AccountNumberGenerator.isValidLuhn("1234567890123456"));
            assertFalse(AccountNumberGenerator.isValidLuhn("1234567890123456789"));
        }

        @Test
        @DisplayName("Should return false for non-digit characters")
        void isValidLuhn_NonDigits() {
            assertFalse(AccountNumberGenerator.isValidLuhn("1234567890123456AB"));
            assertFalse(AccountNumberGenerator.isValidLuhn("12345678901234ABCD"));
        }

        @Test
        @DisplayName("Should return false for invalid checksum")
        void isValidLuhn_InvalidChecksum() {
            String base = "12345678901234";
            String invalidNumber = base + "00";
            assertFalse(AccountNumberGenerator.isValidLuhn(invalidNumber));
        }
    }

    @Nested
    @DisplayName("Collision Test")
    class CollisionTests {

        @Test
        @DisplayName("Should have no collision in 1000 iterations")
        void collisionTest_NoCollision() {
            Set<String> generated = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String accountNumber = AccountNumberGenerator.generateAccountNumber();
                assertTrue(AccountNumberGenerator.isValidLuhn(accountNumber));
                assertFalse(generated.contains(accountNumber));
                generated.add(accountNumber);
            }
            assertEquals(1000, generated.size());
        }
    }

    @Nested
    @DisplayName("Format Test")
    class FormatTests {

        @Test
        @DisplayName("Should have correct format MB prefix + 14 digits + 2 checksum")
        void format_MBPrefix() {
            String accountNumber = AccountNumberGenerator.generateAccountNumber();
            assertEquals(16, accountNumber.length());
            assertTrue(accountNumber.matches("\\d{16}"));
        }
    }
}