package com.minibank.account.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Internal Authentication Filter for Account Service.
 *
 * <p>Validates the {@code X-Internal-Token} header on incoming requests to ensure
 * they originate from the API Gateway. Requests without a valid token are rejected
 * with HTTP 401, preventing unauthorized direct access to the service.</p>
 *
 * <p>This filter runs at {@link Ordered#HIGHEST_PRECEDENCE} to ensure internal auth
 * is checked before Spring Security's filter chain processes the request.</p>
 *
 * <p>Can be disabled via configuration: {@code internal.auth.enabled=false}.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "internal.auth.enabled", havingValue = "true", matchIfMissing = true)
public class InternalAuthFilter implements Filter {

    @Value("${internal.auth.secret}")
    private String internalSecret;

    @Value("${internal.auth.token-ttl:300000}")
    private long tokenTtl;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Skip internal auth for health check, actuator, swagger and public endpoints
        if (path.startsWith("/actuator") || path.startsWith("/health")
                || path.endsWith("/health") || path.contains("swagger")
                || path.contains("api-docs") || path.contains("/accounts")) {
            chain.doFilter(request, response);
            return;
        }

        String token = httpRequest.getHeader("X-Internal-Token");

        if (token == null || !validateToken(token, path)) {
            log.warn("Invalid or missing internal auth token for path: {}", path);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"code\":401,\"message\":\"Unauthorized: Invalid internal token\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Validates the internal auth token by checking TTL and HMAC signature.
     */
    private boolean validateToken(String token, String requestPath) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int colonIndex = decoded.indexOf(':');
            if (colonIndex < 0) {
                return false;
            }

            long timestamp = Long.parseLong(decoded.substring(0, colonIndex));

            // Token TTL check
            if (System.currentTimeMillis() - timestamp > tokenTtl) {
                log.warn("Internal token expired for path: {}", requestPath);
                return false;
            }

            // HMAC verification
            String data = timestamp + ":" + requestPath;
            String expectedHmac = calculateHmac(data);
            String actualHmac = decoded.substring(colonIndex + 1);

            return MessageDigest.isEqual(
                    expectedHmac.getBytes(StandardCharsets.UTF_8),
                    actualHmac.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Internal token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Calculates HMAC-SHA256 for the given data using the internal secret.
     */
    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC calculation failed", e);
        }
    }
}
