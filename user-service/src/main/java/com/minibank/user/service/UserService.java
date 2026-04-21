package com.minibank.user.service;

import com.minibank.user.dto.*;
import com.minibank.user.entity.User;
import com.minibank.user.exception.*;
import com.minibank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Service - Business logic for user management.
 * 
 * Handles user registration, authentication, and profile management.
 * Implements caching for frequently accessed user data.
 * 
 * Cache Strategy:
 * - User profile: Cacheable (5 min TTL) - changes rarely
 * - Balance: NEVER cached - managed by Account Service
 * - Session: Redis (30 min TTL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;

    /**
     * Registers a new user.
     * 
     * @param request registration request with user details
     * @return created user response
     * @throws EmailAlreadyExistsException if email is already registered
     */
    @Transactional
    public UserResponse register(UserRegistrationRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        // Check if phone already exists (if provided)
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new UserServiceException(
                "Phone number already registered",
                org.springframework.http.HttpStatus.CONFLICT,
                "PHONE_EXISTS"
            );
        }

        // Security Fix: Check if nationalId already exists (prevent duplicate identity)
        if (request.getNationalId() != null && !request.getNationalId().isBlank()
                && userRepository.findByNationalId(request.getNationalId()).isPresent()) {
            throw new UserServiceException(
                "National ID already registered",
                org.springframework.http.HttpStatus.CONFLICT,
                "NATIONAL_ID_EXISTS"
            );
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .nationalId(request.getNationalId())
                .status(User.UserStatus.PENDING)
                .emailVerified(false)
                .phoneVerified(false)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully with id: {}", user.getId());

        return UserResponse.fromEntity(user);
    }

    /**
     * Authenticates a user and generates JWT tokens.
     * 
     * Security Checks (in order):
     * 1. Find user by email (case-insensitive)
     * 2. Auto-unlock if lock duration has expired
     * 3. Reject locked accounts
     * 4. Verify password
     * 5. Reject PENDING users — only ACTIVE users can login
     * 6. Record successful login
     * 7. Generate tokens
     * 
     * @param request login request with credentials
     * @return authentication response with tokens
     * @throws InvalidCredentialsException if credentials are invalid
     * @throws AccountLockedException if account is locked
     */
    @Transactional
    public AuthResponse login(UserLoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Security Fix: Use case-insensitive email lookup to prevent duplicate accounts
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // Security Fix: Auto-unlock account if lock duration has expired
        if (user.isAccountLocked() && user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            log.info("Auto-unlocking expired lock for user: {}", user.getId());
            user.resetFailedLoginAttempts();
            userRepository.save(user);
        }

        // Check if account is still locked after auto-unlock check
        if (user.isAccountLocked()) {
            throw new AccountLockedException();
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.incrementFailedLoginAttempts(MAX_FAILED_LOGIN_ATTEMPTS, ACCOUNT_LOCK_DURATION_MINUTES);
            userRepository.save(user);
            throw new InvalidCredentialsException();
        }

        // Security Fix: PENDING users cannot login — only ACTIVE users allowed
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new UserServiceException(
                "Account is not active. Status: " + user.getStatus() + ". Please verify your email and phone.",
                org.springframework.http.HttpStatus.FORBIDDEN,
                "ACCOUNT_NOT_ACTIVE"
            );
        }

        // Record successful login
        user.recordSuccessfulLogin();
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("User logged in successfully: {}", user.getId());

        return AuthResponse.of(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpiration(),
            UserResponse.fromEntity(user)
        );
    }

    /**
     * Gets a user by ID with caching.
     * 
     * @param id user ID
     * @return user response
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(UUID id) {
        log.debug("Fetching user by id: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        
        return UserResponse.fromEntity(user);
    }

    /**
     * Gets a user by email.
     * 
     * @param email user email
     * @return user response
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        
        return UserResponse.fromEntity(user);
    }

    /**
     * Updates a user's profile.
     * 
     * @param id user ID
     * @param request update request
     * @return updated user response
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    public UserResponse updateProfile(UUID id, UserUpdateRequest request) {
        log.info("Updating profile for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // Check if phone is being changed and if it's already taken
        if (request.getPhone() != null && 
            !request.getPhone().equals(user.getPhone()) && 
            userRepository.existsByPhone(request.getPhone())) {
            throw new UserServiceException(
                "Phone number already in use",
                org.springframework.http.HttpStatus.CONFLICT,
                "PHONE_IN_USE"
            );
        }

        // Update fields
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        user = userRepository.save(user);
        log.info("User profile updated: {}", id);

        return UserResponse.fromEntity(user);
    }

    /**
     * Soft deletes a user account.
     * 
     * @param id user ID
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public void deleteAccount(UUID id) {
        log.info("Deleting account for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        user.softDelete();
        userRepository.save(user);

        log.info("User account soft deleted: {}", id);
    }

    /**
     * Verifies a user's email.
     * 
     * @param id user ID
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    public UserResponse verifyEmail(UUID id) {
        log.info("Verifying email for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        user.setEmailVerified(true);
        
        // Activate account if phone is also verified or not required
        if (user.getPhone() == null || user.getPhoneVerified()) {
            user.setStatus(User.UserStatus.ACTIVE);
        }

        user = userRepository.save(user);
        log.info("Email verified for user: {}", id);

        return UserResponse.fromEntity(user);
    }

    /**
     * Verifies a user's phone.
     * 
     * @param id user ID
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    @CachePut(value = "users", key = "#id")
    public UserResponse verifyPhone(UUID id) {
        log.info("Verifying phone for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        user.setPhoneVerified(true);
        
        // Activate account if email is also verified
        if (user.getEmailVerified()) {
            user.setStatus(User.UserStatus.ACTIVE);
        }

        user = userRepository.save(user);
        log.info("Phone verified for user: {}", id);

        return UserResponse.fromEntity(user);
    }

    /**
     * Validates a JWT token and returns the user.
     * 
     * @param token JWT token
     * @return user response
     */
    @Transactional(readOnly = true)
    public UserResponse validateToken(String token) {
        String userId = jwtService.extractUserId(token);
        
        if (userId == null) {
            throw new UserServiceException(
                "Invalid token",
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN"
            );
        }

        return getUserById(UUID.fromString(userId));
    }

    /**
     * Refreshes an access token using a refresh token.
     * 
     * @param refreshToken refresh token
     * @return new authentication response
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new UserServiceException(
                "Invalid or expired refresh token",
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN"
            );
        }

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException(UUID.fromString(userId)));

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.of(
            newAccessToken,
            newRefreshToken,
            jwtService.getAccessTokenExpiration(),
            UserResponse.fromEntity(user)
        );
    }
}
