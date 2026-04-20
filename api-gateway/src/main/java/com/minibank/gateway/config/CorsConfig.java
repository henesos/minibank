package com.minibank.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * CORS Configuration for API Gateway
 *
 * Allows frontend applications to communicate with the gateway
 * from different origins.
 */
@Configuration
public class CorsConfig {

    private static final long CORS_MAX_AGE = 3600L;

    /** CORS web filter bean configuration. */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allowed origins (configure for production)
        // Using allowedOriginPatterns for wildcard support in Docker environment
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://localhost",
                "http://127.0.0.1",
                "http://frontend",          // Docker service name
                "http://minibank-frontend", // Docker container name
                "http://localhost:80",
                "http://127.0.0.1:80"
        ));

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
        corsConfig.setMaxAge(CORS_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
