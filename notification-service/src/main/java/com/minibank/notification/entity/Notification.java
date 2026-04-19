package com.minibank.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification Entity for MiniBank
 * 
 * Stores all notification records for audit and tracking purposes.
 * Supports multiple notification channels: EMAIL, SMS, PUSH, IN_APP.
 * 
 * NOTIFICATION LIFECYCLE:
 * 1. PENDING - Notification created, waiting to be sent
 * 2. SENT - Successfully sent to provider
 * 3. DELIVERED - Confirmed delivery (if tracking available)
 * 4. FAILED - Failed to send after retries
 * 5. CANCELLED - Cancelled before sending
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "user_id"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_type", columnList = "type"),
    @Index(name = "idx_notification_reference", columnList = "reference_id"),
    @Index(name = "idx_notification_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * User who will receive the notification
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Notification channel type
     */
    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /**
     * Current status of the notification
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * Subject/title of the notification
     */
    @Column(name = "subject", length = 255)
    private String subject;

    /**
     * Main content/body of the notification
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Reference to the related entity (transaction, account, etc.)
     */
    @Column(name = "reference_id")
    private UUID referenceId;

    /**
     * Type of the referenced entity
     */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    /**
     * Recipient address (email, phone number, device token, etc.)
     */
    @Column(name = "recipient", length = 255)
    private String recipient;

    /**
     * Idempotency key for duplicate prevention
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    /**
     * Number of retry attempts
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts allowed
     */
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * Error message if failed
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * When the notification was sent
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * When the notification was delivered
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * Additional metadata as JSON
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Whether the notification has been read by the user
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean read = false;

    /**
     * Soft delete flag
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Notification channel types
     */
    public enum NotificationType {
        EMAIL,      // Email notification
        SMS,        // SMS text message
        PUSH,       // Mobile push notification
        IN_APP      // In-app notification
    }

    /**
     * Notification status lifecycle
     */
    public enum NotificationStatus {
        PENDING,    // Created, waiting to be sent
        SENDING,    // Currently being sent
        SENT,       // Successfully sent to provider
        DELIVERED,  // Delivery confirmed
        FAILED,     // Failed after all retries
        CANCELLED   // Cancelled before sending
    }

    /**
     * Marks notification as sent.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Marks notification as delivered.
     */
    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Marks notification as failed.
     */
    public void markAsFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = error;
    }

    /**
     * Increments retry count and sets status back to pending.
     */
    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount < this.maxRetries) {
            this.status = NotificationStatus.PENDING;
        }
    }

    /**
     * Checks if notification can be retried.
     */
    public boolean canRetry() {
        return this.retryCount < this.maxRetries && 
               (this.status == NotificationStatus.PENDING || 
                this.status == NotificationStatus.FAILED);
    }

    /**
     * Marks notification as sending.
     */
    public void markAsSending() {
        this.status = NotificationStatus.SENDING;
    }

    /**
     * Cancels the notification.
     */
    public void cancel() {
        this.status = NotificationStatus.CANCELLED;
    }

    /**
     * Soft deletes the notification.
     */
    public void softDelete() {
        this.deleted = true;
    }

    /**
     * Marks the notification as read.
     */
    public void markAsRead() {
        this.read = true;
    }

    /**
     * Marks the notification as unread.
     */
    public void markAsUnread() {
        this.read = false;
    }
}
