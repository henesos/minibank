package com.minibank.user.unit;

import com.minibank.user.dto.*;
import com.minibank.user.entity.User;
import com.minibank.user.exception.EmailAlreadyExistsException;
import com.minibank.user.exception.InvalidCredentialsException;
import com.minibank.user.exception.UserNotFoundException;
import com.minibank.user.repository.UserRepository;
import com.minibank.user.service.JwtService;
import com.minibank.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

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

            when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
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

            when(userRepository.findByEmail("wrong@email.com")).thenReturn(Optional.empty());

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

            when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

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

            when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));

            // Verify failed attempts were incremented
            verify(userRepository).save(argThat(user -> 
                user.getFailedLoginAttempts() == 1
            ));
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

            when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", testPasswordHash)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, 
                () -> userService.login(request));

            // Verify account was locked
            verify(userRepository).save(argThat(user -> 
                user.getStatus() == User.UserStatus.LOCKED
            ));
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
}
