package com.minibank.user.unit;

import com.minibank.user.entity.User;
import com.minibank.user.entity.VerificationToken;
import com.minibank.user.exception.UserNotFoundException;
import com.minibank.user.repository.UserRepository;
import com.minibank.user.repository.VerificationTokenRepository;
import com.minibank.user.service.UserService;
import com.minibank.user.service.VerificationTokenService;
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

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VerificationTokenService verificationTokenService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@minibank.com";

        testUser = User.builder()
                .id(testUserId)
                .email(testEmail)
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .status(User.UserStatus.PENDING)
                .emailVerified(false)
                .phoneVerified(false)
                .failedLoginAttempts(0)
                .build();
    }

    @Nested
    @DisplayName("Create Token")
    class CreateTokenTests {

        @Test
        @DisplayName("Should create email verification token successfully")
        void createToken_Email_Success() {
            when(verificationTokenRepository.existsByUserIdAndTypeAndUsedFalse(testUserId, VerificationToken.TokenType.EMAIL))
                    .thenReturn(false);
            when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(invocation -> {
                VerificationToken token = invocation.getArgument(0);
                return token;
            });

            VerificationToken token = verificationTokenService.createToken(testUserId, VerificationToken.TokenType.EMAIL);

            assertNotNull(token);
            assertEquals(testUserId, token.getUserId());
            assertEquals(VerificationToken.TokenType.EMAIL, token.getType());
            assertEquals(6, token.getToken().length());
            assertFalse(token.getUsed());
            verify(verificationTokenRepository).save(any(VerificationToken.class));
        }

        @Test
        @DisplayName("Should create phone verification token successfully")
        void createToken_Phone_Success() {
            when(verificationTokenRepository.existsByUserIdAndTypeAndUsedFalse(testUserId, VerificationToken.TokenType.PHONE))
                    .thenReturn(false);
            when(verificationTokenRepository.save(any(VerificationToken.class))).thenAnswer(invocation -> {
                VerificationToken token = invocation.getArgument(0);
                return token;
            });

            VerificationToken token = verificationTokenService.createToken(testUserId, VerificationToken.TokenType.PHONE);

            assertNotNull(token);
            assertEquals(testUserId, token.getUserId());
            assertEquals(VerificationToken.TokenType.PHONE, token.getType());
            assertEquals(6, token.getToken().length());
            assertFalse(token.getUsed());
            verify(verificationTokenRepository).save(any(VerificationToken.class));
        }

        @Test
        @DisplayName("Should throw exception when token already exists")
        void createToken_AlreadyExists_ThrowsException() {
            when(verificationTokenRepository.existsByUserIdAndTypeAndUsedFalse(testUserId, VerificationToken.TokenType.EMAIL))
                    .thenReturn(true);

            assertThrows(Exception.class, () ->
                    verificationTokenService.createToken(testUserId, VerificationToken.TokenType.EMAIL));

            verify(verificationTokenRepository, never()).save(any(VerificationToken.class));
        }
    }

    @Nested
    @DisplayName("Validate Token")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should validate token successfully with valid code")
        void validateToken_ValidCode_Success() {
            String validCode = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token(validCode)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            when(verificationTokenRepository.findByTokenAndType(validCode, VerificationToken.TokenType.EMAIL))
                    .thenReturn(Optional.of(token));

            VerificationToken validated = verificationTokenService.validateToken(testUserId, validCode, VerificationToken.TokenType.EMAIL);

            assertNotNull(validated);
            assertEquals(testUserId, validated.getUserId());
            assertEquals(validCode, validated.getToken());
        }

        @Test
        @DisplayName("Should throw 400 for invalid code")
        void validateToken_InvalidCode_Throws400() {
            String invalidCode = "000000";
            when(verificationTokenRepository.findByTokenAndType(invalidCode, VerificationToken.TokenType.EMAIL))
                    .thenReturn(Optional.empty());

            assertThrows(Exception.class, () ->
                    verificationTokenService.validateToken(testUserId, invalidCode, VerificationToken.TokenType.EMAIL));
        }

        @Test
        @DisplayName("Should throw 400 for expired code")
        void validateToken_ExpiredCode_Throws400() {
            String expiredCode = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token(expiredCode)
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .used(false)
                    .build();

            when(verificationTokenRepository.findByTokenAndType(expiredCode, VerificationToken.TokenType.EMAIL))
                    .thenReturn(Optional.of(token));

            assertThrows(Exception.class, () ->
                    verificationTokenService.validateToken(testUserId, expiredCode, VerificationToken.TokenType.EMAIL));
        }

        @Test
        @DisplayName("Should throw 400 for already used code")
        void validateToken_UsedCode_Throws400() {
            String usedCode = "123456";
            VerificationToken token = VerificationToken.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .type(VerificationToken.TokenType.EMAIL)
                    .token(usedCode)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(true)
                    .build();

            when(verificationTokenRepository.findByTokenAndType(usedCode, VerificationToken.TokenType.EMAIL))
                    .thenReturn(Optional.of(token));

            assertThrows(Exception.class, () ->
                    verificationTokenService.validateToken(testUserId, usedCode, VerificationToken.TokenType.EMAIL));
        }
    }
}