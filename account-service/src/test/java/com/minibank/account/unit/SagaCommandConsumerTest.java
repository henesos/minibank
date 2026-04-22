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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.ReflectionTestUtils;

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
 * <p>Covers saga command routing, event parsing, handler behavior,
 * H4 (manual ack + @Transactional coordination), and H8 (String-based
 * BigDecimal conversion) with mocked AccountService and KafkaTemplate.
 */
@ExtendWith(MockitoExtension.class)
class SagaCommandConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Map<String, Object>>> sendFuture;

    @Mock
    private Acknowledgment ack;

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
    // Parse Amount Tests (ADR-015: String-Based BigDecimal)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse Amount")
    class ParseAmountTests {

        @Test
        @DisplayName("Should return BigDecimal with default scale applied")
        void parseAmount_BigDecimal() {
            BigDecimal bd = new BigDecimal("250.75");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd, (Object) null);

            assertEquals(new BigDecimal("250.7500"), result);
            assertEquals(4, result.scale());
        }

        @Test
        @DisplayName("Should return BigDecimal with explicit scale applied")
        void parseAmount_BigDecimal_WithScale() {
            BigDecimal bd = new BigDecimal("250.75");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd, (Object) 2);

            assertEquals(new BigDecimal("250.75"), result);
            assertEquals(2, result.scale());
        }

        @Test
        @DisplayName("Should convert Integer to BigDecimal via String (no precision loss)")
        void parseAmount_Integer() {
            Integer intValue = 500;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) intValue, (Object) null);

            assertEquals(new BigDecimal("500.0000"), result);
            assertEquals(4, result.scale());
        }

        @Test
        @DisplayName("Should convert Double to BigDecimal via String (ADR-015: no doubleValue())")
        void parseAmount_Double() {
            Double doubleValue = 99.99;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) doubleValue, (Object) null);

            // Double.toString(99.99) = "99.99" → BigDecimal("99.99") = exact
            assertEquals(0, new BigDecimal("99.9900").compareTo(result));
            assertEquals(4, result.scale());
        }

        @Test
        @DisplayName("Should preserve precision for problematic Double values")
        void parseAmount_Double_ProblematicValue() {
            // Double 10.33 has exact representation issues, but toString() gives "10.33"
            Double doubleValue = 10.33;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) doubleValue, (Object) null);

            assertEquals(0, new BigDecimal("10.3300").compareTo(result));
        }

        @Test
        @DisplayName("Should parse numeric string to BigDecimal")
        void parseAmount_NumericString() {
            String numericString = "1234.56";
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) numericString, (Object) null);

            assertEquals(new BigDecimal("1234.5600"), result);
            assertEquals(4, result.scale());
        }

        @Test
        @DisplayName("Should parse Long to BigDecimal via String")
        void parseAmount_Long() {
            Long longValue = 1000L;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) longValue, (Object) null);

            assertEquals(new BigDecimal("1000.0000"), result);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null value")
        void parseAmount_Null() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) null, (Object) null)
            );

            assertTrue(exception.getMessage().contains("Amount value is null"));
        }

        @Test
        @DisplayName("Should throw NumberFormatException for non-numeric string")
        void parseAmount_NonNumericString() {
            String nonNumeric = "abc";

            assertThrows(NumberFormatException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) nonNumeric, (Object) null)
            );
        }

        @Test
        @DisplayName("Should throw NumberFormatException for empty string")
        void parseAmount_EmptyString() {
            assertThrows(NumberFormatException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseAmount", (Object) "", (Object) null)
            );
        }

        @Test
        @DisplayName("Should respect explicit scale parameter")
        void parseAmount_ExplicitScale() {
            BigDecimal bd = new BigDecimal("100.12345");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd, (Object) 2);

            assertEquals(new BigDecimal("100.12"), result);
            assertEquals(2, result.scale());
        }

        @Test
        @DisplayName("Should default to scale 4 when scaleObj is null")
        void parseAmount_NullScale_DefaultsTo4() {
            BigDecimal bd = new BigDecimal("50.5");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd, (Object) null);

            assertEquals(4, result.scale());
        }

        @Test
        @DisplayName("Should default to scale 4 when scaleObj is invalid")
        void parseAmount_InvalidScale_DefaultsTo4() {
            BigDecimal bd = new BigDecimal("50.5");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd, (Object) "not-a-number");

            assertEquals(4, result.scale());
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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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

            // ADR-014: ack should be called (defensive path — no active tx in unit test)
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE when InsufficientBalanceException thrown")
        void debitRequest_InsufficientBalance() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new InsufficientBalanceException(fromAccountId, amount, new BigDecimal("50.00")))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify DEBIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Insufficient balance"));

            // ADR-014: ack should be called (business exception, no rollback needed)
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE when AccountNotFoundException thrown")
        void debitRequest_AccountNotFound() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new AccountNotFoundException(fromAccountId))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify DEBIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Account not found"));

            // ADR-014: ack should be called (business exception)
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should propagate unexpected exception and NOT ack (ADR-014 rollback scenario)")
        void debitRequest_UnexpectedException_PropagatesAndNoAck() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doThrow(new RuntimeException("Database connection failed"))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act & Assert - RuntimeException propagates from handler (no catch(Exception))
            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            // ADR-014: ack should NOT be called — rollback, Kafka redelivers
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should pass correct fromAccountId to withdraw")
        void debitRequest_CorrectAccountId() {
            // Arrange
            Map<String, Object> event = buildDebitEvent();
            doNothing().when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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

            // ADR-014: ack should be called
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should publish CREDIT_FAILURE when AccountNotFoundException thrown")
        void creditRequest_AccountNotFound() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doThrow(new AccountNotFoundException(toAccountId))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify CREDIT_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Account not found"));

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should propagate unexpected exception and NOT ack (ADR-014)")
        void creditRequest_UnexpectedException_PropagatesAndNoAck() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doThrow(new RuntimeException("Unexpected failure"))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            // ADR-014: ack should NOT be called
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should pass correct toAccountId to deposit")
        void creditRequest_CorrectAccountId() {
            // Arrange
            Map<String, Object> event = buildCreditEvent();
            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should publish COMPENSATE_FAILURE when exception thrown")
        void compensateDebit_Failure() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new AccountNotFoundException(fromAccountId))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify COMPENSATE_FAILURE published with error message
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Compensation failed"));

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should propagate RuntimeException and NOT ack (ADR-014)")
        void compensateDebit_RuntimeException_PropagatesAndNoAck() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new RuntimeException("Service unavailable"))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            // ADR-014: ack should NOT be called — RuntimeException from handler propagates
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should not call withdraw for compensate debit")
        void compensateDebit_DoesNotCallWithdraw() {
            // Arrange
            Map<String, Object> event = buildCompensateEvent();
            doNothing().when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("Should ack for unknown event type (don't retry malformed messages)")
        void unknownEventType_ShouldAck() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "UNKNOWN_EVENT");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert — unknown events should be acked to prevent infinite retry
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should ack immediately for null event type (ADR-014)")
        void nullEventType_ShouldAckImmediately() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", null);
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert — null eventType is malformed, ack immediately, no retry
            verify(ack).acknowledge();
            verifyNoInteractions(accountService);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("Should ack immediately for invalid saga ID (ADR-014)")
        void invalidSagaId_ShouldAckImmediately() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", "not-a-uuid");
            event.put("transactionId", transactionId.toString());

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert — malformed identifiers should be acked to prevent infinite retry
            verify(ack).acknowledge();
            verifyNoInteractions(accountService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should publish DEBIT_FAILURE for null fromAccountId (parse error)")
        void nullFromAccountId_PublishesFailure() {
            // Arrange - sagaId and transactionId are valid, but fromAccountId is null
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", null);
            event.put("amount", amount);

            // Act & Assert — should not throw, handler catches IllegalArgumentException
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            // Assert - accountService.withdraw should NOT be called
            verify(accountService, never()).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            // Assert - DEBIT_FAILURE should be published (parse error)
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            assertTrue(((String) published.get("errorMessage")).contains("Invalid parameters"));

            // Should be acked (parse error, no retry needed)
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should publish CREDIT_FAILURE for null toAccountId (parse error)")
        void nullToAccountId_PublishesFailure() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", null);
            event.put("amount", amount);

            // Act & Assert
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            verify(accountService, never()).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_FAILURE", published.get("eventType"));

            verify(ack).acknowledge();
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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify withdraw was called and amount was parsed correctly
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, new BigDecimal("100.0000").compareTo(requestCaptor.getValue().getAmount()));
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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert - verify withdraw was called with parsed amount
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, new BigDecimal("250.5000").compareTo(requestCaptor.getValue().getAmount()));
        }

        @Test
        @DisplayName("Should handle amount as Double without precision loss (ADR-015)")
        void amountAsDouble_NoPrecisionLoss() {
            // Arrange — Double 10.33 is a classic precision problem value
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", 10.33); // Double

            doNothing().when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert — String-based conversion preserves "10.33" exactly
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            BigDecimal parsedAmount = requestCaptor.getValue().getAmount();
            assertEquals(0, new BigDecimal("10.3300").compareTo(parsedAmount));
        }

        @Test
        @DisplayName("Should publish DEBIT_FAILURE for null amount (parse error)")
        void nullAmount_PublishesFailure() {
            // Arrange
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", null);

            // Act & Assert — handler catches IllegalArgumentException from parseAmount
            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            verify(accountService, never()).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());
            assertEquals("DEBIT_FAILURE", eventCaptor.getValue().get("eventType"));

            verify(ack).acknowledge();
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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

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
            sagaCommandConsumer.consumeSagaCommand(event, ack);

            // Assert
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertNotNull(published.get("errorMessage"));
            assertNotEquals("", published.get("errorMessage"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // H4: Manual Ack + Transaction Coordination Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("H4: Manual Ack Coordination (ADR-014)")
    class ManualAckTests {

        @Test
        @DisplayName("Should ack after successful debit")
        void successfulDebit_ShouldAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            doNothing().when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            sagaCommandConsumer.consumeSagaCommand(event, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should ack after business exception (InsufficientBalance)")
        void businessException_ShouldAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            doThrow(new InsufficientBalanceException(fromAccountId, amount, BigDecimal.ZERO))
                    .when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            sagaCommandConsumer.consumeSagaCommand(event, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should NOT ack after unexpected RuntimeException (rollback scenario)")
        void unexpectedException_ShouldNotAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            doThrow(new RuntimeException("Unexpected DB error"))
                    .when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should ack for unknown event type (skip, don't retry)")
        void unknownEvent_ShouldAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "UNKNOWN");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            sagaCommandConsumer.consumeSagaCommand(event, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should ack for null event type (skip malformed)")
        void nullEventType_ShouldAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", null);

            sagaCommandConsumer.consumeSagaCommand(event, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should ack for invalid sagaId (skip malformed)")
        void invalidSagaId_ShouldAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", "invalid");
            event.put("transactionId", transactionId.toString());

            sagaCommandConsumer.consumeSagaCommand(event, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should NOT ack when credit RuntimeException propagates")
        void creditRuntimeException_ShouldNotAck() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", toAccountId.toString());
            event.put("amount", amount);

            doThrow(new RuntimeException("Kafka timeout"))
                    .when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event, ack));

            verify(ack, never()).acknowledge();
        }
    }
}
