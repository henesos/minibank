package com.minibank.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for API Gateway
 * 
 * Tests the gateway routing and filter chain.
 * Uses mock services for testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Health endpoint should be accessible without authentication")
    void healthEndpoint_shouldBeAccessible() {
        webTestClient.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Protected endpoint should return 401 without token")
    void protectedEndpoint_shouldReturn401WithoutToken() {
        webTestClient.get()
                .uri("/api/users/profile")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected endpoint should return 401 with invalid token")
    void protectedEndpoint_shouldReturn401WithInvalidToken() {
        webTestClient.get()
                .uri("/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Login endpoint should be accessible without authentication")
    void loginEndpoint_shouldBeAccessible() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"test@minibank.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().is5xxServerError(); // Service not available, but auth passed
    }

    @Test
    @DisplayName("Register endpoint should be accessible without authentication")
    void registerEndpoint_shouldBeAccessible() {
        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Test\",\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().is5xxServerError(); // Service not available, but auth passed
    }

    @Test
    @DisplayName("Swagger UI should be accessible")
    void swaggerUi_shouldBeAccessible() {
        webTestClient.get()
                .uri("/swagger-user/swagger-ui/index.html")
                .exchange()
                .expectStatus().is5xxServerError(); // Service not available, but auth passed
    }
}
