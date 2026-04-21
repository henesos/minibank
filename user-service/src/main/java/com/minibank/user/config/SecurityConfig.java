package com.minibank.user.config;

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
 * Configures:
 * - Stateless session (JWT based, validated by API Gateway)
 * - CORS: Only allows requests from API Gateway
 * - Gateway Authentication Filter: Reads X-User-ID header set by API Gateway
 * - Public endpoints (register, login, refresh, health)
 * - Protected endpoints (everything else requires authentication)
 * - BCrypt password encoder
 * 
 * Security Model:
 * - API Gateway validates JWT tokens and sets X-User-ID, X-User-Email, X-User-Role headers
 * - User Service trusts these headers (internal network communication)
 * - GatewayAuthenticationFilter reads these headers and populates SecurityContext
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (using JWT, not cookies)
            .csrf(AbstractHttpConfigurer::disable)

            // Enable CORS with custom configuration (only allow API Gateway)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Set session management to stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Add Gateway Authentication Filter before Spring Security's default filter
            // Security: Reads X-User-ID header from API Gateway and sets SecurityContext
            .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Configure authorization - SINGLE authorizeHttpRequests call
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no authentication required
                .requestMatchers(
                    "/api/v1/users/register",
                    "/api/v1/users/login",
                    "/api/v1/users/refresh",
                    "/api/v1/users/health",
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()
                
                // Security Fix: Changed from permitAll() to authenticated()
                // All other endpoints require valid authentication via API Gateway
                .anyRequest().authenticated()
            );
        
        return http.build();
    }

    /**
     * CORS Configuration — only allow API Gateway origin.
     * Security: Prevents cross-origin requests from untrusted sources.
     * Adjust allowed origins based on your deployment environment.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Security: Only allow API Gateway origin
        configuration.setAllowedOrigins(List.of(
            "http://localhost:8080",    // API Gateway default
            "http://api-gateway:8080"   // Docker/K8s internal
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (good balance of security and performance)
        return new BCPasswordEncoder(12);
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
