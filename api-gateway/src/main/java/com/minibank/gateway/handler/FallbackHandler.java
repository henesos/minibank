package com.minibank.gateway.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Fallback Handler for Circuit Breaker
 * 
 * Provides fallback responses when downstream services are unavailable.
 */
@Component
public class FallbackHandler {

    public Mono<ServerResponse> userServiceFallback(ServerRequest request) {
        return createFallbackResponse("User Service", request);
    }

    public Mono<ServerResponse> accountServiceFallback(ServerRequest request) {
        return createFallbackResponse("Account Service", request);
    }

    public Mono<ServerResponse> transactionServiceFallback(ServerRequest request) {
        return createFallbackResponse("Transaction Service", request);
    }

    public Mono<ServerResponse> notificationServiceFallback(ServerRequest request) {
        return createFallbackResponse("Notification Service", request);
    }

    private Mono<ServerResponse> createFallbackResponse(String serviceName, ServerRequest request) {
        String responseBody = """
                {
                    "status": 503,
                    "error": "Service Unavailable",
                    "message": "%s is currently unavailable. Please try again later.",
                    "path": "%s",
                    "timestamp": "%s"
                }
                """.formatted(
                        serviceName,
                        request.path(),
                        java.time.Instant.now().toString()
                );

        return ServerResponse
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseBody);
    }
}
