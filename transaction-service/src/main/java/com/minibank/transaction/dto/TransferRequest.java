package com.minibank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for transfer request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "Source account ID is required")
    private UUID fromAccountId;

    @NotNull(message = "Destination account ID is required")
    private UUID toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * Idempotency key for duplicate prevention.
     * Client should generate a unique key for each transfer.
     */
    @NotNull(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    /**
     * User ID initiating the transfer (sender).
     * SECURITY: @NotNull ensures daily limit check cannot be bypassed
     * by omitting this field. Set from X-User-ID header by controller.
     */
    @NotNull(message = "Source user ID is required for daily limit validation")
    private UUID fromUserId;

    private UUID toUserId;

    private String currency;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
