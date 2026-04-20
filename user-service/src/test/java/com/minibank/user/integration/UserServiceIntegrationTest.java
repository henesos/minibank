package com.minibank.user.integration;

import com.minibank.user.dto.*;
import com.minibank.user.entity.User;
import com.minibank.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for User Service.
 *
 * <p>Uses Testcontainers to spin up a PostgreSQL database for testing.
 * Tests the full application stack including HTTP layer, JWT filter, and security config.</p>
 *
 * <p>After S1 security fix, protected endpoints require a valid JWT in the Authorization header.
 * Public endpoints (register, login, refresh, verify-email, verify-phone, health) remain accessible
 * without authentication.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String accessToken;
    private static UUID userId;

    // ═══════════════════════════════════════════════════════════════════════
    // Public Endpoint Tests (no auth required)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Should register a new user (public endpoint)")
    void registerUser_Success() throws Exception {
        UserRegistrationRequest request = UserRegistrationRequest.builder()
                .email("integration@minibank.com")
                .password("Password123!")
                .firstName("Integration")
                .lastName("Test")
                .build();

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration@minibank.com"))
                .andExpect(jsonPath("$.firstName").value("Integration"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(result -> {
                    // Extract user ID from response
                    String response = result.getResponse().getContentAsString();
                    userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
                });
    }

    @Test
    @Order(2)
    @DisplayName("Should verify email (public endpoint)")
    void verifyEmail_Success() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/verify-email", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(3)
    @DisplayName("Should login with verified account (public endpoint)")
    void login_Success() throws Exception {
        // Ensure user is ACTIVE before login
        User user = userRepository.findById(userId).orElseThrow();
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);

        UserLoginRequest request = UserLoginRequest.builder()
                .email("integration@minibank.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("integration@minibank.com"))
                .andDo(result -> {
                    String response = result.getResponse().getContentAsString();
                    accessToken = objectMapper.readTree(response).get("accessToken").asText();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Protected Endpoint Tests (JWT required)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Should get user by ID with valid JWT")
    void getUserById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("integration@minibank.com"));
    }

    @Test
    @Order(5)
    @DisplayName("Should update user profile with valid JWT")
    void updateProfile_Success() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .build();

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Security Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Should fail to register with existing email")
    void register_DuplicateEmail_Fails() throws Exception {
        UserRegistrationRequest request = UserRegistrationRequest.builder()
                .email("integration@minibank.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EMAIL_EXISTS"));
    }

    @Test
    @Order(7)
    @DisplayName("Should fail login with wrong password")
    void login_WrongPassword_Fails() throws Exception {
        UserLoginRequest request = UserLoginRequest.builder()
                .email("integration@minibank.com")
                .password("WrongPassword!")
                .build();

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    @DisplayName("Should return 401 when accessing protected endpoint without token")
    void getUserById_NoToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("Should return 404 for non-existent user (with valid JWT)")
    void getUser_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}", nonExistentId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("Should delete user account with valid JWT")
    void deleteAccount_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", userId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Health Check Test
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("Health check should return UP (public endpoint)")
    void healthCheck_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("user-service"));
    }
}
