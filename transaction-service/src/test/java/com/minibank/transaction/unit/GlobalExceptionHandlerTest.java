package com.minibank.transaction.unit;

import com.minibank.transaction.exception.GlobalExceptionHandler;
import com.minibank.transaction.exception.TransactionServiceException;
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
 * Unit tests for Transaction Service GlobalExceptionHandler.
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
    // 1. handleTransactionServiceException
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleTransactionServiceException")
    class HandleTransactionServiceExceptionTests {

        @Test
        @DisplayName("Should return correct HTTP status from exception")
        void shouldReturnCorrectHttpStatus() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Transaction not found", HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("Should return errorCode from exception")
        void shouldReturnErrorCode() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Duplicate transaction", HttpStatus.CONFLICT, "DUPLICATE_TRANSACTION");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("DUPLICATE_TRANSACTION", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("Should return message from exception")
        void shouldReturnMessage() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Daily limit exceeded", HttpStatus.BAD_REQUEST, "DAILY_LIMIT_EXCEEDED");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Daily limit exceeded", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return status code and error phrase in body")
        void shouldReturnStatusAndErrorPhrase() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Insufficient funds", HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
            assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.getBody().getError());
        }

        @Test
        @DisplayName("Should return non-null timestamp")
        void shouldReturnTimestamp() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Test error", HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("Should handle CONFLICT status from TransactionServiceException")
        void shouldHandleConflictStatus() {
            // Arrange
            TransactionServiceException ex = new TransactionServiceException(
                    "Idempotency key conflict", HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleTransactionServiceException(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(409, response.getBody().getStatus());
            assertEquals("IDEMPOTENCY_CONFLICT", response.getBody().getErrorCode());
            assertEquals("Idempotency key conflict", response.getBody().getMessage());
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
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
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
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
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
        @DisplayName("Should populate details map with field names and messages")
        void shouldPopulateDetailsMap() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("transferRequest", "amount", "must be greater than zero"),
                    new FieldError("transferRequest", "recipientAccountNumber", "must not be blank")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> details = response.getBody().getDetails();
            assertNotNull(details);
            assertEquals(2, details.size());
            assertEquals("must be greater than zero", details.get("amount"));
            assertEquals("must not be blank", details.get("recipientAccountNumber"));
        }

        @Test
        @DisplayName("Should return 'Invalid request parameters' message")
        void shouldReturnValidationMessage() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("request", "amount", "must be positive")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("Invalid request parameters", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return 'Validation Failed' as error description")
        void shouldReturnValidationErrorDescription() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
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
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
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
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                    new FieldError("depositRequest", "amount", "must not be null")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> details = response.getBody().getDetails();
            assertNotNull(details);
            assertEquals(1, details.size());
            assertEquals("must not be null", details.get("amount"));
        }

        @Test
        @DisplayName("Should handle empty field errors list")
        void shouldHandleEmptyFieldErrors() {
            // Arrange
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of());
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                    handler.handleValidationException(ex);

            // Assert
            assertNotNull(response.getBody());
            Map<String, String> details = response.getBody().getDetails();
            assertNotNull(details);
            assertTrue(details.isEmpty());
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
    }
}
