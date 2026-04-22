package com.minibank.gateway;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * MiniBank API Gateway Application
 *
 * Features:
 * - Spring Cloud Gateway for routing
 * - JWT Authentication for all services
 * - Rate limiting with Redis
 * - CORS configuration
 * - Request logging
 * - Distributed tracing with Zipkin
 * - Inter-service authentication with HMAC internal tokens
 */
@Slf4j
@SpringBootApplication
public class ApiGatewayApplication {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${internal.auth.secret}")
    private String internalAuthSecret;

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
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

    /**
     * Key resolver for rate limiting.
     * Uses client IP address as the key.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : UUID.randomUUID().toString();
            return Mono.just(ip);
        };
    }
}
