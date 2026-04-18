package com.minibank.notification.integration;

import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.entity.Notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Notification Service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("Notification API Integration Tests")
    class NotificationApiTests {

        @Test
        @DisplayName("POST /api/v1/notifications - should create notification")
        void createNotification_Success() {
            // Arrange
            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.randomUUID())
                    .type(NotificationType.EMAIL)
                    .subject("Integration Test")
                    .content("This is a test notification")
                    .build();

            // Act
            ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
                    "/api/v1/notifications",
                    request,
                    NotificationResponse.class
            );

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getId());
            assertEquals(request.getUserId(), response.getBody().getUserId());
            assertEquals(NotificationType.EMAIL, response.getBody().getType());
        }

        @Test
        @DisplayName("GET /api/v1/notifications/{id} - should return 404 for non-existent notification")
        void getNotification_NotFound() {
            // Act
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/notifications/" + UUID.randomUUID(),
                    String.class
            );

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("GET /api/v1/notifications/health - should return health status")
        void healthCheck() {
            // Act
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/notifications/health",
                    String.class
            );

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().contains("healthy"));
        }

        @Test
        @DisplayName("POST /api/v1/notifications - should validate required fields")
        void createNotification_ValidationError() {
            // Arrange - missing required fields
            NotificationRequest request = NotificationRequest.builder()
                    .build();

            // Act
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/notifications",
                    request,
                    String.class
            );

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("User Notifications API Tests")
    class UserNotificationsTests {

        @Test
        @DisplayName("GET /api/v1/notifications/user/{userId} - should return paginated notifications")
        void getUserNotifications_Success() {
            // Arrange
            UUID userId = UUID.randomUUID();

            // Act
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/notifications/user/" + userId + "?page=0&size=10",
                    String.class
            );

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Send Notification API Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("POST /api/v1/notifications/{id}/send - should return 404 for non-existent notification")
        void sendNotification_NotFound() {
            // Act
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/notifications/" + UUID.randomUUID() + "/send",
                    null,
                    String.class
            );

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
}
