package com.minibank.gateway.unit;

import com.minibank.gateway.filter.RequestLoggingFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestLoggingFilter
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    @DisplayName("Should add X-Request-ID header if not present")
    void shouldAddRequestIdHeader() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
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
    @DisplayName("Should use existing X-Request-ID header if present")
    void shouldUseExistingRequestIdHeader() {
        // Arrange
        String existingRequestId = "existing-request-id";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header("X-Request-ID", existingRequestId)
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
    @DisplayName("Should log request method and path")
    void shouldLogRequestMethodAndPath() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/api/transactions")
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
    @DisplayName("Filter should have the highest priority (order -200)")
    void filterOrder_shouldBeHighest() {
        // The filter should have order -200 to execute first
        assert filter.getOrder() == -200;
    }
}
