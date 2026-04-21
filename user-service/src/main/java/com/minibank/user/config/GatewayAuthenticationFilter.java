package com.minibank.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Gateway Authentication Filter.
 * 
 * Reads trusted headers set by API Gateway after JWT validation:
 * - X-User-ID:    Authenticated user's unique identifier
 * - X-User-Email: Authenticated user's email
 * - X-User-Role:  Authenticated user's role
 * 
 * Security Model:
 * - API Gateway validates JWT and sets these headers
 * - User Service trusts these headers (internal network only)
 * - For protected endpoints: X-User-ID header is REQUIRED (401 if missing)
 * - For public endpoints: X-User-ID header is optional (no auth needed)
 * - Sets PreAuthenticatedAuthenticationToken in SecurityContext
 * 
 * NOTE: JWT validation is NOT performed here — that's API Gateway's responsibility.
 */
@Slf4j
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-ID";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * Public endpoints that do not require X-User-ID header.
     * Must match the permitAll() paths in SecurityConfig.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/users/register",
        "/api/v1/users/login",
        "/api/v1/users/refresh",
        "/api/v1/users/health"
    );

    /**
     * Public path prefixes that do not require authentication.
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/actuator",
        "/swagger-ui",
        "/v3/api-docs"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String userId = request.getHeader(HEADER_USER_ID);

        // For public endpoints, authentication header is optional
        if (isPublicPath(requestURI)) {
            // If header is present, still set SecurityContext (for optional auth info)
            if (userId != null && !userId.isBlank()) {
                setAuthentication(request, userId);
            }
            // Continue regardless — public endpoints don't require auth
            filterChain.doFilter(request, response);
            return;
        }

        // For protected endpoints: X-User-ID header is REQUIRED
        if (userId == null || userId.isBlank()) {
            // Security: Reject with 401 to prevent unauthorized access
            log.warn("Missing X-User-ID header for protected request: {} {}", request.getMethod(), requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\","
                + "\"status\":401,"
                + "\"error\":\"Unauthorized\","
                + "\"errorCode\":\"MISSING_AUTH_HEADER\","
                + "\"message\":\"Missing authentication header from gateway\"}"
            );
            return;
        }

        // Set authentication for protected endpoint
        setAuthentication(request, userId);
        filterChain.doFilter(request, response);
    }

    /**
     * Sets the SecurityContext with pre-authenticated token from gateway headers.
     */
    private void setAuthentication(HttpServletRequest request, String userId) {
        String email = request.getHeader(HEADER_USER_EMAIL);
        String role = request.getHeader(HEADER_USER_ROLE);

        log.debug("Gateway auth - userId: {}, email: {}, role: {}", userId, email, role);

        // Build granted authorities from role header
        List<SimpleGrantedAuthority> authorities = List.of();
        if (role != null && !role.isBlank()) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        }

        // Create pre-authenticated token — API Gateway already validated JWT
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(userId, null, authorities);
        authentication.setDetails(email);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Checks if the request path is a public endpoint that doesn't require authentication.
     */
    private boolean isPublicPath(String requestURI) {
        // Exact match for public paths
        if (PUBLIC_PATHS.contains(requestURI)) {
            return true;
        }
        // Prefix match for swagger, actuator, etc.
        for (String prefix : PUBLIC_PREFIXES) {
            if (requestURI.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
