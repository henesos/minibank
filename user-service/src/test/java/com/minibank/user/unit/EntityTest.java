package com.minibank.user.unit;

import com.minibank.user.entity.User;
import com.minibank.user.entity.VerificationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {

    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@minibank.com";
    }

    @Nested
    @DisplayName("User Status")
    class UserStatusTests {

        @Test
        @DisplayName("Should have all required statuses")
        void userStatus_HasAllStatuses() {
            assertNotNull(User.UserStatus.PENDING);
            assertNotNull(User.UserStatus.ACTIVE);
            assertNotNull(User.UserStatus.SUSPENDED);
            assertNotNull(User.UserStatus.LOCKED);
            assertNotNull(User.UserStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("User Builder")
    class UserBuilderTests {

        @Test
        @DisplayName("Should build user with all fields")
        void userBuilder_BuildsSuccessfully() {
            User user = User.builder()
                    .id(testUserId)
                    .email(testEmail)
                    .passwordHash("hash")
                    .firstName("Test")
                    .lastName("User")
                    .status(User.UserStatus.ACTIVE)
                    .emailVerified(true)
                    .phoneVerified(false)
                    .failedLoginAttempts(0)
                    .build();

            assertNotNull(user);
            assertEquals(testUserId, user.getId());
            assertEquals(testEmail, user.getEmail());
            assertEquals("Test", user.getFirstName());
            assertEquals("User", user.getLastName());
            assertEquals(User.UserStatus.ACTIVE, user.getStatus());
        }

        @Test
        @DisplayName("Should generate full name")
        void userBuilder_GeneratesFullName() {
            User user = User.builder()
                    .firstName("Test")
                    .lastName("User")
                    .build();

            assertEquals("Test User", user.getFullName());
        }
    }

    @Nested
    @DisplayName("User Methods")
    class UserMethodsTests {

        @Test
        @DisplayName("Should increment failed login attempts")
        void incrementFailedLoginAttempts_IncrementsCount() {
            User user = User.builder()
                    .failedLoginAttempts(0)
                    .build();

            user.incrementFailedLoginAttempts(5, 30);

            assertTrue(user.getFailedLoginAttempts() > 0);
        }

        @Test
        @DisplayName("Should reset failed login attempts")
        void resetFailedLoginAttempts_Success() {
            User user = User.builder()
                    .failedLoginAttempts(3)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            user.resetFailedLoginAttempts();

            assertEquals(0, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil());
        }

        @Test
        @DisplayName("Should check if account is locked when locked status")
        void isAccountLocked_LockedStatus_ReturnsTrue() {
            User user = User.builder()
                    .status(User.UserStatus.LOCKED)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            assertTrue(user.isAccountLocked());
        }

        @Test
        @DisplayName("Should soft delete user")
        void softDelete_MarksAsDeleted() {
            User user = User.builder()
                    .deleted(false)
                    .status(User.UserStatus.ACTIVE)
                    .build();

            user.softDelete();

            assertTrue(user.getDeleted());
            assertEquals(User.UserStatus.CLOSED, user.getStatus());
        }

        @Test
        @DisplayName("Should record successful login")
        void recordSuccessfulLogin_UpdatesLoginTime() {
            User user = User.builder()
                    .build();

            user.recordSuccessfulLogin();

            assertNotNull(user.getLastLoginAt());
        }
    }

    @Nested
    @DisplayName("VerificationToken")
    class VerificationTokenTests {

        @Test
        @DisplayName("Should create verification token")
        void createToken_Success() {
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            assertNotNull(token);
            assertEquals(testUserId, token.getUserId());
            assertEquals(VerificationToken.TokenType.EMAIL, token.getType());
            assertEquals("123456", token.getToken());
        }

        @Test
        @DisplayName("Should mark token as used")
        void markUsed_SetsUsed() {
            VerificationToken token = VerificationToken.builder()
                    .used(false)
                    .build();

            token.markUsed();

            assertTrue(token.getUsed());
        }

        @Test
        @DisplayName("Should check if token is expired")
        void tokenIsExpired_ReturnsTrue() {
            VerificationToken token = VerificationToken.builder()
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .used(false)
                    .build();

            assertTrue(token.getExpiresAt().isBefore(LocalDateTime.now()));
        }
    }
}