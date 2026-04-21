package com.minibank.account.service;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.exception.*;
import com.minibank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account Service - Business logic for account management.
 * 
 * CRITICAL RULES FOR BALANCE OPERATIONS:
 * 
 * 1. BALANCE IS NEVER CACHED
 *    - Always read from database
 *    - No @Cacheable on balance-related methods
 * 
 * 2. USE ATOMIC UPDATES
 *    - deductBalance() returns int (rows affected)
 *    - 0 = failed (insufficient balance)
 *    - 1 = success
 * 
 * 3. NO OPTIMISTIC LOCKING FOR BALANCE
 *    - @Version is for entity updates
 *    - Balance uses atomic SQL (UPDATE ... WHERE balance >= amount)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /** SecureRandom for cryptographically strong account number generation */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Maximum retry attempts for account number collision */
    private static final int MAX_ACCOUNT_NUMBER_RETRIES = 3;

    /**
     * Creates a new account for a user.
     * 
     * @param request account creation request
     * @return created account response
     */
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        log.info("Creating {} account for user: {}", request.getAccountType(), request.getUserId());

        // Generate unique account number with SecureRandom + Luhn check digit
        String accountNumber = generateAccountNumber();

        // Create account - ACTIVE by default for immediate use
        Account account = Account.builder()
                .userId(request.getUserId())
                .accountNumber(accountNumber)
                .accountType(Account.AccountType.valueOf(request.getAccountType().toUpperCase()))
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "TRY")
                .name(request.getName())
                .description(request.getDescription())
                .status(Account.AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);

        // If initial deposit, add it
        if (request.getInitialDeposit() != null && request.getInitialDeposit().compareTo(BigDecimal.ZERO) > 0) {
            int result = accountRepository.addBalance(account.getId(), request.getInitialDeposit());
            if (result > 0) {
                account.setBalance(request.getInitialDeposit());
                account.setAvailableBalance(request.getInitialDeposit());
            }
        }

        log.info("Account created: {} for user: {}", accountNumber, request.getUserId());
        return AccountResponse.fromEntity(account);
    }

    /**
     * Gets an account by ID.
     * Balance is always read from database - never cached.
     * 
     * @param id account ID
     * @return account response
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return AccountResponse.fromEntity(account);
    }

    /**
     * Finds an account entity by ID (for ownership checks).
     * 
     * @param id account ID
     * @return account entity
     */
    @Transactional(readOnly = true)
    public Account findAccountById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Finds an account entity by account number (for ownership checks).
     * 
     * @param accountNumber account number
     * @return account entity
     */
    @Transactional(readOnly = true)
    public Account findAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    /**
     * Gets an account by account number.
     * 
     * @param accountNumber account number
     * @return account response
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return AccountResponse.fromEntity(account);
    }

    /**
     * Gets all accounts for a user.
     * 
     * @param userId user ID
     * @return list of accounts
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gets current balance - ALWAYS from database, NEVER cached.
     * 
     * @param id account ID
     * @return current balance
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID id) {
        return accountRepository.getBalanceById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Gets available balance - ALWAYS from database, NEVER cached.
     * 
     * @param id account ID
     * @return available balance
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(UUID id) {
        return accountRepository.getAvailableBalanceById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * ATOMIC DEPOSIT - Adds money to account.
     * 
     * @param id account ID
     * @param request deposit request
     * @return updated account response
     */
    @Transactional
    public AccountResponse deposit(UUID id, BalanceUpdateRequest request) {
        log.info("Deposit request for account: {}, amount: {}", id, request.getAmount());

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.isActive()) {
            throw new InactiveAccountException(id, account.getStatus().name());
        }

        int result = accountRepository.addBalance(id, request.getAmount());
        if (result == 0) {
            throw new AccountServiceException("Deposit failed", 
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, 
                "DEPOSIT_FAILED");
        }

        log.info("Deposit successful for account: {}, amount: {}", id, request.getAmount());
        return getAccountById(id);
    }

    /**
     * ATOMIC WITHDRAWAL - Deducts money from account.
     * 
     * CRITICAL: Uses atomic SQL update with balance check.
     * Returns 0 if insufficient balance - no partial withdrawal.
     * 
     * @param id account ID
     * @param request withdrawal request
     * @return updated account response
     * @throws InsufficientBalanceException if not enough balance
     */
    @Transactional
    public AccountResponse withdraw(UUID id, BalanceUpdateRequest request) {
        log.info("Withdrawal request for account: {}, amount: {}", id, request.getAmount());

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.isActive()) {
            throw new InactiveAccountException(id, account.getStatus().name());
        }

        // ATOMIC UPDATE - checks balance AND deducts in one SQL
        int result = accountRepository.deductBalance(id, request.getAmount());
        
        if (result == 0) {
            // Get current balance for error message
            BigDecimal currentBalance = accountRepository.getBalanceById(id).orElse(BigDecimal.ZERO);
            throw new InsufficientBalanceException(id, request.getAmount(), currentBalance);
        }

        log.info("Withdrawal successful for account: {}, amount: {}", id, request.getAmount());
        return getAccountById(id);
    }

    /**
     * Transfers money between accounts.
     * Uses Saga Orchestrator for coordination.
     * 
     * @param fromAccountId source account
     * @param toAccountId destination account
     * @param amount transfer amount
     * @return transfer result
     */
    @Transactional
    public boolean transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        log.info("Transfer: {} -> {}, amount: {}", fromAccountId, toAccountId, amount);

        // Deduct from source (atomic)
        int deductResult = accountRepository.deductBalance(fromAccountId, amount);
        if (deductResult == 0) {
            log.warn("Transfer failed - insufficient balance in source: {}", fromAccountId);
            return false;
        }

        // Add to destination (atomic)
        int addResult = accountRepository.addBalance(toAccountId, amount);
        if (addResult == 0) {
            // COMPENSATION: Add money back to source
            log.error("Transfer failed - could not add to destination: {}. Compensating...", toAccountId);
            accountRepository.addBalance(fromAccountId, amount);
            return false;
        }

        log.info("Transfer successful: {} -> {}, amount: {}", fromAccountId, toAccountId, amount);
        return true;
    }

    /**
     * Activates an account.
     * 
     * @param id account ID
     * @return updated account response
     */
    @Transactional
    public AccountResponse activateAccount(UUID id) {
        log.info("Activating account: {}", id);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        account.activate();
        account = accountRepository.save(account);

        log.info("Account activated: {}", id);
        return AccountResponse.fromEntity(account);
    }

    /**
     * Suspends an account.
     * 
     * @param id account ID
     * @return updated account response
     */
    @Transactional
    public AccountResponse suspendAccount(UUID id) {
        log.info("Suspending account: {}", id);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        account.suspend();
        account = accountRepository.save(account);

        log.info("Account suspended: {}", id);
        return AccountResponse.fromEntity(account);
    }

    /**
     * Closes an account (soft delete).
     * 
     * @param id account ID
     */
    @Transactional
    public void closeAccount(UUID id) {
        log.info("Closing account: {}", id);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        // Check if balance is zero
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountServiceException(
                "Cannot close account with non-zero balance: " + account.getBalance(),
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "ACCOUNT_HAS_BALANCE"
            );
        }

        account.softDelete();
        accountRepository.save(account);

        log.info("Account closed: {}", id);
    }

    /**
     * Generates a unique account number using SecureRandom + Luhn check digit.
     * 
     * Format: MB + 10-digit SecureRandom number + 2-digit Luhn check digit
     * Example: MB123456789078 (MB + 1234567890 + 78)
     * 
     * Includes collision retry mechanism (max 3 attempts).
     * 
     * @return unique account number
     */
    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_RETRIES; attempt++) {
            // Generate 10 cryptographically random digits
            StringBuilder digits = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                digits.append(SECURE_RANDOM.nextInt(10));
            }

            // Compute 2-digit Luhn check digit from "MB" + 10 digits
            String baseNumber = "MB" + digits;
            int checkDigit = computeLuhnCheckDigit(baseNumber);
            String accountNumber = baseNumber + String.format("%02d", checkDigit);

            // Check for collision
            if (!accountRepository.findByAccountNumber(accountNumber).isPresent()) {
                log.debug("Generated unique account number: {} (attempt {})", accountNumber, attempt + 1);
                return accountNumber;
            }

            log.warn("Account number collision: {} (attempt {}), retrying...", accountNumber, attempt + 1);
        }

        // All retries exhausted — extremely unlikely with SecureRandom 10 digits
        log.error("Failed to generate unique account number after {} attempts", MAX_ACCOUNT_NUMBER_RETRIES);
        throw new AccountServiceException(
            "Failed to generate unique account number",
            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
            "ACCOUNT_NUMBER_GENERATION_FAILED"
        );
    }

    /**
     * Computes a 2-digit Luhn check digit for the given base string.
     * 
     * The Luhn algorithm is applied to the numeric characters of the input.
     * Non-numeric characters (e.g., 'M', 'B') are converted to their
     * Unicode code point modulo 10 to participate in the checksum.
     * 
     * @param base the base string (e.g., "MB1234567890")
     * @return 2-digit check value (0-99)
     */
    private int computeLuhnCheckDigit(String base) {
        // Extract digits: for non-numeric chars, use (codePoint % 10) as digit
        int[] digits = new int[base.length()];
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isDigit(c)) {
                digits[i] = c - '0';
            } else {
                // Map letters to digits: M=7 (77%10), B=6 (66%10)
                digits[i] = c % 10;
            }
        }

        // Standard Luhn algorithm — double every second digit from right
        int sum = 0;
        boolean doubleDigit = true; // Start with doubling the rightmost
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

        // Return 2-digit check: (100 - (sum % 100)) % 100
        // This ensures the check digit is in range 0-99
        return (100 - (sum % 100)) % 100;
    }

    /**
     * Checks if an account belongs to a user.
     * 
     * @param accountId account ID
     * @param userId user ID
     * @return true if account belongs to user
     */
    @Transactional(readOnly = true)
    public boolean isAccountOwner(UUID accountId, UUID userId) {
        return accountRepository.existsByIdAndUserId(accountId, userId);
    }
}
