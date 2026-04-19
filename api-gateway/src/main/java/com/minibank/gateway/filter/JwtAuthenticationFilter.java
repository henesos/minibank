package com.minibank.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Authentication Filter for API Gateway
 * 
 * Validates JWT tokens on incoming requests and adds user information
 * to request headers for downstream services.
 * 
 * Public endpoints (no authentication required):
 * - /api/auth/login
 * - /api/auth/register
 * - /actuator/**
 * - /swagger-ui/**
 * - /v3/api-docs/**
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/users/login",      // Login endpoint
            "/api/v1/users/register",   // Register endpoint
            "/api/auth/login",          // Legacy login
            "/api/auth/register",       // Legacy register
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-user",
            "/swagger-account",
            "/swagger-transaction",
            "/swagger-notification",
            "/fallback",
            "/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip authentication for CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            log.debug("OPTIONS request, skipping authentication: {}", path);
            return chain.filter(exchange);
        }

        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            log.debug("Public endpoint, skipping authentication: {}", path);
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            // Validate and parse token
            Claims claims = validateToken(token);
            
            // Extract user information
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            log.debug("Token validated for user: {} (role: {})", email, role);

            // Add user headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-ID", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return onError(exchange, "JWT token has expired", HttpStatus.UNAUTHORIZED);
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            return onError(exchange, "Invalid JWT signature", HttpStatus.UNAUTHORIZED);
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            return onError(exchange, "Malformed JWT token", HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return onError(exchange, "JWT validation failed", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Validates the JWT token and returns the claims.
     */
    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if the path is a public endpoint.
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(path::startsWith);
    }

    /**
     * Return error response for authentication failures.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        
        String errorBody = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()
        );
        
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(errorBody.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }
}
