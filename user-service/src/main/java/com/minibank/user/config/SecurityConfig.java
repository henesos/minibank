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

/**
 * Security Configuration for User Service.
 * 
 * Configures:
 * - Stateless session (JWT based)
 * - Public endpoints (register, login, health)
 * - Protected endpoints (everything else)
 * - BCrypt password encoder
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (using JWT, not cookies)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Set session management to stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure authorization - SINGLE authorizeHttpRequests call
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/v1/users/register",
                    "/api/v1/users/login",
                    "/api/v1/users/refresh",
                    "/api/v1/users/health",
                    "/actuator/**"
                ).permitAll()
                
                // All other endpoints - for development, permit all
                // TODO: Change to .authenticated() after implementing JWT filter
                .anyRequest().permitAll()
            );
        
        return http.build();
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
