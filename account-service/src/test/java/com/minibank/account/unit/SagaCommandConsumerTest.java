package com.minibank.account.unit;

import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.exception.AccountNotFoundException;
import com.minibank.account.exception.InsufficientBalanceException;
import com.minibank.account.kafka.SagaCommandConsumer;
import com.minibank.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for SagaCommandConsumer.
 *
 * Tests saga command routing, event parsing, and handler behavior
 * with mocked AccountService and KafkaTemplate.
 */
@ExtendWith(MockitoExtension.class)
class SagaCommandConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Map<String, Object>>> sendFuture;

    @InjectMocks
    private SagaCommandConsumer sagaCommandConsumer;

    private UUID sagaId;
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        sagaId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        amount = new BigDecimal("100.00");

        when(kafkaTemplate.send(anyString(), anyString(), any(Map.class)))
                .thenReturn(sendFuture);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse UUID Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse UUID")
    class ParseUUIDTests {

        @Test
        @DisplayName("Should parse valid UUID string")
        void parseUUID_ValidString() {
            String uuidString = "123e4567-e89b-12d3-a456-426614174000";
            UUID result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseUUID", (Object) uuidString);

            assertEquals(UUID.fromString(uuidString), result);
        }

        @Test
        @DisplayName("Should pass through UUID object unchanged")
        void parseUUID_UUIDObject() {
            UUID uuid = UUID.randomUUID();
            UUID result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseUUID", (Object) uuid);

            assertEquals(uuid, result);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null value")
        void parseUUID_Null() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseUUID", (Object) null)
            );

            assertTrue(exception.getMessage().contains("UUID value is null"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid UUID string")
        void parseUUID_InvalidString() {
            String invalid = "not-a-valid-uuid";

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseUUID", (Object) invalid)
            );

            assertTrue(exception.getMessage().contains("Invalid UUID value"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty string")
        void parseUUID_EmptyString() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseUUID", (Object) "")
            );

            assertTrue(exception.getMessage().contains("Invalid UUID value"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse Amount Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse Amount")
    class ParseAmountTests {

        @Test
        @DisplayName("Should return BigDecimal as-is")
        void parseAmount_BigDecimal() {
            BigDecimal bd = new BigDecimal("250.75");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd);

            assertEquals(bd, result);
            assertSame(bd, result);
        }

        @Test
        @DisplayName("Should convert Integer to BigDecimal")
        void parseAmount_Integer() {
            Integer intValue = 500;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) intValue);

            assertEquals(new BigDecimal("500.0"), result);
        }

        @Test
        @DisplayName("Should convert Double to BigDecimal")
        void parseAmount_Double() {
            Double doubleValue = 99.99;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) doubleValue);

            assertEquals(BigDecimal.valueOf(99.99), result);
        }

        @Test
        @DisplayName("Should parse numeric string to BigDecimal")
        void parseAmount_NumericString() {
            String numericString = "1234.56";
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) numericString);

            assertEquals(new BigDecimal("1234.56"), result);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null value")
        void parseAmount_Null() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) null)
            );

            assertTrue(exception.getMessage().contains("Amount value is null"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for non-numeric string")
        void parseAmount_NonNumericString() {
            String nonNumeric = "abc";

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) nonNumeric)
            );

            assertTrue(exception.getMessage().contains("Invalid amount value"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty string")
        void parseAmount_EmptyString() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) "")
            );

            assertTrue(exception.getMessage().contains("Invalid amount value"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Debit Request Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Debit Request")
    class DebitRequestTests {

        private Map<String, Object> buildDebitEvent() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);
            return event;
        }

        @Test
        @DisplayName("Should withdraw and publish DEBIT_SUCCESS on success")
        void debitRequest_Success() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doNothing().when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.withdraw was called
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(amount, requestCaptor.getValue().getAmount());

            // Assert - verify DEBIT_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertEquals(fromAccountId.toString(), published.get("fromAccountId"));
            assertNull(published.get("toAccountId"));
            assertEquals(amount, published.get("amount"));
            assertNull(published.get("errorMessage"));
            assertEquals("TRY", published.get("currency"));
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE when InsufficientBalanceException thrown")
        void debitRequest_InsufficientBalance() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new InsufficientBalanceException(fromAccountId, amount, new BigDecimal("50.00")))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify DEBIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Insufficient balance"));
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE when AccountNotFoundException thrown")
        void debitRequest_AccountNotFound() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new AccountNotFoundException(fromAccountId))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify DEBIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Account not found"));
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE for unexpected exception")
        void debitRequest_UnexpectedException() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new RuntimeException("Database connection failed"))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify DEBIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Unexpected error"));
            assertTrue(errorMsg.contains("Database connection failed"));
        }

        @Test
        @DisplayName("Should pass correct fromAccountId to withdraw")
        void debitRequest_CorrectAccountId() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doNothing().when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Credit Request Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Credit Request")
    class CreditRequestTests {

        private Map<String, Object> buildCreditEvent() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", toAccountId.toString());
            event.put("amount", amount);
            return event;
        }

        @Test
        @DisplayName("Should deposit and publish CREDIT_SUCCESS on success")
        void creditRequest_Success() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doNothing().when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.deposit was called
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).deposit(eq(toAccountId), requestCaptor.capture());
            assertEquals(amount, requestCaptor.getValue().getAmount());

            // Assert - verify CREDIT_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertNull(published.get("fromAccountId"));
            assertEquals(toAccountId.toString(), published.get("toAccountId"));
            assertEquals(amount, published.get("amount"));
            assertNull(published.get("errorMessage"));
            assertEquals("TRY", published.get("currency"));
        }

        @Test
        @DisplayName("Should publish CREDIT_FAILURE when AccountNotFoundException thrown")
        void creditRequest_AccountNotFound() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doThrow(new AccountNotFoundException(toAccountId))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify CREDIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Account not found"));
        }

        @Test
        @DisplayName("Should publish CREDIT_FAILURE for unexpected exception")
        void creditRequest_UnexpectedException() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doThrow(new RuntimeException("Unexpected failure"))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify CREDIT_FAILURE published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Unexpected error"));
            assertTrue(errorMsg.contains("Unexpected failure"));
        }

        @Test
        @DisplayName("Should pass correct toAccountId to deposit")
        void creditRequest_CorrectAccountId() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));
        }

        @Test
        @DisplayName("Should not call withdraw for credit request")
        void creditRequest_DoesNotCallWithdraw() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(accountService, never()).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensate Debit Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Compensate Debit")
    class CompensateDebitTests {

        private Map<String, Object> buildCompensateEvent() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "COMPENSATE_DEBIT");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);
            return event;
        }

        @Test
        @DisplayName("Should deposit and publish COMPENSATE_SUCCESS on success")
        void compensateDebit_Success() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doNothing().when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.deposit was called with fromAccountId (refund)
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).deposit(eq(fromAccountId), requestCaptor.capture());
            assertEquals(amount, requestCaptor.getValue().getAmount());

            // Assert - verify COMPENSATE_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertEquals(fromAccountId.toString(), published.get("fromAccountId"));
            assertNull(published.get("toAccountId"));
            assertEquals(amount, published.get("amount"));
            assertNull(published.get("errorMessage"));
            assertEquals("TRY", published.get("currency"));
        }

        @Test
        @DisplayName("Should publish COMPENSATE_FAILURE when exception thrown")
        void compensateDebit_Failure() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new AccountNotFoundException(fromAccountId))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify COMPENSATE_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Compensation failed"));
        }

        @Test
        @DisplayName("Should publish COMPENSATE_FAILURE for RuntimeException")
        void compensateDebit_RuntimeException() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new RuntimeException("Service unavailable"))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify COMPENSATE_FAILURE published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_FAILURE", published.get("eventType"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Service unavailable"));
        }

        @Test
        @DisplayName("Should not call withdraw for compensate debit")
        void compensateDebit_DoesNotCallWithdraw() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(accountService, never()).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Unknown Event Type Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unknown Event Type")
    class UnknownEventTypeTests {

        @Test
        @DisplayName("Should not call accountService for unknown event type")
        void unknownEventType_NoAccountServiceCalls() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "UNKNOWN_EVENT");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should not publish any event for unknown event type")
        void unknownEventType_NoKafkaSend() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "UNKNOWN_EVENT");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("Should handle null event type without crashing")
        void unknownEventType_NullEventType() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", null);
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            verifyNoInteractions(accountService);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(Map.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should catch null sagaId and not crash")
        void nullSagaId_NoCrash() {
            // Arrange - sagaId is null, which will cause parseUUID to throw
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", null);
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            // Act & Assert - the outer try/catch should prevent any crash
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            // Assert - accountService should not be called since parsing failed
            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should catch null transactionId and not crash")
        void nullTransactionId_NoCrash() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", null);
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            // Act & Assert
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should catch invalid sagaId format and not crash")
        void invalidSagaId_NoCrash() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", "invalid-uuid");
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            // Act & Assert
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            verifyNoInteractions(accountService);
        }

        @Test
        @DisplayName("Should catch null fromAccountId in DEBIT_REQUEST and publish DEBIT_FAILURE")
        void nullFromAccountId_PublishesFailure() {
            // Arrange - sagaId and transactionId are valid, but fromAccountId is null
            // This means parseUUID in the outer scope succeeds, but fails in handleDebitRequest
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", null);
            event.put("amount", amount);

            // Act & Assert - should not throw, handler catches exception
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            // Assert - accountService.withdraw should NOT be called
            verify(accountService, never()).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            // Assert - DEBIT_FAILURE should be published because the handler's catch(Exception e) catches it
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
        }

        @Test
        @DisplayName("Should catch null toAccountId in CREDIT_REQUEST and publish CREDIT_FAILURE")
        void nullToAccountId_PublishesFailure() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", null);
            event.put("amount", amount);

            // Act & Assert
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));

            verify(accountService, never()).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_FAILURE", published.get("eventType"));
        }

        @Test
        @DisplayName("Should handle amount as Integer in event map")
        void amountAsInteger() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", 100); // Integer instead of BigDecimal

            doNothing().when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify withdraw was called and amount was parsed correctly
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, new BigDecimal("100.0").compareTo(requestCaptor.getValue().getAmount()));
        }

        @Test
        @DisplayName("Should handle amount as String in event map")
        void amountAsString() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", "250.50"); // String amount

            doNothing().when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify withdraw was called with parsed amount
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, new BigDecimal("250.50").compareTo(requestCaptor.getValue().getAmount()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Event Publishing Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("Should publish to saga-events topic with sagaId as key")
        void publishEvent_CorrectTopicAndKey() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            doNothing().when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), any(Map.class));
        }

        @Test
        @DisplayName("Response event should contain all required fields")
        void responseEvent_ContainsAllFields() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", toAccountId.toString());
            event.put("amount", amount);

            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertNotNull(published.get("eventId"));
            assertNotNull(published.get("sagaId"));
            assertNotNull(published.get("transactionId"));
            assertNotNull(published.get("eventType"));
            assertNotNull(published.get("amount"));
            assertNotNull(published.get("currency"));
            assertNotNull(published.get("timestamp"));
            assertNull(published.get("errorMessage"));
        }

        @Test
        @DisplayName("Failure response event should contain error message")
        void failureEvent_ContainsErrorMessage() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            doThrow(new InsufficientBalanceException("Insufficient funds"))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertNotNull(published.get("errorMessage"));
            assertNotEquals("", published.get("errorMessage"));
        }
    }
}
