package com.minibank.account.dto;

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
 * DTO for account creation request.
 *
 * Note: userId is optional in request body as it will be extracted
 * from X-User-ID header by the API Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreateRequest {

    /**
     * User ID - optional in request, set from X-User-ID header.
     */
    private UUID userId;

    @NotNull(message = "Account type is required")
    private String accountType;

    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @DecimalMin(value = "0.00", message = "Initial deposit must be non-negative")
    private BigDecimal initialDeposit;

    private String currency;  // Default: TRY
}
