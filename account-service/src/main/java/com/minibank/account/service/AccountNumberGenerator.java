package com.minibank.account.service;

import java.security.SecureRandom;

public class AccountNumberGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RANDOM_DIGITS = 14;
    private static final int CHECKSUM_DIGITS = 2;
    private static final int TOTAL_LENGTH = RANDOM_DIGITS + CHECKSUM_DIGITS;

    public static String generateAccountNumber() {
        StringBuilder randomDigits = new StringBuilder(RANDOM_DIGITS);
        for (int i = 0; i < RANDOM_DIGITS; i++) {
            randomDigits.append(SECURE_RANDOM.nextInt(10));
        }

        int checkDigit = computeLuhnCheckDigit(randomDigits.toString());
        return randomDigits + String.format("%02d", checkDigit);
    }

    public static boolean isValidLuhn(String accountNumber) {
        if (accountNumber == null || accountNumber.length() != TOTAL_LENGTH) {
            return false;
        }

        if (!accountNumber.matches("\\d{" + TOTAL_LENGTH + "}")) {
            return false;
        }

        String base = accountNumber.substring(0, RANDOM_DIGITS);
        int storedCheckDigit = Integer.parseInt(accountNumber.substring(RANDOM_DIGITS));
        int computedCheckDigit = computeLuhnCheckDigit(base);

        return storedCheckDigit == computedCheckDigit;
    }

    private static int computeLuhnCheckDigit(String base) {
        int[] digits = new int[base.length()];
        for (int i = 0; i < base.length(); i++) {
            digits[i] = base.charAt(i) - '0';
        }

        int sum = 0;
        boolean doubleDigit = true;
        for (int i = digits.length - 1; i >= 0; i--) {
            int d = digits[i];
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }

        return (100 - (sum % 100)) % 100;
    }
}