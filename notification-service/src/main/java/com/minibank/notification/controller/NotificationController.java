package com.minibank.notification.controller;

import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Notification Service.
 * 
 * Provides endpoints for managing notifications:
 * - Create notifications
 * - Get notification status
 * - List user notifications
 * - Process pending notifications
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "APIs for managing notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Creates a new notification.
     */
    @PostMapping
    @Operation(summary = "Create notification", description = "Creates and queues a new notification")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Duplicate notification")
    })
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody NotificationRequest request) {
        
        log.info("Creating notification for user: {}, type: {}", 
                request.getUserId(), request.getType());
        
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a notification by ID.
     */
    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification", description = "Gets notification details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationResponse> getNotification(
            @Parameter(description = "Notification ID") 
            @PathVariable("notificationId") UUID notificationId) {
        
        log.debug("Getting notification: {}", notificationId);
        
        NotificationResponse response = notificationService.getNotification(notificationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets all notifications for a user.
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user notifications", description = "Gets all notifications for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved")
    })
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @Parameter(description = "User ID") 
            @PathVariable("userId") UUID userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        log.debug("Getting notifications for user: {}", userId);
        
        Page<NotificationResponse> notifications = notificationService
                .getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Sends a pending notification.
     */
    @PostMapping("/{notificationId}/send")
    @Operation(summary = "Send notification", description = "Sends a pending notification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification sent successfully"),
            @ApiResponse(responseCode = "404", description = "Notification not found"),
            @ApiResponse(responseCode = "500", description = "Failed to send notification")
    })
    public ResponseEntity<NotificationResponse> sendNotification(
            @Parameter(description = "Notification ID") 
            @PathVariable("notificationId") UUID notificationId) {
        
        log.info("Sending notification: {}", notificationId);
        
        NotificationResponse response = notificationService.sendNotification(notificationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets all pending notifications (admin endpoint).
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending notifications", description = "Gets all pending notifications")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending notifications retrieved")
    })
    public ResponseEntity<List<NotificationResponse>> getPendingNotifications() {
        log.debug("Getting pending notifications");
        
        List<NotificationResponse> notifications = notificationService.getPendingNotifications();
        return ResponseEntity.ok(notifications);
    }

    /**
     * Processes all pending notifications (admin endpoint).
     */
    @PostMapping("/process")
    @Operation(summary = "Process pending notifications", description = "Processes all pending notifications")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications processed")
    })
    public ResponseEntity<Integer> processPendingNotifications() {
        log.info("Processing pending notifications");
        
        int processed = notificationService.processPendingNotifications();
        return ResponseEntity.ok(processed);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Notification Service is healthy");
    }
}
