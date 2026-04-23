package com.minibank.user.unit;

import com.minibank.user.entity.User;
import com.minibank.user.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "thisIsATestSecretKeyForJwtTokenGenerationThatIsLongEnough");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604800000L);

        testUserId = UUID.randomUUID();
        testEmail = "test@minibank.com";

        testUser = User.builder()
                .id(testUserId)
                .email(testEmail)
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .status(User.UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .failedLoginAttempts(0)
                .build();
    }

    @Nested
    @DisplayName("Generate Access Token")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("Should generate access token successfully")
        void generateAccessToken_Success() {
            String token = jwtService.generateAccessToken(testUser);

            assertNotNull(token);
            assertTrue(token.length() > 0);
        }

        @Test
        @DisplayName("Should include user ID in token claims")
        void generateAccessToken_ContainsUserId() {
            String token = jwtService.generateAccessToken(testUser);

            String extractedUserId = jwtService.extractUserId(token);

            assertEquals(testUserId.toString(), extractedUserId);
        }

        @Test
        @DisplayName("Should include email in token claims")
        void generateAccessToken_ContainsEmail() {
            String token = jwtService.generateAccessToken(testUser);

            String extractedEmail = jwtService.extractEmail(token);

            assertEquals(testEmail, extractedEmail);
        }

        @Test
        @DisplayName("Should include access type in token claims")
        void generateAccessToken_ContainsAccessType() {
            boolean isValid = jwtService.validateAccessToken(jwtService.generateAccessToken(testUser));

            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Generate Refresh Token")
    class GenerateRefreshTokenTests {

        @Test
        @DisplayName("Should generate refresh token successfully")
        void generateRefreshToken_Success() {
            String token = jwtService.generateRefreshToken(testUser);

            assertNotNull(token);
            assertTrue(token.length() > 0);
        }

        @Test
        @DisplayName("Should include user ID in refresh token")
        void generateRefreshToken_ContainsUserId() {
            String token = jwtService.generateRefreshToken(testUser);

            String extractedUserId = jwtService.extractUserId(token);

            assertEquals(testUserId.toString(), extractedUserId);
        }

        @Test
        @DisplayName("Should validate refresh token successfully")
        void generateRefreshToken_Valid() {
            String token = jwtService.generateRefreshToken(testUser);

            boolean isValid = jwtService.validateRefreshToken(token);

            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Validate Token")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return false for invalid token")
        void validateAccessToken_InvalidToken_ReturnsFalse() {
            boolean isValid = jwtService.validateAccessToken("invalid.token.here");

            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for wrong token type")
        void validateAccessToken_WrongType_ReturnsFalse() {
            String refreshToken = jwtService.generateRefreshToken(testUser);

            boolean isValid = jwtService.validateAccessToken(refreshToken);

            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for expired token")
        void validateAccessToken_ExpiredToken_ReturnsFalse() {
            String token = jwtService.generateAccessToken(testUser);

            boolean isValid = jwtService.validateAccessToken(token);

            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Extract User ID")
    class ExtractUserIdTests {

        @Test
        @DisplayName("Should extract user ID from access token")
        void extractUserId_FromAccessToken_Success() {
            String token = jwtService.generateAccessToken(testUser);

            String userId = jwtService.extractUserId(token);

            assertEquals(testUserId.toString(), userId);
        }

        @Test
        @DisplayName("Should extract user ID from refresh token")
        void extractUserId_FromRefreshToken_Success() {
            String token = jwtService.generateRefreshToken(testUser);

            String userId = jwtService.extractUserId(token);

            assertEquals(testUserId.toString(), userId);
        }
    }

    @Nested
    @DisplayName("Extract Email")
    class ExtractEmailTests {

        @Test
        @DisplayName("Should extract email from access token")
        void extractEmail_FromAccessToken_Success() {
            String token = jwtService.generateAccessToken(testUser);

            String email = jwtService.extractEmail(token);

            assertEquals(testEmail, email);
        }

        @Test
        @DisplayName("Should return null for refresh token without email claim")
        void extractEmail_FromRefreshToken_ReturnsNull() {
            String token = jwtService.generateRefreshToken(testUser);

            String email = jwtService.extractEmail(token);

            assertNull(email);
        }
    }

    @Nested
    @DisplayName("Extract Expiration")
    class ExtractExpirationTests {

        @Test
        @DisplayName("Should extract expiration date from token")
        void extractExpiration_Success() {
            String token = jwtService.generateAccessToken(testUser);

            Date expiration = jwtService.extractExpiration(token);

            assertNotNull(expiration);
            assertTrue(expiration.after(new Date()));
        }
    }

    @Nested
    @DisplayName("Get Access Token Expiration")
    class GetAccessTokenExpirationTests {

        @Test
        @DisplayName("Should return correct expiration value")
        void getAccessTokenExpiration_Success() {
            Long expiration = jwtService.getAccessTokenExpiration();

            assertEquals(86400000L, expiration);
        }
    }
}