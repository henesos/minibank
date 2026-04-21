package com.minibank.account.dto;

import com.minibank.account.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for account response.
 * 
 * Note: Balance is included but should always be read from DB,
 * not from any cached response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private String status;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Maps Account entity to AccountResponse DTO.
     * 
     * @param account the account entity
     * @return the response DTO
     */
    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .name(account.getName())
                .description(account.getDescription())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
