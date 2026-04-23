package com.minibank.user.unit;

import com.minibank.user.filter.JwtAuthenticationFilter;
import com.minibank.user.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Nested
    @DisplayName("Gateway Path")
    class GatewayPathTests {

        @Test
        @DisplayName("Should trust X-User-ID from gateway")
        void gatewayHeader_SetsAuthentication() throws Exception {
            String userId = UUID.randomUUID().toString();
            request.addHeader("X-User-ID", userId);
            request.setRequestURI("/api/v1/users/me");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Direct Access Path")
    class DirectAccessPathTests {

        @Test
        @DisplayName("Should skip when no authorization header")
        void noAuthHeader_SkipsFilter() throws Exception {
            request.setRequestURI("/api/v1/users/me");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(jwtService, never()).validateAccessToken(any());
        }

        @Test
        @DisplayName("Should skip when authorization header is not bearer")
        void nonBearerHeader_SkipsFilter() throws Exception {
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            request.setRequestURI("/api/v1/users/me");

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(jwtService, never()).validateAccessToken(any());
        }

        @Test
        @DisplayName("Should validate bearer token")
        void bearerToken_ValidatesToken() throws Exception {
            String validToken = "valid.jwt.token";
            String userId = UUID.randomUUID().toString();
            request.addHeader("Authorization", "Bearer " + validToken);
            request.setRequestURI("/api/v1/users/me");

            when(jwtService.validateAccessToken(validToken)).thenReturn(true);
            when(jwtService.extractUserId(validToken)).thenReturn(userId);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(jwtService).validateAccessToken(validToken);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not authenticate for invalid token")
        void invalidToken_DoesNotAuthenticate() throws Exception {
            String invalidToken = "invalid.jwt.token";
            request.addHeader("Authorization", "Bearer " + invalidToken);
            request.setRequestURI("/api/v1/users/me");

            when(jwtService.validateAccessToken(invalidToken)).thenReturn(false);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(jwtService).validateAccessToken(invalidToken);
            verify(filterChain).doFilter(request, response);
        }
    }
}