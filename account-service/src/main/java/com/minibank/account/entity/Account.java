package com.minibank.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Entity for MiniBank
 * 
 * Represents a bank account with balance and status.
 * 
 * CRITICAL DESIGN DECISIONS:
 * 1. Balance is NEVER cached - always read from DB
 * 2. Balance updates use atomic SQL (UPDATE ... WHERE balance >= amount)
 * 3. Uses DECIMAL(19,4) for precision - no floating point errors
 * 4. Soft delete pattern for audit trail
 * 
 * Currency Support:
 * - TRY (Turkish Lira) - default
 * - USD, EUR supported for international accounts
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_user_id", columnList = "user_id"),
    @Index(name = "idx_account_number", columnList = "account_number", unique = true),
    @Index(name = "idx_account_status", columnList = "status"),
    @Index(name = "idx_account_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    /**
     * CRITICAL: Balance is stored with DECIMAL(19,4) precision.
     * NEVER use double/float for financial calculations!
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Account types supported by MiniBank.
     */
    public enum AccountType {
        SAVINGS,    // Tasarruf hesabı
        CHECKING,   // Vadesiz hesap
        BUSINESS    // Ticari hesap
    }

    /**
     * Account status lifecycle.
     */
    public enum AccountStatus {
        PENDING,    // Newly created, awaiting activation
        ACTIVE,     // Fully operational
        DORMANT,    // No activity for extended period
        SUSPENDED,  // Temporarily disabled
        CLOSED      // Permanently closed
    }

    /**
     * Soft deletes the account.
     */
    public void softDelete() {
        this.deleted = true;
        this.status = AccountStatus.CLOSED;
    }

    /**
     * Activates the account.
     */
    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Suspends the account.
     */
    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    /**
     * Checks if account is active for transactions.
     */
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    /**
     * Checks if there's sufficient balance for a withdrawal.
     * 
     * @param amount the amount to check
     * @return true if sufficient balance exists
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.availableBalance.compareTo(amount) >= 0;
    }
}
