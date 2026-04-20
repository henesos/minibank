package com.minibank.notification.unit;

import com.minibank.notification.exception.DuplicateNotificationException;
import com.minibank.notification.exception.GlobalExceptionHandler;
import com.minibank.notification.exception.NotificationNotFoundException;
import com.minibank.notification.exception.NotificationServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Notification Service GlobalExceptionHandler.
 *
 * <p>Tests all exception handler methods — NotificationNotFoundException, DuplicateNotificationException,
 * NotificationServiceException, validation errors, and generic exceptions — verifying correct HTTP status codes,
 * error codes, messages, and field-level error details. No Spring context is loaded — pure unit tests
 * with direct instantiation and Mockito mocks.</p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. handleNotificationNotFound
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleNotificationNotFound")
    class HandleNotificationNotFoundTests {

        @Test
        @DisplayName("Should return 404 NOT_FOUND status")
        void shouldReturn404Status() {
            // Arrange
            NotificationNotFoundException ex = new NotificationNotFoundException("Notification not found");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationNotFound(ex);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return NOTIFICATION_NOT_FOUND errorCode")
        void shouldReturnNotificationNotFoundErrorCode() {
            // Arrange
            NotificationNotFoundException ex = new NotificationNotFoundException("Notification not found");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationNotFound(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("NOTIFICATION_NOT_FOUND", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return message from exception")
        void shouldReturnMessage() {
            // Arrange
            NotificationNotFoundException ex = new NotificationNotFoundException(
                    "Notification not found with id: " + UUID.randomUUID());

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationNotFound(ex);

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getMessage().contains("Notification not found"));
        }

        @Test
        @DisplayName("Should return status code 404 and 'Not Found' description")
        void shouldReturn404AndNotFoundDescription() {
            // Arrange
            UUID notificationId = UUID.randomUUID();
            NotificationNotFoundException ex = new NotificationNotFoundException(notificationId);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationNotFound(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(404, response.getBody().getStatus());
            assertEquals("Not Found", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            NotificationNotFoundException ex = new NotificationNotFoundException("Not found");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationNotFound(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. handleDuplicateNotification
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleDuplicateNotification")
    class HandleDuplicateNotificationTests {

        @Test
        @DisplayName("Should return 409 CONFLICT status")
        void shouldReturn409Status() {
            // Arrange
            DuplicateNotificationException ex = new DuplicateNotificationException("Duplicate detected");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleDuplicateNotification(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return DUPLICATE_NOTIFICATION errorCode")
        void shouldReturnDuplicateNotificationErrorCode() {
            // Arrange
            DuplicateNotificationException ex = new DuplicateNotificationException("Duplicate detected");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleDuplicateNotification(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("DUPLICATE_NOTIFICATION", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return message from exception")
        void shouldReturnMessage() {
            // Arrange
            UUID existingId = UUID.randomUUID();
            DuplicateNotificationException ex = new DuplicateNotificationException("idempotency-key-123", existingId);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleDuplicateNotification(ex);

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getMessage().contains("Duplicate notification detected"));
            assertTrue(response.getBody().getMessage().contains("idempotency-key-123"));
            assertTrue(response.getBody().getMessage().contains(existingId.toString()));
        }

        @Test
        @DisplayName("Should return status code 409 and 'Conflict' description")
        void shouldReturn409AndConflictDescription() {
            // Arrange
            DuplicateNotificationException ex = new DuplicateNotificationException("Duplicate");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleDuplicateNotification(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(409, response.getBody().getStatus());
            assertEquals("Conflict", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            DuplicateNotificationException ex = new DuplicateNotificationException("Duplicate");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleDuplicateNotification(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. handleNotificationServiceException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleNotificationServiceException")
    class HandleNotificationServiceExceptionTests {

        @Test
        @DisplayName("Should return 500 INTERNAL_SERVER_ERROR status")
        void shouldReturn500Status() {
            // Arrange
            NotificationServiceException ex = new NotificationServiceException("Email service unavailable");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationServiceException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return NOTIFICATION_SERVICE_ERROR errorCode")
        void shouldReturnNotificationServiceErrorCode() {
            // Arrange
            NotificationServiceException ex = new NotificationServiceException("SMS gateway failure");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("NOTIFICATION_SERVICE_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return message from exception")
        void shouldReturnMessage() {
            // Arrange
            NotificationServiceException ex = new NotificationServiceException("Failed to send email");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Failed to send email", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return status code 500 and 'Internal Server Error' description")
        void shouldReturn500AndErrorDescription() {
            // Arrange
            NotificationServiceException ex = new NotificationServiceException("Service down");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().getStatus());
            assertEquals("Internal Server Error", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            NotificationServiceException ex = new NotificationServiceException("Error");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleNotificationServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. handleValidationErrors
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleValidationErrors")
    class HandleValidationErrorsTests {

        @Test
        @DisplayName("Should return 400 BAD_REQUEST status")
        void shouldReturn400Status() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "email", "must be a valid email address")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return VALIDATION_ERROR errorCode")
        void shouldReturnValidationErrorCode() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "email", "must be a valid email address")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should populate fieldErrors map with field names and messages")
        void shouldPopulateFieldErrorsMap() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("notificationRequest", "userId", "must not be null"),
                    new FieldError("notificationRequest", "type", "must not be blank")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> fieldErrors = response.getBody().getFieldErrors();
            assertNotNull(fieldErrors);
            assertEquals(2, fieldErrors.size());
            assertEquals("must not be null", fieldErrors.get("userId"));
            assertEquals("must not be blank", fieldErrors.get("type"));
        }

        @Test
        @DisplayName("Should return 'Validation failed' message")
        void shouldReturnValidationMessage() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "email", "invalid format")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Validation failed", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return 'Bad Request' as error description")
        void shouldReturnBadRequestDescription() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "subject", "must not be blank")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Bad Request", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return status code 400 in body")
        void shouldReturn400StatusCodeInBody() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "content", "must not be empty")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
        }

        @Test
        @DisplayName("Should handle single field error")
        void shouldHandleSingleFieldError() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("notificationRequest", "idempotencyKey", "must be unique")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationErrors(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> fieldErrors = response.getBody().getFieldErrors();
            assertNotNull(fieldErrors);
            assertEquals(1, fieldErrors.size());
            assertEquals("must be unique", fieldErrors.get("idempotencyKey"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. handleGenericException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleGenericException")
    class HandleGenericExceptionTests {

        @Test
        @DisplayName("Should return 500 INTERNAL_SERVER_ERROR status")
        void shouldReturn500Status() {
            // Arrange
            Exception ex = new RuntimeException("Unexpected failure");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return INTERNAL_ERROR errorCode")
        void shouldReturnInternalErrorCode() {
            // Arrange
            Exception ex = new RuntimeException("Something went wrong");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return generic error message")
        void shouldReturnGenericMessage() {
            // Arrange
            Exception ex = new RuntimeException("Database connection lost");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("An unexpected error occurred", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return status code 500 and 'Internal Server Error' description")
        void shouldReturn500AndErrorDescription() {
            // Arrange
            Exception ex = new NullPointerException("Null reference");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().getStatus());
            assertEquals("Internal Server Error", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            Exception ex = new RuntimeException("Unexpected");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("Should not expose original exception message to client")
        void shouldNotExposeOriginalMessage() {
            // Arrange
            Exception ex = new RuntimeException("SENSITIVE EMAIL CREDENTIAL LEAKED");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotEquals("SENSITIVE EMAIL CREDENTIAL LEAKED", response.getBody().getMessage());
            assertEquals("An unexpected error occurred", response.getBody().getMessage());
        }
    }
}
