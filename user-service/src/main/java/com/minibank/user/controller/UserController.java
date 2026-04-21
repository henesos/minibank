package com.minibank.user.controller;

import com.minibank.user.dto.*;
import com.minibank.user.exception.UserServiceException;
import com.minibank.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User Controller - REST API endpoints for user management.
 * 
 * Base path: /api/v1/users
 * 
 * Endpoints:
 * - POST /register - Register new user
 * - POST /login - Authenticate user
 * - POST /refresh - Refresh access token
 * - GET /{id} - Get user by ID (IDOR protected)
 * - PUT /{id} - Update user profile (IDOR protected)
 * - DELETE /{id} - Delete user account (IDOR protected)
 * - POST /{id}/verify-email - Verify email (IDOR protected)
 * - POST /{id}/verify-phone - Verify phone (IDOR protected)
 * - GET /me - Get current user from X-User-ID header
 * 
 * Security:
 * - IDOR Protection: X-User-ID header must match path variable {id}
 * - API Gateway sets X-User-ID, X-User-Email, X-User-Role headers after JWT validation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Register a new user.
     * 
     * @param request registration request
     * @return created user
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate user and get tokens.
     * 
     * @param request login request
     * @return authentication response with tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token.
     * 
     * @param request refresh token request
     * @return new authentication response
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        AuthResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Get user by ID.
     * 
     * Security: IDOR Protection — X-User-ID header must match the requested {id}.
     * Only the authenticated user can access their own data.
     * 
     * @param id user ID (path variable)
     * @param request HTTP request (to read X-User-ID header)
     * @return user response
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID id,
            HttpServletRequest request) {
        // IDOR Fix: Verify authenticated user can only access their own data
        validateUserIdMatch(id, request);
        log.debug("Get user request for id: {}", id);
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user (from X-User-ID header set by API Gateway).
     * 
     * Security: Uses X-User-ID header instead of parsing JWT token,
     * since API Gateway already validated the JWT.
     * 
     * @param request HTTP request (to read X-User-ID header)
     * @return user response
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpServletRequest request) {
        UUID userId = getAuthenticatedUserId(request);
        log.debug("Get current user request for id: {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update user profile.
     * 
     * Security: IDOR Protection — X-User-ID header must match the requested {id}.
     * 
     * @param id user ID (path variable)
     * @param updateRequest update request
     * @param request HTTP request (to read X-User-ID header)
     * @return updated user
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest updateRequest,
            HttpServletRequest request) {
        // IDOR Fix: Verify authenticated user can only update their own profile
        validateUserIdMatch(id, request);
        log.info("Update profile request for user: {}", id);
        UserResponse response = userService.updateProfile(id, updateRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account (soft delete).
     * 
     * Security: IDOR Protection — X-User-ID header must match the requested {id}.
     * 
     * @param id user ID (path variable)
     * @param request HTTP request (to read X-User-ID header)
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable UUID id,
            HttpServletRequest request) {
        // IDOR Fix: Verify authenticated user can only delete their own account
        validateUserIdMatch(id, request);
        log.info("Delete account request for user: {}", id);
        userService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify user email.
     * 
     * Security: IDOR Protection — X-User-ID header must match the requested {id}.
     * 
     * @param id user ID (path variable)
     * @param request HTTP request (to read X-User-ID header)
     * @return updated user
     */
    @PostMapping("/{id}/verify-email")
    public ResponseEntity<UserResponse> verifyEmail(
            @PathVariable UUID id,
            HttpServletRequest request) {
        // IDOR Fix: Verify authenticated user can only verify their own email
        validateUserIdMatch(id, request);
        log.info("Verify email request for user: {}", id);
        UserResponse response = userService.verifyEmail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify user phone.
     * 
     * Security: IDOR Protection — X-User-ID header must match the requested {id}.
     * 
     * @param id user ID (path variable)
     * @param request HTTP request (to read X-User-ID header)
     * @return updated user
     */
    @PostMapping("/{id}/verify-phone")
    public ResponseEntity<UserResponse> verifyPhone(
            @PathVariable UUID id,
            HttpServletRequest request) {
        // IDOR Fix: Verify authenticated user can only verify their own phone
        validateUserIdMatch(id, request);
        log.info("Verify phone request for user: {}", id);
        UserResponse response = userService.verifyPhone(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("user-service")
                .build());
    }

    // ==================== Security Helper Methods ====================

    /**
     * Extracts authenticated user ID from X-User-ID header set by API Gateway.
     * 
     * Security: API Gateway validates JWT and sets this header.
     * User Service trusts this header on internal network.
     * 
     * @param request HTTP request containing gateway headers
     * @return UUID of the authenticated user
     * @throws UserServiceException if X-User-ID header is missing or invalid
     */
    private UUID getAuthenticatedUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-ID");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UserServiceException(
                "Missing authentication header",
                HttpStatus.UNAUTHORIZED,
                "MISSING_AUTH_HEADER"
            );
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new UserServiceException(
                "Invalid user identifier in authentication header",
                HttpStatus.UNAUTHORIZED,
                "INVALID_AUTH_HEADER"
            );
        }
    }

    /**
     * Validates that the authenticated user (from X-User-ID header) matches
     * the requested resource ID (path variable).
     * 
     * Security: IDOR (Insecure Direct Object Reference) protection.
     * Prevents users from accessing or modifying other users' data.
     * 
     * @param pathId the user ID from the path variable
     * @param request HTTP request containing X-User-ID header
     * @throws UserServiceException with 403 Forbidden if IDs don't match
     */
    private void validateUserIdMatch(UUID pathId, HttpServletRequest request) {
        UUID authenticatedId = getAuthenticatedUserId(request);
        if (!pathId.equals(authenticatedId)) {
            log.warn("IDOR attempt: authenticated user {} tried to access resource of user {}",
                    authenticatedId, pathId);
            throw new UserServiceException(
                "Access denied: You can only access your own resources",
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
            );
        }
    }

    /**
     * DTO for refresh token request.
     */
    @lombok.Data
    public static class RefreshTokenRequest {
        private String refreshToken;
    }

    /**
     * DTO for health check response.
     */
    @lombok.Data
    @lombok.Builder
    public static class HealthResponse {
        private String status;
        private String service;
    }
}
