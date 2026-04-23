package com.minibank.transaction.service;

import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransactionResponse;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.exception.DailyLimitExceededException;
import com.minibank.transaction.exception.DuplicateTransactionException;
import com.minibank.transaction.exception.TransactionNotFoundException;
import com.minibank.transaction.exception.TransactionServiceException;
import com.minibank.transaction.repository.TransactionRepository;
import com.minibank.transaction.saga.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Transaction Service - Business logic for money transfers.
 * 
 * Implements the Saga pattern with distributed idempotency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.transaction.max-daily-transfer:50000.00}")
    private BigDecimal maxDailyTransfer;

    @Value("${app.transaction.idempotency-ttl:86400}")
    private int idempotencyTtlSeconds;

    private static final String IDEMPOTENCY_KEY_PREFIX = "tx:idempotency:";
    private static final String IDEMPOTENCY_VALUE_PROCESSING = "PROCESSING";

    /**
     * Initiates a new money transfer.
     * 
     * IDEMPOTENCY FLOW:
     * 1. Check Redis for existing idempotency key
     * 2. If exists and processing, return existing transaction
     * 3. If exists and completed, return completed transaction
     * 4. If not exists, set key to PROCESSING and create transaction
     * 
     * @param request transfer request
     * @return transaction response
     */
    @Transactional
    public TransactionResponse initiateTransfer(TransferRequest request) {
        log.info("Initiating transfer: from={}, to={}, amount={}", 
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        String idempotencyKey = request.getIdempotencyKey();
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // STEP 1: Check idempotency key in Redis
        String existingValue = redisTemplate.opsForValue().get(redisKey);
        if (existingValue != null) {
            log.info("Idempotency key found in Redis: {}", idempotencyKey);
            return handleExistingIdempotencyKey(idempotencyKey, existingValue);
        }

        // STEP 2: Check database for idempotency key (double-check pattern)
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate transaction detected in DB: {}", idempotencyKey);
            Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new TransactionNotFoundException(idempotencyKey));
            return TransactionResponse.fromEntity(existing);
        }

        // STEP 3: Check daily limit — fromUserId is now @NotNull, cannot be bypassed
        if (request.getFromUserId() == null) {
            throw new TransactionServiceException(
                "fromUserId is required for daily limit validation",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "MISSING_FROM_USER_ID"
            );
        }
        checkDailyLimit(request.getFromUserId(), request.getAmount());

        // STEP 4: Validate accounts are different
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new TransactionServiceException(
                "Source and destination accounts cannot be the same",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "SAME_ACCOUNT"
            );
        }

        // STEP 5: Set idempotency key in Redis (atomic operation)
        boolean setSuccessful = Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                redisKey, 
                IDEMPOTENCY_VALUE_PROCESSING, 
                idempotencyTtlSeconds, 
                TimeUnit.SECONDS
            )
        );

        if (!setSuccessful) {
            log.warn("Race condition detected for idempotency key: {}", idempotencyKey);
            return handleExistingIdempotencyKey(idempotencyKey, 
                redisTemplate.opsForValue().get(redisKey));
        }

        // STEP 6: Create transaction
        Transaction transaction = Transaction.builder()
                .sagaId(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .fromUserId(request.getFromUserId())
                .toUserId(request.getToUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "TRY")
                .description(request.getDescription())
                .status(Transaction.TransactionStatus.PENDING)
                .retryCount(0)
                .build();

        transaction = transactionRepository.save(transaction);

        // STEP 7: Start the saga
        sagaOrchestrator.startSaga(transaction);

        log.info("Transfer initiated: transactionId={}, sagaId={}", 
                transaction.getId(), transaction.getSagaId());

        return TransactionResponse.fromEntity(transaction);
    }

    /**
     * Handles existing idempotency key.
     */
    private TransactionResponse handleExistingIdempotencyKey(String idempotencyKey, String value) {
        if (IDEMPOTENCY_VALUE_PROCESSING.equals(value)) {
            // Transaction is still being processed
            Transaction transaction = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new TransactionNotFoundException(idempotencyKey));
            return TransactionResponse.fromEntity(transaction);
        } else {
            // Transaction ID is stored as value - return completed transaction
            UUID transactionId = UUID.fromString(value);
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException(transactionId));
            return TransactionResponse.fromEntity(transaction);
        }
    }

    /**
     * Checks if the daily transfer limit would be exceeded.
     * 
     * FIXED: Includes all active transaction statuses (PENDING, PROCESSING, DEBITED, COMPLETED)
     * to prevent limit bypass via concurrent pending transactions.
     */
    private void checkDailyLimit(UUID userId, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal dailyTotal = transactionRepository.getDailyTransferTotal(userId, startOfDay);
        
        BigDecimal newTotal = dailyTotal.add(amount);
        
        if (newTotal.compareTo(maxDailyTransfer) > 0) {
            throw new DailyLimitExceededException(amount, maxDailyTransfer, dailyTotal);
        }
    }

    /**
     * Gets a transaction by ID.
     * 
     * @param id transaction ID
     * @return transaction response
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return TransactionResponse.fromEntity(transaction);
    }

    /**
     * Finds a transaction entity by ID (for ownership checks).
     * 
     * @param id transaction ID
     * @return transaction entity
     */
    @Transactional(readOnly = true)
    public Transaction findTransactionById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    /**
     * Finds a transaction entity by saga ID (for ownership checks).
     * 
     * @param sagaId saga correlation ID
     * @return transaction entity
     */
    @Transactional(readOnly = true)
    public Transaction findTransactionBySagaId(UUID sagaId) {
        return transactionRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new TransactionNotFoundException(sagaId));
    }

    /**
     * Gets a transaction by saga ID.
     * 
     * @param sagaId saga correlation ID
     * @return transaction response
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionBySagaId(UUID sagaId) {
        Transaction transaction = transactionRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new TransactionNotFoundException(sagaId));
        return TransactionResponse.fromEntity(transaction);
    }

    /**
     * Gets all transactions for a user.
     * 
     * @param userId user ID
     * @return list of transactions
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByUserId(UUID userId) {
        return transactionRepository.findByUserId(userId).stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gets all transactions for a user with pagination.
     * 
     * @param userId user ID
     * @param page page number (0-indexed)
     * @param size page size
     * @return page of transactions
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByUserIdPaginated(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> transactionPage = transactionRepository.findByUserIdPaginated(userId, pageable);
        return transactionPage.map(TransactionResponse::fromEntity);
    }

    /**
     * Gets all transactions for an account.
     * 
     * @param accountId account ID
     * @return list of transactions
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccountId(UUID accountId) {
        List<Transaction> asSource = transactionRepository.findByFromAccountId(accountId);
        List<Transaction> asDestination = transactionRepository.findByToAccountId(accountId);
        
        asSource.addAll(asDestination);
        
        return asSource.stream()
                .distinct()
                .map(TransactionResponse::fromEntity)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Updates the Redis idempotency key with the completed transaction ID.
     * Called by SagaOrchestrator when saga completes successfully or with compensation.
     * 
     * @param idempotencyKey the idempotency key to mark as complete
     * @param transactionId the completed transaction ID
     */
    public void markIdempotencyComplete(String idempotencyKey, UUID transactionId) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, transactionId.toString(), 
                idempotencyTtlSeconds, TimeUnit.SECONDS);
        log.info("Idempotency key marked complete: key={}, transactionId={}", idempotencyKey, transactionId);
    }
}
