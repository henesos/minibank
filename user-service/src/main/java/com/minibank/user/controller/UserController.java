package com.minibank.user.controller;

import com.minibank.user.dto.*;
import com.minibank.user.service.UserService;
import io.micrometer.tracing.annotation.SpanTag;
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
 * - GET /{id} - Get user by ID
 * - PUT /{id} - Update user profile
 * - DELETE /{id} - Delete user account
 * - POST /{id}/verify-email - Verify email
 * - POST /{id}/verify-phone - Verify phone
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
     * @param id user ID
     * @return user response
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.debug("Get user request for id: {}", id);
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user (from JWT token).
     * 
     * @param authHeader authorization header
     * @return user response
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        UserResponse response = userService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Update user profile.
     * 
     * @param id user ID
     * @param request update request
     * @return updated user
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("Update profile request for user: {}", id);
        UserResponse response = userService.updateProfile(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user account (soft delete).
     * 
     * @param id user ID
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        log.info("Delete account request for user: {}", id);
        userService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify user email.
     * 
     * @param id user ID
     * @return updated user
     */
    @PostMapping("/{id}/verify-email")
    public ResponseEntity<UserResponse> verifyEmail(@PathVariable UUID id) {
        log.info("Verify email request for user: {}", id);
        UserResponse response = userService.verifyEmail(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify user phone.
     * 
     * @param id user ID
     * @return updated user
     */
    @PostMapping("/{id}/verify-phone")
    public ResponseEntity<UserResponse> verifyPhone(@PathVariable UUID id) {
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

    /**
     * Extracts token from Authorization header.
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
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
