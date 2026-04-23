package com.minibank.account.unit;

import com.minibank.account.dto.BalanceUpdateRequest;
import com.minibank.account.entity.Account;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceUpdateRequest;

/**
     * Unit Tests for SagaCommandConsumer.
     *
     * Tests saga command routing, event parsing, and handler behavior
     * with mocked AccountService and KafkaTemplate.
     */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaCommandConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Map<String, Object>>> sendFuture;

    @InjectMocks
    private SagaCommandConsumer sagaCommandConsumer;

    private Account testAccount;
    private AccountResponse testAccountResponse;

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

        testAccount = Account.builder()
                .id(fromAccountId)
                .accountNumber("MB1234567890")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(Account.AccountStatus.ACTIVE)
                .build();

        testAccountResponse = AccountResponse.builder()
                .id(fromAccountId)
                .accountNumber("MB1234567890")
                .accountType("SAVINGS")
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .currency("TRY")
                .status("ACTIVE")
                .build();

        lenient().when(kafkaTemplate.send(anyString(), anyString(), any(Map.class)))
                .thenReturn(sendFuture);
        lenient().when(accountService.withdraw(any(UUID.class), any(BalanceUpdateRequest.class)))
                .thenReturn(testAccountResponse);
        lenient().when(accountService.deposit(any(UUID.class), any(BalanceUpdateRequest.class)))
                .thenReturn(testAccountResponse);
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
        @DisplayName("Should return null when value is null")
        void parseUUID_Null() {
            UUID result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseUUID", (Object) null);

            assertNull(result);
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

            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty string")
        void parseUUID_EmptyString() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                            "parseUUID", (Object) "")
            );

            assertNotNull(exception.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse Amount Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse Amount")
    class ParseAmountTests {

        @Test
        @DisplayName("Should return BigDecimal with scale 4")
        void parseAmount_BigDecimal() {
            BigDecimal bd = new BigDecimal("250.75");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) bd);

            assertEquals(new BigDecimal("250.7500"), result);
        }

        @Test
        @DisplayName("Should convert Integer to BigDecimal with scale 4")
        void parseAmount_Integer() {
            Integer intValue = 500;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) intValue);

            assertEquals(new BigDecimal("500.0000"), result);
        }

        @Test
        @DisplayName("Should convert Double 10.33 to BigDecimal with full precision")
        void parseAmount_Double_10_33() {
            Double doubleValue = 10.33;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) doubleValue);

            assertEquals(new BigDecimal("10.3300"), result);
        }

        @Test
        @DisplayName("Should convert Double 1000.0 to BigDecimal with scale 4")
        void parseAmount_Double_1000() {
            Double doubleValue = 1000.0;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) doubleValue);

            assertEquals(new BigDecimal("1000.0000"), result);
        }

        @Test
        @DisplayName("Should return BigDecimal.ZERO for null value")
        void parseAmount_Null() {
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) null);

            assertEquals(BigDecimal.ZERO, result);
        }

        @Test
        @DisplayName("Should parse numeric string to BigDecimal with scale 4")
        void parseAmount_NumericString() {
            String numericString = "1234.56";
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) numericString);

            assertEquals(new BigDecimal("1234.5600"), result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse Amount With Scale Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse Amount With Scale")
    class ParseAmountWithScaleTests {

        @Test
        @DisplayName("Should use custom scale when provided")
        void parseAmount_WithCustomScale() {
            BigDecimal bd = new BigDecimal("250.75");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", bd, 2);

            assertEquals(new BigDecimal("250.75"), result);
        }

        @Test
        @DisplayName("Should return default scale for null scale parameter")
        void parseAmount_NullScale_ReturnsDefault() {
            BigDecimal bd = new BigDecimal("100.5");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", bd, null);

            assertEquals(new BigDecimal("100.5000"), result);
        }

        @Test
        @DisplayName("Should return default scale for invalid scale string")
        void parseAmount_InvalidScaleString_ReturnsDefault() {
            BigDecimal bd = new BigDecimal("100.5");
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", bd, "invalid");

            assertEquals(new BigDecimal("100.5000"), result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse Scale Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse Scale")
    class ParseScaleTests {

        @Test
        @DisplayName("Should return default scale for null")
        void parseScale_Null_ReturnsDefault() {
            Integer result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseScale", (Object) null);

            assertEquals(4, result);
        }

        @Test
        @DisplayName("Should parse valid integer string")
        void parseScale_ValidString() {
            Integer result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseScale", "2");

            assertEquals(2, result);
        }

        @Test
        @DisplayName("Should return default for invalid string")
        void parseScale_InvalidString_ReturnsDefault() {
            Integer result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseScale", "abc");

            assertEquals(4, result);
        }

        @Test
        @DisplayName("Should return default for empty string")
        void parseScale_EmptyString_ReturnsDefault() {
            Integer result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseScale", "");

            assertEquals(4, result);
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
            doReturn(testAccountResponse).when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.withdraw was called
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).withdraw(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, amount.compareTo(requestCaptor.getValue().getAmount()));

            // Assert - verify DEBIT_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("DEBIT_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertEquals(fromAccountId.toString(), published.get("fromAccountId"));
            assertNull(published.get("toAccountId"));
            assertEquals(0, amount.compareTo((BigDecimal) published.get("amount")));
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
        @DisplayName("Should publish DEBIT_FAILURE and throw RuntimeException for unexpected exception")
        void debitRequest_UnexpectedException() {
            Map<String, Object> event = buildDebitEvent();
            doThrow(new RuntimeException("Database connection failed"))
                    .when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));

            assertTrue(thrown.getMessage().contains("Database connection failed"));

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
            doReturn(testAccountResponse).when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

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
            doReturn(testAccountResponse).when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.deposit was called
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).deposit(eq(toAccountId), requestCaptor.capture());
            assertEquals(0, amount.compareTo(requestCaptor.getValue().getAmount()));

            // Assert - verify CREDIT_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("CREDIT_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertNull(published.get("fromAccountId"));
            assertEquals(toAccountId.toString(), published.get("toAccountId"));
            assertEquals(0, amount.compareTo((BigDecimal) published.get("amount")));
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
        @DisplayName("Should publish CREDIT_FAILURE and throw RuntimeException for unexpected exception")
        void creditRequest_UnexpectedException() {
            Map<String, Object> event = buildCreditEvent();
            doThrow(new RuntimeException("Unexpected failure"))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));

            assertTrue(thrown.getMessage().contains("Unexpected failure"));

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
            doReturn(testAccountResponse).when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

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
            doReturn(testAccountResponse).when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

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
            doReturn(testAccountResponse).when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            // Act
            sagaCommandConsumer.consumeSagaCommand(event);

            // Assert - verify accountService.deposit was called with fromAccountId (refund)
            ArgumentCaptor<BalanceUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountService).deposit(eq(fromAccountId), requestCaptor.capture());
            assertEquals(0, amount.compareTo(requestCaptor.getValue().getAmount()));

            // Assert - verify COMPENSATE_SUCCESS published
            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_SUCCESS", published.get("eventType"));
            assertEquals(sagaId.toString(), published.get("sagaId"));
            assertEquals(transactionId.toString(), published.get("transactionId"));
            assertEquals(fromAccountId.toString(), published.get("fromAccountId"));
            assertNull(published.get("toAccountId"));
            assertEquals(0, amount.compareTo((BigDecimal) published.get("amount")));
            assertNull(published.get("errorMessage"));
            assertEquals("TRY", published.get("currency"));
        }

        @Test
        @DisplayName("Should publish COMPENSATE_FAILURE and throw RuntimeException when exception thrown")
        void compensateDebit_Failure() {
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new AccountNotFoundException(fromAccountId))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));

            assertTrue(thrown.getMessage().contains("Account not found"));

            ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
            verify(kafkaTemplate).send(eq("saga-events"), eq(sagaId.toString()), eventCaptor.capture());

            Map<String, Object> published = eventCaptor.getValue();
            assertEquals("COMPENSATE_FAILURE", published.get("eventType"));
            assertNotNull(published.get("errorMessage"));
            String errorMsg = (String) published.get("errorMessage");
            assertTrue(errorMsg.contains("Compensation failed"));
        }

        @Test
        @DisplayName("Should publish COMPENSATE_FAILURE and throw RuntimeException for RuntimeException")
        void compensateDebit_RuntimeException() {
            Map<String, Object> event = buildCompensateEvent();
            doThrow(new RuntimeException("Service unavailable"))
                    .when(accountService).deposit(eq(fromAccountId), any(BalanceUpdateRequest.class));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));

            assertTrue(thrown.getMessage().contains("Service unavailable"));

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
            doReturn(testAccountResponse).when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

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
        @DisplayName("Should throw NullPointerException for null event type")
        void unknownEventType_NullEventType() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", null);
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());

            assertThrows(NullPointerException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Create Response Event Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create Response Event")
    class CreateResponseEventTests {

        @Test
        @DisplayName("Should create response event with all fields")
        void createResponseEvent_WithAllFields() {
            Map<String, Object> event = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "createResponseEvent",
                    sagaId, transactionId, "DEBIT_SUCCESS",
                    fromAccountId, null, amount, null);

            assertNotNull(event);
            assertEquals("DEBIT_SUCCESS", event.get("eventType"));
            assertEquals(sagaId.toString(), event.get("sagaId"));
            assertEquals(fromAccountId.toString(), event.get("fromAccountId"));
        }

        @Test
        @DisplayName("Should handle null fromAccountId and toAccountId")
        void createResponseEvent_NullAccountIds() {
            Map<String, Object> event = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "createResponseEvent",
                    sagaId, transactionId, "CREDIT_SUCCESS",
                    null, toAccountId, amount, null);

            assertNotNull(event);
            assertNull(event.get("fromAccountId"));
            assertEquals(toAccountId.toString(), event.get("toAccountId"));
        }

        @Test
        @DisplayName("Should include error message when provided")
        void createResponseEvent_WithErrorMessage() {
            Map<String, Object> event = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "createResponseEvent",
                    sagaId, transactionId, "DEBIT_FAILURE",
                    fromAccountId, null, amount, "Insufficient balance");

            assertNotNull(event.get("errorMessage"));
            assertEquals("Insufficient balance", event.get("errorMessage"));
        }

        @Test
        @DisplayName("Should include TRY as default currency")
        void createResponseEvent_DefaultCurrency() {
            Map<String, Object> event = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "createResponseEvent",
                    sagaId, transactionId, "DEBIT_SUCCESS",
                    fromAccountId, null, amount, null);

            assertEquals("TRY", event.get("currency"));
        }

        @Test
        @DisplayName("Should include timestamp")
        void createResponseEvent_HasTimestamp() {
            Map<String, Object> event = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "createResponseEvent",
                    sagaId, transactionId, "DEBIT_SUCCESS",
                    fromAccountId, null, amount, null);

            assertNotNull(event.get("timestamp"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Credit Request Error Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Credit Request Error Path")
    class CreditRequestErrorPathTests {

        @Test
        @DisplayName("Should handle InactiveAccountException for credit request")
        void creditRequest_InactiveAccountException() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", toAccountId.toString());
            event.put("amount", amount);

            doThrow(new RuntimeException("Account inactive"))
                    .when(accountService).deposit(eq(toAccountId), any(BalanceUpdateRequest.class));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));

            assertTrue(thrown.getMessage().contains("Account inactive"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Long Value Amount Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Long Value Amount Parsing")
    class LongValueAmountTests {

        @Test
        @DisplayName("Should parse Long amount correctly")
        void parseAmount_Long() {
            Long longValue = 500L;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) longValue);

            assertEquals(new BigDecimal("500.0000"), result);
        }

        @Test
        @DisplayName("Should parse large Long amount correctly")
        void parseAmount_LargeLong() {
            Long longValue = 1000000L;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) longValue);

            assertEquals(new BigDecimal("1000000.0000"), result);
        }

        @Test
        @DisplayName("Should handle Float amount")
        void parseAmount_Float() {
            Float floatValue = 99.99f;
            BigDecimal result = ReflectionTestUtils.invokeMethod(sagaCommandConsumer,
                    "parseAmount", (Object) floatValue);

            assertNotNull(result);
            assertEquals(4, result.scale());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw RuntimeException for null sagaId")
        void nullSagaId_NoCrash() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", null);
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            assertThrows(RuntimeException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));
        }

        @Test
        @DisplayName("Should handle null transactionId")
        void nullTransactionId_NoCrash() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", null);
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid sagaId")
        void invalidSagaId_NoCrash() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", "invalid-uuid");
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", fromAccountId.toString());
            event.put("amount", amount);

            assertThrows(IllegalArgumentException.class,
                    () -> sagaCommandConsumer.consumeSagaCommand(event));
        }

        @Test
        @DisplayName("Should handle null fromAccountId")
        void nullFromAccountId_PublishesFailure() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "DEBIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("fromAccountId", null);
            event.put("amount", amount);

            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));
        }

        @Test
        @DisplayName("Should handle null toAccountId")
        void nullToAccountId_PublishesFailure() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CREDIT_REQUEST");
            event.put("sagaId", sagaId.toString());
            event.put("transactionId", transactionId.toString());
            event.put("toAccountId", null);
            event.put("amount", amount);

            assertDoesNotThrow(() -> sagaCommandConsumer.consumeSagaCommand(event));
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

            doReturn(testAccountResponse).when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

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

            doReturn(testAccountResponse).when(accountService).withdraw(eq(fromAccountId), any(BalanceUpdateRequest.class));

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

            doReturn(testAccountResponse).when(accountService).withdraw(any(UUID.class), any(BalanceUpdateRequest.class));

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

            doReturn(testAccountResponse).when(accountService).deposit(any(UUID.class), any(BalanceUpdateRequest.class));

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
