package com.minibank.notification.dto;

import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for notification data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private NotificationStatus status;
    private String subject;
    private String content;
    private UUID referenceId;
    private String referenceType;
    private String recipient;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;

    /**
     * Creates a summary response (without full content).
     */
    public NotificationResponse asSummary() {
        return NotificationResponse.builder()
                .id(this.id)
                .userId(this.userId)
                .type(this.type)
                .status(this.status)
                .subject(this.subject)
                .referenceId(this.referenceId)
                .referenceType(this.referenceType)
                .createdAt(this.createdAt)
                .build();
    }
}
