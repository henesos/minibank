package com.minibank.user.unit;

import com.minibank.user.dto.*;
import com.minibank.user.entity.User;
import com.minibank.user.entity.VerificationToken;
import com.minibank.user.exception.*;
import com.minibank.user.repository.UserRepository;
import com.minibank.user.repository.VerificationTokenRepository;
import com.minibank.user.service.UserService;
import com.minibank.user.service.VerificationTokenService;
import com.minibank.user.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for UserService.
 * 
 * Tests business logic in isolation using Mockito mocks.
 * Follows TDD Red-Green-Refactor cycle.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private VerificationTokenService verificationTokenService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;
    private String testPassword;
    private String testPasswordHash;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@minibank.com";
        testPassword = "Password123!";
        testPasswordHash = "$2a$12$hashedpassword";

        testUser = User.builder()
                .id(testUserId)
                .email(testEmail)
                .passwordHash(testPasswordHash)
                .firstName("Test")
                .lastName("User")
                .status(User.UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Registration Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should register user successfully with valid data")
        void register_Success() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .firstName("Test")
                    .lastName("User")
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(false);
            when(passwordEncoder.encode(testPassword)).thenReturn(testPasswordHash);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            UserResponse response = userService.register(request);

            // Assert
            assertNotNull(response);
            assertEquals(testEmail, response.getEmail());
            assertEquals("Test", response.getFirstName());
            assertEquals("User", response.getLastName());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void register_EmailExists_ThrowsException() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(true);

            // Act & Assert
            assertThrows(EmailAlreadyExistsException.class, 
                () -> userService.register(request));
            
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should set user status to PENDING on registration")
        void register_SetsPendingStatus() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(false);
            when(passwordEncoder.encode(testPassword)).thenReturn(testPasswordHash);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                return savedUser;
            });

            // Act
            userService.register(request);

            // Assert
            verify(userRepository).save(argThat(user -> 
                user.getStatus() == User.UserStatus.PENDING
            ));
        }

        @Test
        @DisplayName("Should throw exception when phone already exists")
        void register_PhoneExists_ThrowsException() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .phone("5551234567")
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(false);
            when(userRepository.existsByPhone("5551234567")).thenReturn(true);

            // Act & Assert
            assertThrows(UserServiceException.class, 
                () -> userService.register(request));
        }

        @Test
        @DisplayName("Should throw exception when nationalId already exists")
        void register_NationalIdExists_ThrowsException() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .nationalId("12345678901")
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(false);
            when(userRepository.findByNationalId("12345678901")).thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThrows(UserServiceException.class,
                () -> userService.register(request));
        }

        @Test
        @DisplayName("Should register successfully with nationalId")
        void register_WithNationalId_Success() {
            // Arrange
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .nationalId("12345678901")
                    .build();

            when(userRepository.existsByEmail(testEmail)).thenReturn(false);
            when(userRepository.findByNationalId("12345678901")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(testPassword)).thenReturn(testPasswordHash);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            UserResponse response = userService.register(request);

            // Assert
            assertNotNull(response);
            verify(userRepository).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Login Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Login")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            // Arrange
            UserLoginRequest request = UserLoginRequest.builder()
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            when(userRepository.findByEmailIgnoreCase(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(testPassword, testPasswordHash)).thenReturn(true);
            when(jwtService.generateAccessToken(testUser)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token");
            when(jwtService.getAccessTokenExpiration()).thenReturn(86400000L);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            AuthResponse response = userService.login(request);

            // Assert
            assertNotNull(response);
            assertEquals("access-token", response.getAccessToken());
            assertEquals("refresh-token", response.getRefreshToken());
            assertNotNull(response.getUser());
        }

        @Test
        @DisplayName("Should throw exception for invalid email")
        void login_InvalidEmail_ThrowsException() {
            // Arrange
            UserLoginRequest request = UserLoginRequest.builder()
                    .email("wrong@email.com")
                    .password(testPassword)
                    .build();

            when(userRepository.findByEmailIgnoreCase("wrong@email.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));
        }

        @Test
        @DisplayName("Should throw exception for invalid password")
        void login_InvalidPassword_ThrowsException() {
            // Arrange
            UserLoginRequest request = UserLoginRequest.builder()
                    .email(testEmail)
                    .password("wrongpassword")
                    .build();

            when(userRepository.findByEmailIgnoreCase(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));
        }

        @Test
        @DisplayName("Should increment failed attempts on wrong password")
        void login_WrongPassword_IncrementsAttempts() {
            // Arrange
            UserLoginRequest request = UserLoginRequest.builder()
                    .email(testEmail)
                    .password("wrongpassword")
                    .build();

            when(userRepository.findByEmailIgnoreCase(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));

            // Verify failed attempts were incremented
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should lock account after max failed attempts")
        void login_MaxFailedAttempts_LocksAccount() {
            // Arrange
            testUser.setFailedLoginAttempts(4); // One more attempt will lock

            UserLoginRequest request = UserLoginRequest.builder()
                    .email(testEmail)
                    .password("wrongpassword")
                    .build();

            when(userRepository.findByEmailIgnoreCase(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));

            // Verify account was locked
            verify(userRepository).save(any(User.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get User Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get User")
    class GetUserTests {

        @Test
        @DisplayName("Should return user when found by ID")
        void getUserById_Success() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            // Act
            UserResponse response = userService.getUserById(testUserId);

            // Assert
            assertNotNull(response);
            assertEquals(testUserId, response.getId());
            assertEquals(testEmail, response.getEmail());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void getUserById_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.getUserById(nonExistentId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Update User Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Update User")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update user profile successfully")
        void updateProfile_Success() {
            // Arrange
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .firstName("Updated")
                    .lastName("Name")
                    .build();

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            UserResponse response = userService.updateProfile(testUserId, request);

            // Assert
            assertNotNull(response);
            verify(userRepository).save(argThat(user -> 
                "Updated".equals(user.getFirstName()) && 
                "Name".equals(user.getLastName())
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Delete User Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Delete User")
    class DeleteUserTests {

        @Test
        @DisplayName("Should soft delete user successfully")
        void deleteAccount_Success() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.deleteAccount(testUserId);

            // Assert
            verify(userRepository).save(argThat(user -> 
                user.getDeleted() == true && 
                user.getStatus() == User.UserStatus.CLOSED
            ));
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent user")
        void deleteAccount_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.deleteAccount(nonExistentId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Verify Email Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Verify Email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully")
        void verifyEmail_Success() {
            // Arrange
            String code = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token(code)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            when(verificationTokenService.validateToken(testUserId, code, VerificationToken.TokenType.EMAIL)).thenReturn(token);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(verificationTokenRepository.save(any(VerificationToken.class))).thenReturn(token);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            UserResponse response = userService.verifyEmail(testUserId, code);

            // Assert
            assertNotNull(response);
            verify(verificationTokenService).validateToken(testUserId, code, VerificationToken.TokenType.EMAIL);
        }

        @Test
        @DisplayName("Should throw exception when user not found for email verification")
        void verifyEmail_UserNotFound_ThrowsException() {
            // Arrange
            String code = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token(code)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            when(verificationTokenService.validateToken(testUserId, code, VerificationToken.TokenType.EMAIL)).thenReturn(token);
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.verifyEmail(testUserId, code));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Verify Phone Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Verify Phone")
    class VerifyPhoneTests {

        @Test
        @DisplayName("Should verify phone successfully")
        void verifyPhone_Success() {
            // Arrange
            String code = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.PHONE)
                    .token(code)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            when(verificationTokenService.validateToken(testUserId, code, VerificationToken.TokenType.PHONE)).thenReturn(token);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(verificationTokenRepository.save(any(VerificationToken.class))).thenReturn(token);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            UserResponse response = userService.verifyPhone(testUserId, code);

            // Assert
            assertNotNull(response);
            verify(verificationTokenService).validateToken(testUserId, code, VerificationToken.TokenType.PHONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Get User By Email Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get User By Email")
    class GetUserByEmailTests {

        @Test
        @DisplayName("Should return user when found by email")
        void getUserByEmail_Success() {
            // Arrange
            when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

            // Act
            UserResponse response = userService.getUserByEmail(testEmail);

            // Assert
            assertNotNull(response);
            assertEquals(testEmail, response.getEmail());
        }

        @Test
        @DisplayName("Should throw exception when user not found by email")
        void getUserByEmail_NotFound_ThrowsException() {
            // Arrange
            when(userRepository.findByEmail("notfound@email.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.getUserByEmail("notfound@email.com"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Request Verification Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Request Verification")
    class RequestVerificationTests {

        @Test
        @DisplayName("Should request email verification")
        void requestEmailVerification_Success() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            // Act
            userService.requestEmailVerification(testUserId);

            // Assert
            verify(verificationTokenService).createToken(testUserId, VerificationToken.TokenType.EMAIL);
        }

        @Test
        @DisplayName("Should throw exception when requesting email verification for non-existent user")
        void requestEmailVerification_UserNotFound_ThrowsException() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.requestEmailVerification(testUserId));
        }

        @Test
        @DisplayName("Should request phone verification")
        void requestPhoneVerification_Success() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            // Act
            userService.requestPhoneVerification(testUserId);

            // Assert
            verify(verificationTokenService).createToken(testUserId, VerificationToken.TokenType.PHONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Update Profile Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Update Profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update phone successfully")
        void updateProfile_ChangePhone_Success() {
            // Arrange
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .phone("5551234567")
                    .build();

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByPhone("5551234567")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.updateProfile(testUserId, request);

            // Assert
            verify(userRepository).save(argThat(user -> 
                "5551234567".equals(user.getPhone())
            ));
        }

        @Test
        @DisplayName("Should throw exception when new phone is already in use")
        void updateProfile_PhoneInUse_ThrowsException() {
            // Arrange
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .phone("5551234567")
                    .build();

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByPhone("5551234567")).thenReturn(true);

            // Act & Assert
            assertThrows(UserServiceException.class, 
                () -> userService.updateProfile(testUserId, request));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent user profile")
        void updateProfile_UserNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .firstName("New")
                    .build();

            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, 
                () -> userService.updateProfile(nonExistentId, request));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Validate Token Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validate Token")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should validate token successfully with valid JWT")
        void validateToken_ValidToken_Success() {
            String validToken = "valid.jwt.token";
            String userId = testUserId.toString();

            when(jwtService.extractUserId(validToken)).thenReturn(userId);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            UserResponse response = userService.validateToken(validToken);

            assertNotNull(response);
            assertEquals(testUserId, response.getId());
        }

        @Test
        @DisplayName("Should throw exception for invalid token")
        void validateToken_InvalidToken_ThrowsException() {
            String invalidToken = "invalid.token";

            when(jwtService.extractUserId(invalidToken)).thenReturn(null);

            assertThrows(UserServiceException.class,
                () -> userService.validateToken(invalidToken));
        }

        @Test
        @DisplayName("Should throw exception when user not found for valid token")
        void validateToken_UserNotFound_ThrowsException() {
            String validToken = "valid.jwt.token";

            when(jwtService.extractUserId(validToken)).thenReturn(testUserId.toString());
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                () -> userService.validateToken(validToken));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Refresh Token Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh token successfully with valid refresh token")
        void refreshToken_ValidToken_Success() {
            String refreshToken = "valid.refresh.token";

            when(jwtService.validateRefreshToken(refreshToken)).thenReturn(true);
            when(jwtService.extractUserId(refreshToken)).thenReturn(testUserId.toString());
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
            when(jwtService.getAccessTokenExpiration()).thenReturn(86400000L);

            AuthResponse response = userService.refreshToken(refreshToken);

            assertNotNull(response);
            assertEquals("new-access-token", response.getAccessToken());
            assertEquals("new-refresh-token", response.getRefreshToken());
        }

        @Test
        @DisplayName("Should throw exception for invalid refresh token")
        void refreshToken_InvalidToken_ThrowsException() {
            String invalidToken = "invalid.refresh.token";

            when(jwtService.validateRefreshToken(invalidToken)).thenReturn(false);

            assertThrows(UserServiceException.class,
                () -> userService.refreshToken(invalidToken));
        }

        @Test
        @DisplayName("Should throw exception when user not found for valid refresh token")
        void refreshToken_UserNotFound_ThrowsException() {
            String refreshToken = "valid.refresh.token";

            when(jwtService.validateRefreshToken(refreshToken)).thenReturn(true);
            when(jwtService.extractUserId(refreshToken)).thenReturn(testUserId.toString());
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                () -> userService.refreshToken(refreshToken));
        }
    }
}
