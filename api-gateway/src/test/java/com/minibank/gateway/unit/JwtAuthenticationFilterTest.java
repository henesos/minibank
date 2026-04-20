package com.minibank.gateway.unit;

import com.minibank.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    // Secret must be at least 256 bits (32 characters)
    private static final String TEST_JWT_SECRET = "minibank-super-secret-key-for-development-only-min-256-bits";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        // Set the jwtSecret field using reflection
        try {
            var field = JwtAuthenticationFilter.class.getDeclaredField("jwtSecret");
            field.setAccessible(true);
            field.set(filter, TEST_JWT_SECRET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should allow public endpoints without authentication")
    void publicEndpoint_shouldPassWithoutAuth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/auth/login")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Should allow actuator endpoints without authentication")
    void actuatorEndpoint_shouldPassWithoutAuth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Should reject request without Authorization header")
    void missingAuthHeader_shouldReject() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users/profile")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        // Response should be 401
        ServerHttpResponse response = exchange.getResponse();
        assert response.getStatusCode() == HttpStatus.UNAUTHORIZED;

        // Chain should NOT be called
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should reject request with invalid Authorization header format")
    void invalidAuthHeaderFormat_shouldReject() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token123")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        ServerHttpResponse response = exchange.getResponse();
        assert response.getStatusCode() == HttpStatus.UNAUTHORIZED;

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should reject request with malformed JWT token")
    void malformedJwt_shouldReject() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        ServerHttpResponse response = exchange.getResponse();
        assert response.getStatusCode() == HttpStatus.UNAUTHORIZED;

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Should allow swagger endpoints without authentication")
    void swaggerEndpoint_shouldPassWithoutAuth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/swagger-ui/index.html")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Should allow fallback endpoints without authentication")
    void fallbackEndpoint_shouldPassWithoutAuth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/fallback/user")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Filter should have high priority order")
    void filterOrder_shouldBeHighPriority() {
        // The filter should have order -100 for high priority
        assert filter.getOrder() == -100;
    }
}
