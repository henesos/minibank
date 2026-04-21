package com.minibank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for API Gateway
 *
 * Restricts cross-origin access to known frontend origins only.
 * In production, set CORS_ALLOWED_ORIGINS environment variable to
 * the comma-separated list of allowed domains (e.g. "https://minibank.com,https://app.minibank.com").
 *
 * Security: allowedOriginPatterns("*") is NOT used — all origins must be explicitly listed.
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of additional allowed origins (e.g. production domains).
     * Set via CORS_ALLOWED_ORIGINS environment variable.
     */
    @Value("${cors.allowed-origins:}")
    private String extraAllowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Build the list of allowed origins from static + env-var sources
        List<String> allowedOrigins = new java.util.ArrayList<>(Arrays.asList(
                "http://localhost:80",        // Frontend default port
                "http://localhost",           // Frontend without port
                "http://127.0.0.1:80",
                "http://127.0.0.1",
                "http://frontend",            // Docker service name
                "http://minibank-frontend"    // Docker container name
        ));

        // Add production origins from environment variable
        if (extraAllowedOrigins != null && !extraAllowedOrigins.isBlank()) {
            for (String origin : extraAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    allowedOrigins.add(trimmed);
                }
            }
        }

        // Use allowedOrigins (not allowedOriginPatterns("*")) for security
        corsConfig.setAllowedOrigins(allowedOrigins);

        // Allowed methods
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allowed headers
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Idempotency-Key",
                "X-Request-ID"
        ));

        // Exposed headers
        corsConfig.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "X-Request-ID",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset"
        ));

        // Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);

        // Preflight cache duration (seconds)
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
