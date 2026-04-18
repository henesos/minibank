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
     */
    private void handleSendFailure(Notification notification, String errorMessage) {
        notification.incrementRetry();
        
        if (notification.getRetryCount() >= notification.getMaxRetries()) {
            notification.markAsFailed(errorMessage);
            log.error("Notification {} failed after {} attempts: {}", 
                    notification.getId(), notification.getRetryCount(), errorMessage);
        } else {
            notification.markAsFailed(errorMessage);
            log.warn("Notification {} failed, attempt {}/{}: {}", 
                    notification.getId(), 
                    notification.getRetryCount(), 
                    notification.getMaxRetries(), 
                    errorMessage);
        }
    }

    /**
     * Creates notification from transaction event.
     */
    @Transactional
    public NotificationResponse createFromTransactionEvent(TransactionEvent event) {
        log.debug("Creating notification from transaction event: {}", event.getEventType());

        String idempotencyKey = "tx-" + event.getEventId();
        
        // Determine notification content based on event type
        String subject = generateSubject(event);
        String content = generateContent(event);
        
        // Notify the sender (fromUserId)
        NotificationRequest senderRequest = NotificationRequest.builder()
                .userId(event.getFromUserId())
                .type(NotificationType.EMAIL)
                .subject(subject)
                .content(content)
                .referenceId(event.getSagaId())
                .referenceType("TRANSACTION")
                .idempotencyKey(idempotencyKey + "-sender")
                .build();

        return createNotification(senderRequest);
    }

    /**
     * Generates notification subject based on transaction event.
     */
    private String generateSubject(TransactionEvent event) {
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
    }

    /**
     * Generates notification content based on transaction event.
     */
    private String generateContent(TransactionEvent event) {
        StringBuilder sb = new StringBuilder();
        
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
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
