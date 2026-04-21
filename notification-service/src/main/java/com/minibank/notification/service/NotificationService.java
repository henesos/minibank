package com.minibank.notification.service;

import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.entity.Notification;
import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
import com.minibank.notification.exception.DuplicateNotificationException;
import com.minibank.notification.exception.NotificationNotFoundException;
import com.minibank.notification.exception.NotificationServiceException;
import com.minibank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing notifications.
 *
 * Handles creation, sending, and tracking of notifications
 * across multiple channels (email, SMS, push, in-app).
 *
 * Bug fix: handleSendFailure() now correctly keeps status as PENDING
 * when retries remain, instead of incorrectly marking as FAILED.
 * A @Scheduled method retries PENDING notifications with retryCount > 0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${notification.retry.max-attempts:3}")
    private int defaultMaxRetries;

    /**
     * Creates and queues a new notification.
     *
     * @param request notification request
     * @return created notification response
     * @throws DuplicateNotificationException if idempotency key already exists
     */
    @Transactional
    public NotificationResponse createNotification(NotificationRequest request) {
        log.debug("Creating notification for user: {}, type: {}", request.getUserId(), request.getType());

        // Check for duplicate using idempotency key
        if (request.getIdempotencyKey() != null) {
            notificationRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new DuplicateNotificationException(
                                request.getIdempotencyKey(),
                                existing.getId()
                        );
                    });
        }

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .subject(request.getSubject())
                .content(request.getContent())
                .referenceId(request.getReferenceId())
                .referenceType(request.getReferenceType())
                .recipient(request.getRecipient())
                .idempotencyKey(request.getIdempotencyKey())
                .metadata(request.getMetadata())
                .status(NotificationStatus.PENDING)
                .maxRetries(defaultMaxRetries)
                .build();

        notification = notificationRepository.save(notification);
        log.info("Created notification: {} for user: {}", notification.getId(), notification.getUserId());

        return toResponse(notification);
    }

    /**
     * Sends a pending notification.
     *
     * @param notificationId notification to send
     * @return updated notification response
     */
    @Transactional
    public NotificationResponse sendNotification(UUID notificationId) {
        log.debug("Sending notification: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (notification.getStatus() == NotificationStatus.SENT ||
            notification.getStatus() == NotificationStatus.DELIVERED) {
            log.warn("Notification {} already sent with status: {}", notificationId, notification.getStatus());
            return toResponse(notification);
        }

        if (!notification.canRetry()) {
            log.warn("Notification {} cannot be retried - max attempts reached", notificationId);
            throw new NotificationServiceException("Notification cannot be retried - max attempts reached");
        }

        notification.markAsSending();
        notification = notificationRepository.save(notification);

        try {
            boolean sent = sendByChannel(notification);

            if (sent) {
                notification.markAsSent();
                log.info("Successfully sent notification: {}", notificationId);
            } else {
                handleSendFailure(notification, "Failed to send via provider");
            }
        } catch (Exception e) {
            log.error("Error sending notification {}: {}", notificationId, e.getMessage(), e);
            handleSendFailure(notification, e.getMessage());
        }

        notification = notificationRepository.save(notification);
        return toResponse(notification);
    }

    /**
     * Sends notification via appropriate channel.
     */
    private boolean sendByChannel(Notification notification) {
        switch (notification.getType()) {
            case EMAIL:
                return emailService.send(notification);
            case SMS:
                return smsService.send(notification);
            case PUSH:
            case IN_APP:
                // For MVP, in-app and push are simulated
                log.info("Simulating {} notification for user: {}",
                        notification.getType(), notification.getUserId());
                return true;
            default:
                log.warn("Unknown notification type: {}", notification.getType());
                return false;
        }
    }

    /**
     * Handles a failed send attempt.
     *
     * BUG FIX: Previously this always called markAsFailed(), which set the
     * status to FAILED even when retries remained. Now:
     * - If retryCount < maxRetries → status stays PENDING so the scheduled
     *   retry job can pick it up again.
     * - If retryCount >= maxRetries → status becomes FAILED permanently.
     */
    private void handleSendFailure(Notification notification, String errorMessage) {
        notification.incrementRetry();

        if (notification.getRetryCount() >= notification.getMaxRetries()) {
            // No more retries — mark as permanently failed
            notification.markAsFailed(errorMessage);
            log.error("Notification {} failed permanently after {} attempts: {}",
                    notification.getId(), notification.getRetryCount(), errorMessage);
        } else {
            // Retries remaining — keep status as PENDING for scheduled retry
            // incrementRetry() already sets status to PENDING when retries remain
            log.warn("Notification {} send failed, attempt {}/{}: {} — will retry",
                    notification.getId(),
                    notification.getRetryCount(),
                    notification.getMaxRetries(),
                    errorMessage);
        }
    }

    /**
     * Scheduled retry job: runs every 30 seconds to re-attempt PENDING
     * notifications that have been retried at least once (retryCount > 0).
     *
     * This handles notifications that failed to send but still have retries
     * remaining. @EnableScheduling is declared on NotificationServiceApplication.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void retryPendingNotifications() {
        List<Notification> pendingRetries = notificationRepository
                .findByStatusAndRetryCountGreaterThan(NotificationStatus.PENDING, 0);

        if (pendingRetries.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending notifications", pendingRetries.size());

        int retried = 0;
        for (Notification notification : pendingRetries) {
            try {
                sendNotification(notification.getId());
                retried++;
            } catch (Exception e) {
                log.error("Retry failed for notification {}: {}",
                        notification.getId(), e.getMessage());
            }
        }

        log.info("Successfully retried {} out of {} pending notifications",
                retried, pendingRetries.size());
    }

    /**
     * Creates notification from transaction event.
     *
     * @param event   the transaction event
     * @param isSender true = notification for sender (fromUserId),
     *                 false = notification for receiver (toUserId)
     */
    @Transactional
    public NotificationResponse createFromTransactionEvent(TransactionEvent event, boolean isSender) {
        log.debug("Creating notification from transaction event: {} (isSender: {})",
                event.getEventType(), isSender);

        String idempotencyKey = "tx-" + event.getEventId() + (isSender ? "-sender" : "-receiver");

        // Determine notification content based on event type and role
        String subject = generateSubject(event, isSender);
        String content = generateContent(event, isSender);

        // Select the target user
        UUID targetUserId = isSender ? event.getFromUserId() : event.getToUserId();

        NotificationRequest request = NotificationRequest.builder()
                .userId(targetUserId)
                .type(NotificationType.EMAIL)
                .subject(subject)
                .content(content)
                .referenceId(event.getSagaId())
                .referenceType("TRANSACTION")
                .idempotencyKey(idempotencyKey)
                .build();

        return createNotification(request);
    }

    /**
     * Backward-compatible overload: defaults to sender notification.
     */
    @Transactional
    public NotificationResponse createFromTransactionEvent(TransactionEvent event) {
        return createFromTransactionEvent(event, true);
    }

    /**
     * Generates notification subject based on transaction event and role.
     */
    private String generateSubject(TransactionEvent event, boolean isSender) {
        if (isSender) {
            switch (event.getEventType()) {
                case TRANSACTION_INITIATED:
                    return "İşleminiz Başlatıldı - MiniBank";
                case TRANSACTION_COMPLETED:
                    return "İşleminiz Tamamlandı - MiniBank";
                case TRANSACTION_FAILED:
                    return "İşlem Başarısız - MiniBank";
                case COMPENSATION_COMPLETED:
                    return "İşlem İptal Edildi - MiniBank";
                default:
                    return "İşlem Durumu Güncellendi - MiniBank";
            }
        } else {
            // Receiver perspective
            switch (event.getEventType()) {
                case TRANSACTION_COMPLETED:
                    return "Hesabınıza Transfer Alındı - MiniBank";
                default:
                    return "İşlem Durumu Güncellendi - MiniBank";
            }
        }
    }

    /**
     * Generates notification content based on transaction event and role.
     */
    private String generateContent(TransactionEvent event, boolean isSender) {
        StringBuilder sb = new StringBuilder();

        if (isSender) {
            switch (event.getEventType()) {
                case TRANSACTION_INITIATED:
                    sb.append("Sayın Müşterimiz,\n\n");
                    sb.append(String.format("%.2f %s tutarındaki transfer işleminiz başlatılmıştır.\n",
                            event.getAmount(), event.getCurrency()));
                    sb.append("İşlem tamamlandığında bilgilendirileceksiniz.\n\n");
                    break;
                case TRANSACTION_COMPLETED:
                    sb.append("Sayın Müşterimiz,\n\n");
                    sb.append(String.format("%.2f %s tutarındaki transfer işleminiz başarıyla tamamlanmıştır.\n",
                            event.getAmount(), event.getCurrency()));
                    break;
                case TRANSACTION_FAILED:
                    sb.append("Sayın Müşterimiz,\n\n");
                    sb.append(String.format("%.2f %s tutarındaki transfer işleminiz gerçekleştirilemedi.\n",
                            event.getAmount(), event.getCurrency()));
                    if (event.getFailureReason() != null) {
                        sb.append("Hata nedeni: ").append(event.getFailureReason()).append("\n");
                    }
                    break;
                case COMPENSATION_COMPLETED:
                    sb.append("Sayın Müşterimiz,\n\n");
                    sb.append(String.format("%.2f %s tutarındaki transfer işleminiz iptal edilmiş olup,\n",
                            event.getAmount(), event.getCurrency()));
                    sb.append("tutar hesabınıza iade edilmiştir.\n");
                    break;
                default:
                    sb.append("İşlem durumunuz güncellenmiştir.\n");
            }
        } else {
            // Receiver perspective
            switch (event.getEventType()) {
                case TRANSACTION_COMPLETED:
                    sb.append("Sayın Müşterimiz,\n\n");
                    sb.append(String.format("Hesabınıza %.2f %s tutarında transfer alınmıştır.\n",
                            event.getAmount(), event.getCurrency()));
                    break;
                default:
                    sb.append("Hesabınızla ilgili işlem durumunuz güncellenmiştir.\n");
            }
        }

        sb.append("\nSaygılarımızla,\nMiniBank");
        return sb.toString();
    }

    /**
     * Gets notification by ID.
     */
    @Transactional(readOnly = true)
    public NotificationResponse getNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    /**
     * Gets all notifications for a user.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /**
     * Gets pending notifications.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getPendingNotifications() {
        return notificationRepository.findPendingNotifications()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Processes all pending notifications.
     */
    @Transactional
    public int processPendingNotifications() {
        List<Notification> pending = notificationRepository.findPendingNotifications(100);
        int processed = 0;

        for (Notification notification : pending) {
            try {
                sendNotification(notification.getId());
                processed++;
            } catch (Exception e) {
                log.error("Failed to process notification {}: {}",
                        notification.getId(), e.getMessage());
            }
        }

        log.info("Processed {} pending notifications", processed);
        return processed;
    }

    /**
     * Converts entity to response DTO.
     */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .status(notification.getStatus())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .recipient(notification.getRecipient())
                .retryCount(notification.getRetryCount())
                .errorMessage(notification.getErrorMessage())
                .read(notification.getRead())
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    /**
     * Marks a notification as read.
     *
     * @param notificationId notification to mark as read
     * @return updated notification response
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        log.debug("Marking notification as read: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.markAsRead();
        notification = notificationRepository.save(notification);

        log.info("Notification {} marked as read", notificationId);
        return toResponse(notification);
    }

    /**
     * Marks all notifications as read for a user.
     *
     * @param userId user whose notifications to mark as read
     * @return number of notifications marked as read
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        log.debug("Marking all notifications as read for user: {}", userId);

        int count = notificationRepository.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user: {}", count, userId);

        return count;
    }

    /**
     * Gets unread notification count for a user.
     *
     * @param userId user to get count for
     * @return number of unread notifications
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Gets all unread notifications for a user.
     *
     * @param userId user to get notifications for
     * @return list of unread notifications
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Soft deletes a notification.
     *
     * @param notificationId notification to delete
     */
    @Transactional
    public void deleteNotification(UUID notificationId) {
        log.debug("Deleting notification: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.softDelete();
        notificationRepository.save(notification);

        log.info("Notification {} soft deleted", notificationId);
    }
}
