package com.minibank.notification.repository;

import com.minibank.notification.entity.Notification;
import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Notification entity.
 * 
 * Provides CRUD operations and custom queries for notification management.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Finds all notifications for a specific user.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Finds all notifications for a user with specific status.
     */
    List<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status);

    /**
     * Finds all pending notifications ready to be sent.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'PENDING' ORDER BY n.createdAt ASC")
    List<Notification> findPendingNotifications();

    /**
     * Finds pending notifications with limit.
     */
    @Query(value = "SELECT * FROM notifications WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit", 
           nativeQuery = true)
    List<Notification> findPendingNotifications(@Param("limit") int limit);

    /**
     * Finds notification by idempotency key.
     */
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    /**
     * Checks if notification exists by idempotency key.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Finds notifications by reference ID.
     */
    List<Notification> findByReferenceId(UUID referenceId);

    /**
     * Finds notifications by reference ID and type.
     */
    List<Notification> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    /**
     * Counts notifications by user and status.
     */
    long countByUserIdAndStatus(UUID userId, NotificationStatus status);

    /**
     * Counts pending notifications.
     */
    long countByStatus(NotificationStatus status);

    /**
     * Finds failed notifications that can be retried.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<Notification> findRetryableNotifications();

    /**
     * Finds notifications created after a specific date.
     */
    List<Notification> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Finds notifications by type and status.
     */
    List<Notification> findByTypeAndStatus(NotificationType type, NotificationStatus status);

    /**
     * Finds notifications for a user within a date range.
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    List<Notification> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Bulk updates notification status.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = :status WHERE n.id IN :ids")
    int updateStatusByIds(@Param("ids") List<UUID> ids, @Param("status") NotificationStatus status);

    /**
     * Finds unread notifications count for a user (PENDING and SENT).
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.status IN ('PENDING', 'SENT')")
    long countUnreadNotifications(@Param("userId") UUID userId);

    /**
     * Counts unread notifications for a user (by read flag).
     */
    long countByUserIdAndReadFalse(UUID userId);

    /**
     * Finds all unread notifications for a user.
     */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId);

    /**
     * Marks all notifications as read for a user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") UUID userId);

    /**
     * Deletes old notifications (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :beforeDate AND n.status IN ('DELIVERED', 'CANCELLED')")
    int deleteOldNotifications(@Param("beforeDate") LocalDateTime beforeDate);
}
