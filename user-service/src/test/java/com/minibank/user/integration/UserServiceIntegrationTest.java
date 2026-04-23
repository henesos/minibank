package com.minibank.user.integration;

import com.minibank.user.dto.*;
import com.minibank.user.entity.User;
import com.minibank.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
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
 * Uses Testcontainers to spin up a PostgreSQL database for testing.
 * Tests the full application stack including HTTP layer.
 */
@Disabled("Requires Docker for external services")
@SpringBootTest(properties = {
    "JWT_SECRET=test-jwt-secret-key-for-integration-tests-minimum-256-bits",
    "INTERNAL_AUTH_SECRET=test-internal-auth-secret-for-tests",
    "spring.datasource.url=jdbc:tc:postgresql:15:///user_test_db",
    "internal.auth.enabled=false"
})
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

    @Test
    @Order(1)
    @DisplayName("Should register a new user")
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
    @DisplayName("Should activate user manually for login test")
    void activateUser_Success() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Test
    @Order(3)
    @DisplayName("Should login with verified account")
    void login_Success() throws Exception {
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

    @Test
    @Order(4)
    @DisplayName("Should get user by ID")
    void getUserById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", userId)
                .header("X-User-ID", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("integration@minibank.com"));
    }

    @Test
    @Order(5)
    @DisplayName("Should update user profile")
    void updateProfile_Success() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .build();

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                .header("X-User-ID", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }

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
    @DisplayName("Should return 404 for non-existent user")
    void getUser_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        
        mockMvc.perform(get("/api/v1/users/{id}", nonExistentId)
                .header("X-User-ID", nonExistentId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("Should delete user account")
    void deleteAccount_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", userId)
                .header("X-User-ID", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(10)
    @DisplayName("Health check should return UP")
    void healthCheck_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("user-service"));
    }
}
