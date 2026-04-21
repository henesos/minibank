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
 * Provides endpoints for managing notifications with proper authorization:
 * - User-scoped endpoints: require X-User-ID header (set by API Gateway) to
 *   match the requested userId — prevents IDOR attacks
 * - Admin endpoints: require X-User-Role = "ADMIN" header
 * - Internal endpoints: require X-Internal-Request header or admin role
 *
 * Security headers set by API Gateway (JwtAuthenticationFilter):
 *   X-User-ID    — authenticated user's ID
 *   X-User-Role  — authenticated user's role
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "APIs for managing notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // ────────────────────────────────────────────────────────────
    // Authorization helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Extracts the authenticated user ID from the X-User-ID header
     * set by the API Gateway after JWT validation.
     *
     * @throws ResponseStatusException 401 if header is missing
     */
    private UUID getAuthenticatedUserId() {
        String userIdStr = null;
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder
                                    .getRequestAttributes()).getRequest();
            userIdStr = request.getHeader("X-User-ID");
        } catch (Exception e) {
            log.warn("Could not retrieve X-User-ID header: {}", e.getMessage());
        }

        if (userIdStr == null || userIdStr.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing X-User-ID header — unauthenticated request");
        }
        return UUID.fromString(userIdStr);
    }

    /**
     * Validates that the authenticated user owns the requested resource.
     *
     * @throws ResponseStatusException 403 if userId does not match
     */
    private void validateUserOwnership(UUID pathUserId) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        if (!authenticatedUserId.equals(pathUserId)) {
            log.warn("IDOR attempt: authenticated user {} tried to access resource of user {}",
                    authenticatedUserId, pathUserId);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not authorized to access another user's notifications");
        }
    }

    /**
     * Checks whether the current request comes from an admin user.
     */
    private boolean isAdmin() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder
                                    .getRequestAttributes()).getRequest();
            String role = request.getHeader("X-User-Role");
            return "ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Requires admin role; throws 403 if not admin.
     */
    private void requireAdmin() {
        if (!isAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    /**
     * Validates that the authenticated user owns the given notification.
     */
    private void validateNotificationOwnership(NotificationResponse notification) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        if (!authenticatedUserId.equals(notification.getUserId())) {
            log.warn("IDOR attempt: user {} tried to access notification {} owned by user {}",
                    authenticatedUserId, notification.getId(), notification.getUserId());
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not authorized to access this notification");
        }
    }

    /**
     * Checks if the request is internal (service-to-service) or from an admin.
     */
    private boolean isInternalOrAdmin() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder
                                    .getRequestAttributes()).getRequest();
            String internalHeader = request.getHeader("X-Internal-Request");
            if ("true".equalsIgnoreCase(internalHeader)) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        return isAdmin();
    }

    // ────────────────────────────────────────────────────────────
    // Endpoints
    // ────────────────────────────────────────────────────────────

    /**
     * Creates a new notification.
     * Restricted: internal service-to-service calls or admin users only.
     */
    @PostMapping
    @Operation(summary = "Create notification", description = "Creates and queues a new notification (internal/admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Forbidden — internal or admin access required"),
            @ApiResponse(responseCode = "409", description = "Duplicate notification")
    })
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody NotificationRequest request) {

        // Security: only internal services or admins can create notifications directly
        if (!isInternalOrAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only internal services or admins can create notifications");
        }

        log.info("Creating notification for user: {}, type: {}",
                request.getUserId(), request.getType());

        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a notification by ID.
     * User must own the notification.
     */
    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification", description = "Gets notification details by ID (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationResponse> getNotification(
            @Parameter(description = "Notification ID")
            @PathVariable("notificationId") UUID notificationId) {

        log.debug("Getting notification: {}", notificationId);

        NotificationResponse response = notificationService.getNotification(notificationId);

        // IDOR check: user must own this notification
        validateNotificationOwnership(response);

        return ResponseEntity.ok(response);
    }

    /**
     * Gets all notifications for a user.
     * X-User-ID must match the path userId.
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user notifications", description = "Gets all notifications for a specific user (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner")
    })
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @Parameter(description = "User ID")
            @PathVariable("userId") UUID userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        // IDOR check: authenticated user must match path userId
        validateUserOwnership(userId);

        log.debug("Getting notifications for user: {}", userId);

        Page<NotificationResponse> notifications = notificationService
                .getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Sends a pending notification.
     * Admin only — regular users cannot trigger sends.
     */
    @PostMapping("/{notificationId}/send")
    @Operation(summary = "Send notification", description = "Sends a pending notification (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification sent successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden — admin access required"),
            @ApiResponse(responseCode = "404", description = "Notification not found"),
            @ApiResponse(responseCode = "500", description = "Failed to send notification")
    })
    public ResponseEntity<NotificationResponse> sendNotification(
            @Parameter(description = "Notification ID")
            @PathVariable("notificationId") UUID notificationId) {

        requireAdmin();

        log.info("Sending notification: {}", notificationId);

        NotificationResponse response = notificationService.sendNotification(notificationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets all pending notifications.
     * Admin only.
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending notifications", description = "Gets all pending notifications (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending notifications retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden — admin access required")
    })
    public ResponseEntity<List<NotificationResponse>> getPendingNotifications() {
        requireAdmin();

        log.debug("Getting pending notifications");

        List<NotificationResponse> notifications = notificationService.getPendingNotifications();
        return ResponseEntity.ok(notifications);
    }

    /**
     * Processes all pending notifications.
     * Admin only.
     */
    @PostMapping("/process")
    @Operation(summary = "Process pending notifications", description = "Processes all pending notifications (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications processed"),
            @ApiResponse(responseCode = "403", description = "Forbidden — admin access required")
    })
    public ResponseEntity<Integer> processPendingNotifications() {
        requireAdmin();

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

    /**
     * Gets unread notification count for a user.
     * X-User-ID must match the path userId.
     */
    @GetMapping("/user/{userId}/unread/count")
    @Operation(summary = "Get unread count", description = "Gets the count of unread notifications for a user (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner")
    })
    public ResponseEntity<Long> getUnreadCount(
            @Parameter(description = "User ID")
            @PathVariable("userId") UUID userId) {

        // IDOR check
        validateUserOwnership(userId);

        log.debug("Getting unread count for user: {}", userId);

        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Gets all unread notifications for a user.
     * X-User-ID must match the path userId.
     */
    @GetMapping("/user/{userId}/unread")
    @Operation(summary = "Get unread notifications", description = "Gets all unread notifications for a user (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner")
    })
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @Parameter(description = "User ID")
            @PathVariable("userId") UUID userId) {

        // IDOR check
        validateUserOwnership(userId);

        log.debug("Getting unread notifications for user: {}", userId);

        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Marks a notification as read.
     * User must own the notification.
     */
    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark as read", description = "Marks a notification as read (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationResponse> markAsRead(
            @Parameter(description = "Notification ID")
            @PathVariable("notificationId") UUID notificationId) {

        log.info("Marking notification as read: {}", notificationId);

        NotificationResponse response = notificationService.markAsRead(notificationId);

        // IDOR check: user must own this notification
        validateNotificationOwnership(response);

        return ResponseEntity.ok(response);
    }

    /**
     * Marks all notifications as read for a user.
     * X-User-ID must match the path userId.
     */
    @PutMapping("/user/{userId}/read-all")
    @Operation(summary = "Mark all as read", description = "Marks all notifications as read for a user (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner")
    })
    public ResponseEntity<Integer> markAllAsRead(
            @Parameter(description = "User ID")
            @PathVariable("userId") UUID userId) {

        // IDOR check
        validateUserOwnership(userId);

        log.info("Marking all notifications as read for user: {}", userId);

        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Deletes a notification (soft delete).
     * User must own the notification.
     */
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification", description = "Soft deletes a notification (owner only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification deleted"),
            @ApiResponse(responseCode = "403", description = "Forbidden — not notification owner"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> deleteNotification(
            @Parameter(description = "Notification ID")
            @PathVariable("notificationId") UUID notificationId) {

        log.info("Deleting notification: {}", notificationId);

        // Fetch notification first to verify ownership before deleting
        NotificationResponse response = notificationService.getNotification(notificationId);
        validateNotificationOwnership(response);

        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }
}
