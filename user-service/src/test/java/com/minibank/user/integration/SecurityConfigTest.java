package com.minibank.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "JWT_SECRET=test-jwt-secret-key-for-integration-tests-minimum-256-bits",
    "INTERNAL_AUTH_SECRET=test-internal-auth-secret-for-tests",
    "spring.datasource.url=jdbc:tc:postgresql:15:///user_test_db",
    "internal.auth.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityConfigTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("user_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static UUID userId;
    private static String accessToken;

    @Test
    @Order(1)
    @DisplayName("Setup: Register test user and activate manually")
    void setup_User() throws Exception {
        String request = """
            {
                "email": "security@minibank.com",
                "password": "Password123!",
                "firstName": "Security",
                "lastName": "Test"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    @Order(2)
    @DisplayName("Health endpoint should be accessible without authentication")
    void health_NoAuth_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/health"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @DisplayName("Should reject login for PENDING user (not active)")
    void login_PendingUser_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "security@minibank.com", "password": "Password123!"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("Should reject unauthenticated request to protected endpoint with 401")
    void protectedEndpoint_NoAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Should allow authenticated request with valid X-User-ID header")
    void protectedEndpoint_WithAuth_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId)
                .header("X-User-ID", userId.toString())
                .header("X-User-Email", "security@minibank.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("security@minibank.com"));
    }

    @Test
    @Order(5)
    @DisplayName("Should reject request with mismatched X-User-ID header")
    void protectedEndpoint_MismatchedAuth_Unauthorized() throws Exception {
        UUID wrongId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                .header("X-User-ID", wrongId.toString()))
                .andExpect(status().isForbidden());
    }
}