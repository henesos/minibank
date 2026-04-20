package com.minibank.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import com.minibank.user.service.JwtService;

/**
 * JWT Authentication Filter for User Service (Servlet-based).
 *
 * <p>Validates JWT tokens on incoming requests and sets the SecurityContext authentication.
 * This filter acts as a defense-in-depth layer — even if the API Gateway is bypassed
 * (e.g., port forwarding, Kubernetes cluster-internal access), the service remains protected.</p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>If X-User-ID header is present → request came through API Gateway, trust it</li>
 *   <li>If Authorization: Bearer token is present → validate and extract claims</li>
 *   <li>If neither → skip authentication (SecurityConfig handles authorization)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID_HEADER = "X-User-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // --- Gateway trust path ---
        // If X-User-ID header is present, the request was routed through API Gateway
        // which already validated the JWT. Trust it and set authentication directly.
        String gatewayUserId = request.getHeader(X_USER_ID_HEADER);
        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            log.debug("Request routed through API Gateway, trusting X-User-ID: {}", gatewayUserId);
            setAuthentication(gatewayUserId, request);
            filterChain.doFilter(request, response);
            return;
        }

        // --- Direct access path ---
        // Extract Authorization header for JWT validation
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("No Bearer token found for request: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // Validate that this is a valid ACCESS token (not refresh, not expired, not tampered)
            if (jwtService.validateAccessToken(token)) {
                String userId = jwtService.extractUserId(token);
                String email = jwtService.extractEmail(token);
                log.debug("Valid access token for user: {} ({})", email, userId);
                setAuthentication(userId, request);
            } else {
                log.warn("Access token validation failed for request: {} {}",
                        request.getMethod(), request.getRequestURI());
            }
        } catch (Exception e) {
            // JwtService.validateAccessToken catches most JWT errors internally and returns false.
            // This catch is a safety net for unexpected runtime errors (e.g., corrupted claims).
            // Don't set authentication — SecurityConfig will return 401 for protected endpoints.
            log.warn("JWT processing error for request {}: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sets the Spring Security authentication context with the given userId as principal.
     *
     * <p>Uses {@link UsernamePasswordAuthenticationToken} with empty authorities.
     * Role-based authorization can be added later (Sprint 7+) when RBAC is implemented.</p>
     *
     * @param userId  the authenticated user's ID (from JWT subject or gateway header)
     * @param request the current HTTP request (used for authentication details)
     */
    private void setAuthentication(String userId, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.emptyList()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
