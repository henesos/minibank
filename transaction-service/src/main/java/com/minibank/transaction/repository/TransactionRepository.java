package com.minibank.transaction.repository;

import com.minibank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction Repository for database operations.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Finds a transaction by saga ID.
     * 
     * @param sagaId the saga correlation ID
     * @return Optional containing the transaction
     */
    Optional<Transaction> findBySagaId(UUID sagaId);

    /**
     * Finds a transaction by idempotency key.
     * Used for duplicate detection.
     * 
     * @param idempotencyKey the idempotency key
     * @return Optional containing the transaction
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Checks if a transaction exists for the given idempotency key.
     * 
     * @param idempotencyKey the idempotency key
     * @return true if exists
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all transactions for a source account.
     * 
     * @param fromAccountId the source account ID
     * @return list of transactions
     */
    List<Transaction> findByFromAccountId(UUID fromAccountId);

    /**
     * Finds all transactions for a destination account.
     * 
     * @param toAccountId the destination account ID
     * @return list of transactions
     */
    List<Transaction> findByToAccountId(UUID toAccountId);

    /**
     * Finds all transactions for a user (as sender or receiver).
     * 
     * @param userId the user ID
     * @return list of transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.fromUserId = :userId OR t.toUserId = :userId")
    List<Transaction> findByUserId(@Param("userId") UUID userId);

    /**
     * Finds transactions by status.
     * 
     * @param status the transaction status
     * @return list of transactions
     */
    List<Transaction> findByStatus(Transaction.TransactionStatus status);

    /**
     * Finds transactions that can be retried.
     * 
     * @param maxRetryCount maximum retry count
     * @return list of transactions eligible for retry
     */
    @Query("SELECT t FROM Transaction t WHERE t.status IN ('PROCESSING', 'FAILED') AND t.retryCount < :maxRetryCount")
    List<Transaction> findRetryableTransactions(@Param("maxRetryCount") int maxRetryCount);

    /**
     * Finds transactions created within a time range.
     * 
     * @param start start datetime
     * @param end end datetime
     * @return list of transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :start AND :end")
    List<Transaction> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Finds pending transactions older than a threshold.
     * Used for timeout detection.
     * 
     * @param threshold the datetime threshold
     * @return list of timed-out transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' AND t.createdAt < :threshold")
    List<Transaction> findTimedOutTransactions(@Param("threshold") LocalDateTime threshold);

    /**
     * Counts transactions by status.
     * 
     * @param status the status
     * @return count
     */
    long countByStatus(Transaction.TransactionStatus status);

    /**
     * Gets the total amount transferred by a user today.
     * Used for daily limit checks.
     * 
     * @param userId the user ID
     * @param startOfDay start of day
     * @return total amount
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.fromUserId = :userId AND t.status = 'COMPLETED' AND t.completedAt >= :startOfDay")
    java.math.BigDecimal getDailyTransferTotal(@Param("userId") UUID userId, @Param("startOfDay") LocalDateTime startOfDay);
}
