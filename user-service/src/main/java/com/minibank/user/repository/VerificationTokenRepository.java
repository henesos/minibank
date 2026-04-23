package com.minibank.user.repository;

import com.minibank.user.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByUserIdAndTypeAndUsedFalse(UUID userId, VerificationToken.TokenType type);

    Optional<VerificationToken> findByTokenAndType(String token, VerificationToken.TokenType type);

    boolean existsByUserIdAndTypeAndUsedFalse(UUID userId, VerificationToken.TokenType type);
}