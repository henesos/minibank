package com.minibank.user;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MiniBank User Service Application
 *
 * Handles user registration, authentication, and profile management.
 * Runs on port 8081 by default.
 *
 * @author MiniBank Team
 */
@Slf4j
@SpringBootApplication
@EnableJpaAuditing
public class UserServiceApplication {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${internal.auth.secret}")
    private String internalAuthSecret;

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    /**
     * Validates that JWT_SECRET and INTERNAL_AUTH_SECRET environment variables
     * are properly set at application startup. Fails fast if misconfigured.
     */
    @PostConstruct
    public void validateSecrets() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters. Set JWT_SECRET environment variable.");
        }
        log.info("JWT_SECRET validated successfully (length: {})", jwtSecret.length());

        if (internalAuthSecret == null || internalAuthSecret.length() < 32) {
            throw new IllegalStateException(
                "INTERNAL_AUTH_SECRET must be at least 32 characters. Set INTERNAL_AUTH_SECRET environment variable.");
        }
        log.info("INTERNAL_AUTH_SECRET validated successfully (length: {})", internalAuthSecret.length());
    }
}
