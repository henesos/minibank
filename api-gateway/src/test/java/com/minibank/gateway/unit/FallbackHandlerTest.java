package com.minibank.gateway.unit;

import com.minibank.gateway.handler.FallbackHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FallbackHandler
 */
class FallbackHandlerTest {

    private FallbackHandler fallbackHandler;

    @BeforeEach
    void setUp() {
        fallbackHandler = new FallbackHandler();
    }

    @Test
    @DisplayName("User service fallback should return 503")
    void userServiceFallback_shouldReturn503() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        when(request.path()).thenReturn("/fallback/user");

        // Act
        var response = fallbackHandler.userServiceFallback(request);

        // Assert
        StepVerifier.create(response)
                .assertNext(serverResponse -> {
                    assertNotNull(serverResponse);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Account service fallback should return 503")
    void accountServiceFallback_shouldReturn503() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        when(request.path()).thenReturn("/fallback/account");

        // Act
        var response = fallbackHandler.accountServiceFallback(request);

        // Assert
        StepVerifier.create(response)
                .assertNext(serverResponse -> {
                    assertNotNull(serverResponse);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Transaction service fallback should return 503")
    void transactionServiceFallback_shouldReturn503() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        when(request.path()).thenReturn("/fallback/transaction");

        // Act
        var response = fallbackHandler.transactionServiceFallback(request);

        // Assert
        StepVerifier.create(response)
                .assertNext(serverResponse -> {
                    assertNotNull(serverResponse);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Notification service fallback should return 503")
    void notificationServiceFallback_shouldReturn503() {
        // Arrange
        ServerRequest request = mock(ServerRequest.class);
        when(request.path()).thenReturn("/fallback/notification");

        // Act
        var response = fallbackHandler.notificationServiceFallback(request);

        // Assert
        StepVerifier.create(response)
                .assertNext(serverResponse -> {
                    assertNotNull(serverResponse);
                })
                .verifyComplete();
    }
}
