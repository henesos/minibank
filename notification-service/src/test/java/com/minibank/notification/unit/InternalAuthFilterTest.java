package com.minibank.notification.unit;

import com.minibank.notification.config.InternalAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalAuthFilterTest {

    private InternalAuthFilter filter;
    private static final String TEST_SECRET = "test-internal-secret-key-123456789012";
    private static final long TEST_TTL = 300000L;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", TEST_SECRET);
        ReflectionTestUtils.setField(filter, "tokenTtl", TEST_TTL);
    }

    private String generateValidToken(String requestPath) {
        long timestamp = System.currentTimeMillis();
        String data = timestamp + ":" + requestPath;
        String hmac = calculateHmac(data, TEST_SECRET);
        String rawToken = timestamp + ":" + hmac;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.getBytes());
    }

    private String calculateHmac(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC calculation failed", e);
        }
    }

    @Test
    void doFilter_missingToken_shouldReturn401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/accounts");
        when(request.getHeader("X-Internal-Token")).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_validToken_shouldPass() throws Exception {
        String validToken = generateValidToken("/api/v1/accounts");
        when(request.getRequestURI()).thenReturn("/api/v1/accounts");
        when(request.getHeader("X-Internal-Token")).thenReturn(validToken);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_expiredToken_shouldReturn401() throws Exception {
        long oldTimestamp = System.currentTimeMillis() - (TEST_TTL + 1000);
        String data = oldTimestamp + ":/api/v1/accounts";
        String hmac = calculateHmac(data, TEST_SECRET);
        String rawToken = oldTimestamp + ":" + hmac;
        String expiredToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.getBytes());

        when(request.getRequestURI()).thenReturn("/api/v1/accounts");
        when(request.getHeader("X-Internal-Token")).thenReturn(expiredToken);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void doFilter_healthEndpoint_shouldSkipValidation() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getHeader("X-Internal-Token")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_swaggerEndpoint_shouldSkipValidation() throws Exception {
        when(request.getRequestURI()).thenReturn("/swagger-ui.html");
        when(request.getHeader("X-Internal-Token")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_wrongPathInToken_shouldFail() throws Exception {
        String validToken = generateValidToken("/api/v1/accounts");
        when(request.getRequestURI()).thenReturn("/api/v1/other");
        when(request.getHeader("X-Internal-Token")).thenReturn(validToken);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}