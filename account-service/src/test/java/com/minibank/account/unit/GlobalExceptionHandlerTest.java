package com.minibank.account.unit;

import com.minibank.account.exception.AccountServiceException;
import com.minibank.account.exception.GlobalExceptionHandler;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Account Service GlobalExceptionHandler.
 *
 * <p>Tests all exception handler methods and verifies that correct HTTP status codes,
 * error codes, messages, and field-level error details are returned in the response body.
 * No Spring context is loaded — pure unit tests with direct instantiation and Mockito mocks.</p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. handleAccountServiceException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleAccountServiceException")
    class HandleAccountServiceExceptionTests {

        @Test
        @DisplayName("Should return correct HTTP status from exception")
        void shouldReturnCorrectHttpStatus() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Account not found", HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("Should return errorCode from exception")
        void shouldReturnErrorCode() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Insufficient balance", HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("INSUFFICIENT_BALANCE", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return message from exception")
        void shouldReturnMessage() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Account is inactive", HttpStatus.FORBIDDEN, "INACTIVE_ACCOUNT");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Account is inactive", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return status code and error phrase in body")
        void shouldReturnStatusAndErrorPhrase() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Access denied", HttpStatus.FORBIDDEN, "ACCESS_DENIED");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
            assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Test error", HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("Should handle UNAUTHORIZED status from AccountServiceException")
        void shouldHandleUnauthorizedStatus() {
            // Arrange
            AccountServiceException ex = new AccountServiceException(
                    "Missing X-User-ID header", HttpStatus.UNAUTHORIZED, "MISSING_USER_ID");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleAccountServiceException(ex);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(401, response.getBody().getStatus());
            assertEquals("MISSING_USER_ID", response.getBody().getErrorCode());
            assertEquals("Missing X-User-ID header", response.getBody().getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. handleValidationException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleValidationException")
    class HandleValidationExceptionTests {

        @Test
        @DisplayName("Should return BAD_REQUEST status")
        void shouldReturnBadRequest() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return VALIDATION_ERROR errorCode")
        void shouldReturnValidationErrorCode() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should populate errors map with field names and messages")
        void shouldPopulateErrorsMap() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive"),
                    new FieldError("request", "currency", "must not be blank")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> errors = response.getBody().getErrors();
            assertNotNull(errors);
            assertEquals(2, errors.size());
            assertEquals("must be positive", errors.get("amount"));
            assertEquals("must not be blank", errors.get("currency"));
        }

        @Test
        @DisplayName("Should return 'Input validation failed' message")
        void shouldReturnValidationMessage() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Input validation failed", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return 'Validation Failed' as error description")
        void shouldReturnValidationErrorDescription() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Validation Failed", response.getBody().getError());
        }

        @Test
        @DisplayName("Should return status code 400 in body")
        void shouldReturn400StatusCodeInBody() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

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
                    new FieldError("transferRequest", "recipientAccountNumber", "must not be blank")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> errors = response.getBody().getErrors();
            assertNotNull(errors);
            assertEquals(1, errors.size());
            assertEquals("must not be blank", errors.get("recipientAccountNumber"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. handleGenericException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleGenericException")
    class HandleGenericExceptionTests {

        @Test
        @DisplayName("Should return INTERNAL_SERVER_ERROR status")
        void shouldReturnInternalServerError() {
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
            Exception ex = new RuntimeException("SENSITIVE DATABASE CREDENTIAL LEAKED");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotEquals("SENSITIVE DATABASE CREDENTIAL LEAKED", response.getBody().getMessage());
            assertEquals("An unexpected error occurred", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should log error with full stack trace")
        void shouldLogErrorWithStackTrace() {
            // Arrange
            Exception ex = new IllegalStateException("Database connection lost");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return timestamp in response body")
        void shouldReturnTimestampInBody() {
            // Arrange
            Exception ex = new RuntimeException("Error");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleGenericException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }
    }

    @Nested
    @DisplayName("ErrorResponse Inner Class")
    class ErrorResponseTests {

        @Test
        @DisplayName("Should build ErrorResponse with all fields")
        void shouldBuildWithAllFields() {
            // Arrange
            com.minibank.account.exception.GlobalExceptionHandler.ErrorResponse response =
                    com.minibank.account.exception.GlobalExceptionHandler.ErrorResponse.builder()
                    .timestamp(java.time.LocalDateTime.now())
                    .status(404)
                    .error("Not Found")
                    .errorCode("NOT_FOUND")
                    .message("Resource not found")
                    .build();

            // Assert
            assertNotNull(response);
            assertEquals(404, response.getStatus());
            assertEquals("Not Found", response.getError());
            assertEquals("NOT_FOUND", response.getErrorCode());
            assertEquals("Resource not found", response.getMessage());
        }

        @Test
        @DisplayName("Should handle null errors map")
        void shouldHandleNullErrorsMap() {
            // Arrange
            com.minibank.account.exception.GlobalExceptionHandler.ErrorResponse response =
                    com.minibank.account.exception.GlobalExceptionHandler.ErrorResponse.builder()
                    .status(400)
                    .errorCode("VALIDATION_ERROR")
                    .message("Validation failed")
                    .build();

            // Assert
            assertNotNull(response);
            assertNull(response.getErrors());
        }
    }
}
