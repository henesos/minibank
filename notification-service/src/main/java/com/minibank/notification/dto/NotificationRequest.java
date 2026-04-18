package com.minibank.notification.dto;

import com.minibank.notification.entity.Notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must not exceed 5000 characters")
    private String content;

    private UUID referenceId;

    @Size(max = 50, message = "Reference type must not exceed 50 characters")
    private String referenceType;

    @Size(max = 255, message = "Recipient must not exceed 255 characters")
    private String recipient;

    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    private String metadata;
}
