package com.minibank.user.service;

import com.minibank.user.entity.VerificationToken;
import com.minibank.user.exception.UserServiceException;
import com.minibank.user.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository verificationTokenRepository;
    private static final int TOKEN_EXPIRATION_MINUTES = 15;
    private static final String TOKEN_CHARACTERS = "0123456789";
    private static final int TOKEN_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public VerificationToken createToken(UUID userId, VerificationToken.TokenType type) {
        if (verificationTokenRepository.existsByUserIdAndTypeAndUsedFalse(userId, type)) {
            throw new UserServiceException(
                "Active verification token already exists for this " + type.name().toLowerCase(),
                org.springframework.http.HttpStatus.CONFLICT,
                "TOKEN_EXISTS"
            );
        }

        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES);

        VerificationToken verificationToken = VerificationToken.builder()
                .userId(userId)
                .type(type)
                .token(token)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        verificationToken = verificationTokenRepository.save(verificationToken);
        log.info("Created {} verification token for user: {}", type, userId);

        sendVerificationCode(userId, type, token);

        return verificationToken;
    }

    @Transactional
    public VerificationToken validateToken(UUID userId, String code, VerificationToken.TokenType type) {
        VerificationToken token = verificationTokenRepository.findByTokenAndType(code, type)
                .orElseThrow(() -> new UserServiceException(
                    "Invalid verification code",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_CODE"
                ));

        if (!token.getUserId().equals(userId)) {
            throw new UserServiceException(
                "Invalid verification code",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_CODE"
            );
        }

        if (token.getUsed()) {
            throw new UserServiceException(
                "Verification code has already been used",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "CODE_ALREADY_USED"
            );
        }

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new UserServiceException(
                "Verification code has expired",
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "CODE_EXPIRED"
            );
        }

        return token;
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            int index = random.nextInt(TOKEN_CHARACTERS.length());
            sb.append(TOKEN_CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    private void sendVerificationCode(UUID userId, VerificationToken.TokenType type, String token) {
        if (type == VerificationToken.TokenType.EMAIL) {
            log.info("[MOCK EMAIL] Sending email verification code {} to user {}", token, userId);
        } else {
            log.info("[MOCK SMS] Sending phone verification code {} to user {}", token, userId);
        }
    }
}