package com.minibank.transaction.dto;

import com.minibank.transaction.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for transaction response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID id;
    private UUID sagaId;
    private String idempotencyKey;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID fromUserId;
    private UUID toUserId;
    private BigDecimal amount;
    private String currency;
    private String type;  // TRANSFER, DEPOSIT, WITHDRAWAL
    private String status;
    private String sagaStep;
    private String description;
    private String failureReason;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    /**
     * Maps Transaction entity to TransactionResponse DTO.
     */
    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .sagaId(transaction.getSagaId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .fromUserId(transaction.getFromUserId())
                .toUserId(transaction.getToUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type("TRANSFER")  // All transactions in transaction-service are transfers
                .status(transaction.getStatus() != null ? transaction.getStatus().name() : null)
                .sagaStep(transaction.getSagaStep() != null ? transaction.getSagaStep().name() : null)
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .retryCount(transaction.getRetryCount())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}
