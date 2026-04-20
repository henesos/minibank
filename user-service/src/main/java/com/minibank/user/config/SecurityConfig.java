package com.minibank.user.config;

import com.minibank.user.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security Configuration for User Service.
 *
 * <p>Configures:</p>
 * <ul>
 *   <li>Stateless session management (JWT-based, no server-side sessions)</li>
 *   <li>Public endpoints (register, login, refresh, health, actuator, swagger)</li>
 *   <li>Protected endpoints (everything else requires valid JWT or gateway X-User-ID header)</li>
 *   <li>JWT authentication filter (defense-in-depth: validates tokens even without gateway)</li>
 *   <li>CORS support (for development and direct service access scenarios)</li>
 *   <li>Custom 401 JSON error response (REST API compatible)</li>
 *   <li>BCrypt-12 password encoder</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (stateless JWT, no cookies)
            .csrf(AbstractHttpConfigurer::disable)

            // Enable CORS (handles preflight OPTIONS requests correctly)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless session — no HTTP sessions, JWT only
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules — SINGLE authorizeHttpRequests call
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no authentication required
                .requestMatchers(
                    "/api/v1/users/register",
                    "/api/v1/users/login",
                    "/api/v1/users/refresh",
                    "/api/v1/users/health",
                    "/api/v1/users/*/verify-email",
                    "/api/v1/users/*/verify-phone",
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()

                // All other endpoints require authentication (JWT or gateway header)
                .anyRequest().authenticated()
            )

            // Custom 401 JSON response for unauthenticated requests
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(buildUnauthorizedResponse(request.getRequestURI()));
                })
            )

            // Register JWT filter before the default username/password authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration source.
     *
     * <p>Allows all origins, methods, and headers for development.
     * In production, this should be restricted to the gateway origin only.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-User-ID", "X-User-Email"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCPasswordEncoder(12);
    }

    /**
     * Builds a JSON error response for 401 Unauthorized.
     */
    private String buildUnauthorizedResponse(String path) {
        return "{\"status\":401,\"error\":\"Unauthorized\","
                + "\"message\":\"Full authentication is required\","
                + "\"path\":\"" + escapeJson(path) + "\"}";
    }

    /**
     * Minimal JSON string escaping for request paths.
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Custom BCrypt encoder with configurable strength.
     */
    private static class BCPasswordEncoder extends BCryptPasswordEncoder {
        public BCPasswordEncoder(int strength) {
            super(strength);
        }
    }
}
