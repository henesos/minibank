package com.minibank.account.service;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.exception.AccessDeniedException;
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

    /**
     * Cryptographically strong random number generator for account number generation.
     * Class-level singleton to avoid entropy drain from repeated instantiation.
     * DO NOT use Math.random() or java.util.Random — they are predictable.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int BASE_DIGIT_COUNT = 10;
    private static final int MAX_GENERATION_RETRIES = 5;

    /**
     * Creates a new account for a user.
     * 
     * @param request account creation request
     * @return created account response
     */
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        log.info("Creating {} account for user: {}", request.getAccountType(), request.getUserId());

        // Generate unique account number
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
     * Generates a cryptographically secure, unique account number with Luhn checksum.
     *
     * <p>Format: MB + 10 random digits (base) + 1 Luhn check digit = 13 characters total.
     * Example: MB38472619405 (last digit '5' is the Luhn check digit)</p>
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Generate 10 random digits using {@link SecureRandom}</li>
     *   <li>Calculate Luhn check digit over the 10 base digits</li>
     *   <li>Append check digit to form 11-digit numeric part</li>
     *   <li>Check uniqueness in DB via {@link AccountRepository#findByAccountNumber(String)}</li>
     *   <li>On collision, retry up to {@link #MAX_GENERATION_RETRIES} times</li>
     * </ol></p>
     *
     * @return a unique, Luhn-valid account number (13 characters)
     * @throws AccountServiceException if unique number cannot be generated after max retries
     */
    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_GENERATION_RETRIES; attempt++) {
            // 1. Generate 10 random digits using SecureRandom
            StringBuilder sb = new StringBuilder(BASE_DIGIT_COUNT);
            for (int i = 0; i < BASE_DIGIT_COUNT; i++) {
                sb.append(SECURE_RANDOM.nextInt(10));
            }
            String baseDigits = sb.toString();

            // 2. Calculate Luhn check digit
            int checkDigit = calculateLuhnCheckDigit(baseDigits);

            // 3. Form full account number: MB + 10 digits + 1 check digit
            String accountNumber = "MB" + baseDigits + checkDigit;

            // 4. Check uniqueness in DB
            if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
                log.info("Generated account number: {} (attempt {})", accountNumber, attempt + 1);
                return accountNumber;
            }

            log.warn("Account number collision detected: {} (attempt {} of {})",
                    accountNumber, attempt + 1, MAX_GENERATION_RETRIES);
        }

        throw new AccountServiceException(
                "Failed to generate unique account number after " + MAX_GENERATION_RETRIES + " attempts",
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCOUNT_NUMBER_GENERATION_FAILED"
        );
    }

    /**
     * Calculates the Luhn check digit for a given base digit string.
     *
     * <p>The Luhn algorithm processes digits from right to left, doubling every second
     * digit (starting from the rightmost). If doubling results in a value greater than 9,
     * the digits are summed (e.g., 14 → 1+4 = 5). The check digit is chosen so that
     * the total sum of all digits (including check digit) is divisible by 10.</p>
     *
     * <p>Example: For base digits "7992739871", the check digit is 3,
     * making the full Luhn-valid number "79927398713".</p>
     *
     * @param digits the base digits (only numeric characters, e.g. "1234567890")
     * @return the Luhn check digit (0-9)
     */
    private int calculateLuhnCheckDigit(String digits) {
        int sum = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            int d = digits.charAt(i) - '0';
            // Position from the right in the full number (check digit is at position 0)
            int positionFromRight = len - i;
            if (positionFromRight % 2 == 1) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Validates whether the given account number has correct format and passes Luhn check.
     *
     * <p>Validation steps:
     * <ol>
     *   <li>Format check: starts with "MB", total 13 characters, remaining 11 characters are digits</li>
     *   <li>Luhn check: the 11-digit numeric portion must pass Luhn validation</li>
     * </ol></p>
     *
     * <p>This method is useful for input validation in transfers, queries, and external
     * integrations to detect typos and prevent funds from being sent to wrong accounts.</p>
     *
     * @param accountNumber the account number to validate
     * @return true if the account number is valid (correct format and Luhn checksum)
     */
    public boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() != 13 || !accountNumber.startsWith("MB")) {
            return false;
        }

        String digits = accountNumber.substring(2);
        // All 11 characters after "MB" must be digits
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }

        // Luhn validation on all 11 digits
        int sum = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            int d = digits.charAt(i) - '0';
            int positionFromRight = len - 1 - i; // 0-based from right
            if (positionFromRight % 2 == 1) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return sum % 10 == 0;
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

    /**
     * Gets an account by ID after verifying ownership.
     *
     * <p>Use this method when the caller is an authenticated user (not an internal service).
     * Verifies that the account belongs to the requesting user before returning data.</p>
     *
     * @param accountId account ID
     * @param userId the authenticated user's ID (from JWT / X-User-ID header)
     * @return account response
     * @throws AccountNotFoundException if account does not exist
     * @throws AccessDeniedException if user does not own the account
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByIdForUser(UUID accountId, UUID userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getUserId().equals(userId)) {
            throw new AccessDeniedException(accountId, userId);
        }
        return AccountResponse.fromEntity(account);
    }

    /**
     * Gets an account by account number after verifying ownership.
     *
     * <p>Use this method when the caller is an authenticated user (not an internal service).
     * Verifies that the account belongs to the requesting user before returning data.</p>
     *
     * @param accountNumber account number
     * @param userId the authenticated user's ID (from JWT / X-User-ID header)
     * @return account response
     * @throws AccountNotFoundException if account does not exist
     * @throws AccessDeniedException if user does not own the account
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumberForUser(String accountNumber, UUID userId) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        if (!account.getUserId().equals(userId)) {
            throw new AccessDeniedException(account.getId(), userId);
        }
        return AccountResponse.fromEntity(account);
    }

    /**
     * Validates that an account exists, is active, and belongs to the given user.
     *
     * <p>Throws the appropriate exception for each failure case:
     * <ul>
     *   <li>{@link AccountNotFoundException} — account does not exist</li>
     *   <li>{@link AccessDeniedException} — user does not own the account</li>
     *   <li>{@link InactiveAccountException} — account is not active</li>
     * </ul></p>
     *
     * @param accountId account ID
     * @param userId the authenticated user's ID
     * @return the account entity (for further processing in the calling method)
     */
    @Transactional(readOnly = true)
    public Account validateAccountOwnership(UUID accountId, UUID userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getUserId().equals(userId)) {
            throw new AccessDeniedException(accountId, userId);
        }
        if (!account.isActive()) {
            throw new InactiveAccountException(accountId, account.getStatus().name());
        }
        return account;
    }
}
