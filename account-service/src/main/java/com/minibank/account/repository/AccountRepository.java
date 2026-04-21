package com.minibank.account.repository;

import com.minibank.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Repository for database operations.
 * 
 * CRITICAL: Balance operations use atomic SQL updates!
 * 
 * Pattern: Optimistic Locking with @Version + Atomic Updates
 * - Regular operations: Use @Version for conflict detection
 * - Balance updates: Use atomic SQL (single UPDATE with WHERE condition)
 * 
 * NEVER do: Read balance → Modify in memory → Save
 * ALWAYS do: Single atomic UPDATE with condition
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Finds all accounts for a user.
     * 
     * @param userId the user ID
     * @return list of accounts
     */
    List<Account> findByUserId(UUID userId);

    /**
     * Finds an account by account number.
     * 
     * @param accountNumber the account number
     * @return Optional containing the account if found
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Finds active accounts for a user.
     * 
     * @param userId the user ID
     * @return list of active accounts
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);

    /**
     * Finds an account by ID with PESSIMISTIC WRITE lock.
     * Use this for operations that will modify the account.
     * 
     * @param id the account ID
     * @return Optional containing the locked account
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);

    /**
     * ATOMIC BALANCE DEDUCTION
     * 
     * This is the CRITICAL method for withdrawing money.
     * It atomically checks balance AND deducts in a single SQL statement.
     * 
     * Returns: 
     * - 1 if successful (balance was sufficient)
     * - 0 if failed (insufficient balance or account not found)
     * 
     * @param id account ID
     * @param amount amount to deduct
     * @return number of rows affected (1 = success, 0 = failure)
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - :amount, " +
           "a.availableBalance = a.availableBalance - :amount " +
           "WHERE a.id = :id AND a.status = 'ACTIVE' " +
           "AND a.balance >= :amount AND a.availableBalance >= :amount")
    int deductBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * ATOMIC BALANCE ADDITION
     * 
     * Adds money to the account atomically.
     * 
     * @param id account ID
     * @param amount amount to add
     * @return number of rows affected
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :amount, " +
           "a.availableBalance = a.availableBalance + :amount " +
           "WHERE a.id = :id AND a.status = 'ACTIVE'")
    int addBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Gets the current balance for an account.
     * ALWAYS read balance from DB - NEVER from cache!
     * 
     * @param id account ID
     * @return current balance or null if not found
     */
    @Query("SELECT a.balance FROM Account a WHERE a.id = :id")
    Optional<BigDecimal> getBalanceById(@Param("id") UUID id);

    /**
     * Gets the available balance for an account.
     * Available balance = Balance - Reserved amount (for pending transactions)
     * 
     * @param id account ID
     * @return available balance or null if not found
     */
    @Query("SELECT a.availableBalance FROM Account a WHERE a.id = :id")
    Optional<BigDecimal> getAvailableBalanceById(@Param("id") UUID id);

    /**
     * Checks if an account exists for a user.
     * 
     * @param id account ID
     * @param userId user ID
     * @return true if account belongs to user
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Account a WHERE a.id = :id AND a.userId = :userId")
    boolean existsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Counts accounts by status.
     * 
     * @param status the status
     * @return number of accounts with the status
     */
    long countByStatus(Account.AccountStatus status);

    /**
     * Finds dormant accounts (no transactions for specified days).
     * 
     * @param threshold the date threshold
     * @return list of dormant accounts
     */
    @Query("SELECT a FROM Account a WHERE a.status = 'ACTIVE' AND a.updatedAt < :threshold")
    List<Account> findDormantAccounts(@Param("threshold") java.time.LocalDateTime threshold);

    /**
     * Locks funds (reduces available balance without reducing actual balance).
     * Used for pending transactions that need to reserve funds.
     * 
     * @param id account ID
     * @param amount amount to lock
     * @return rows affected
     */
    @Modifying
    @Query("UPDATE Account a SET a.availableBalance = a.availableBalance - :amount " +
           "WHERE a.id = :id AND a.status = 'ACTIVE' AND a.availableBalance >= :amount")
    int lockFunds(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * Unlocks funds (increases available balance).
     * Used when a pending transaction is cancelled.
     * 
     * @param id account ID
     * @param amount amount to unlock
     * @return rows affected
     */
    @Modifying
    @Query("UPDATE Account a SET a.availableBalance = a.availableBalance + :amount " +
           "WHERE a.id = :id")
    int unlockFunds(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
