package com.minibank.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Entity for MiniBank
 *
 * Represents a bank customer with authentication credentials and profile information.
 * Uses soft delete pattern - deleted records are marked but preserved for audit.
 *
 * Security considerations:
 * - Password is stored as bcrypt hash (never plain text)
 * - Email and phone must be unique
 * - Status controls account lifecycle
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_phone", columnList = "phone", unique = true),
    @Index(name = "idx_user_status", columnList = "status"),
    @Index(name = "idx_user_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "national_id", unique = true, length = 20)
    private String nationalId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

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
     * User status enum representing account lifecycle states.
     */
    public enum UserStatus {
        PENDING,        // Newly registered, awaiting verification
        ACTIVE,         // Fully verified and operational
        SUSPENDED,      // Temporarily disabled (security concern)
        LOCKED,         // Locked due to failed login attempts
        CLOSED          // Permanently closed by user or admin
    }

    /**
     * Marks the user as deleted (soft delete).
     * Also anonymizes sensitive data for GDPR compliance.
     */
    public void softDelete() {
        this.deleted = true;
        this.status = UserStatus.CLOSED;
        // Anonymize sensitive data
        this.email = "deleted_" + this.id + "@deleted.minibank.com";
        this.phone = null;
        this.nationalId = null;
    }

    /**
     * Checks if the account is currently locked due to failed login attempts.
     */
    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Resets failed login attempts and unlocks the account.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /**
     * Increments failed login attempts and locks account if threshold exceeded.
     *
     * @param maxAttempts maximum allowed failed attempts
     * @param lockDurationMinutes how long to lock the account
     */
    public void incrementFailedLoginAttempts(int maxAttempts, int lockDurationMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.status = UserStatus.LOCKED;
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
        }
    }

    /**
     * Records a successful login.
     */
    public void recordSuccessfulLogin() {
        this.lastLoginAt = LocalDateTime.now();
        resetFailedLoginAttempts();
    }

    /**
     * Returns the full name of the user.
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        return String.join(" ",
            firstName != null ? firstName : "",
            lastName != null ? lastName : ""
        ).trim();
    }
}
