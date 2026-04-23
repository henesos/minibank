package com.minibank.gateway.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@Disabled("Requires Docker for external services")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CircuitBreakerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Gateway should start successfully")
    void gateway_shouldStartSuccessfully() {
        webTestClient.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Account service route should be protected with JWT")
    void accountServiceRoute_shouldRequireJwtAuthentication() {
        webTestClient.get()
                .uri("/api/v1/accounts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Fallback endpoint should return 503 when called directly")
    void fallbackEndpoint_shouldReturn503() {
        webTestClient.get()
                .uri("/fallback/account")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.error")
                .isEqualTo("Service Unavailable");
    }
}