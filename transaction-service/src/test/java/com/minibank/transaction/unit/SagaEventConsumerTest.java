package com.minibank.transaction.unit;

import com.minibank.transaction.config.SagaEventConsumer;
import com.minibank.transaction.saga.SagaEvent;
import com.minibank.transaction.saga.SagaOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaEventConsumer")
class SagaEventConsumerTest {

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @InjectMocks
    private SagaEventConsumer sagaEventConsumer;

    private SagaEvent.SagaEventBuilder baseEventBuilder() {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .timestamp(LocalDateTime.now())
                .retryCount(0);
    }

    @Nested
    @DisplayName("Debit Event Handling")
    class DebitEventHandling {

        @Test
        @DisplayName("DEBIT_SUCCESS should delegate to sagaOrchestrator.handleDebitSuccess")
        void handleDebitSuccess() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.DEBIT_SUCCESS)
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleDebitSuccess(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }

        @Test
        @DisplayName("DEBIT_FAILURE should delegate to sagaOrchestrator.handleDebitFailure")
        void handleDebitFailure() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.DEBIT_FAILURE)
                    .errorMessage("Insufficient funds")
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleDebitFailure(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }
    }

    @Nested
    @DisplayName("Credit Event Handling")
    class CreditEventHandling {

        @Test
        @DisplayName("CREDIT_SUCCESS should delegate to sagaOrchestrator.handleCreditSuccess")
        void handleCreditSuccess() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.CREDIT_SUCCESS)
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCreditSuccess(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }

        @Test
        @DisplayName("CREDIT_FAILURE should delegate to sagaOrchestrator.handleCreditFailure")
        void handleCreditFailure() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.CREDIT_FAILURE)
                    .errorMessage("Account frozen")
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCreditFailure(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }
    }

    @Nested
    @DisplayName("Compensation Event Handling")
    class CompensationEventHandling {

        @Test
        @DisplayName("COMPENSATE_SUCCESS should delegate to sagaOrchestrator.handleCompensateSuccess")
        void handleCompensateSuccess() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.COMPENSATE_SUCCESS)
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCompensateSuccess(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }

        @Test
        @DisplayName("COMPENSATE_FAILURE should delegate to sagaOrchestrator.handleCompensateFailure")
        void handleCompensateFailure() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.COMPENSATE_FAILURE)
                    .errorMessage("Compensation failed - manual intervention required")
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCompensateFailure(event);
            verifyNoMoreInteractions(sagaOrchestrator);
        }
    }

    @Nested
    @DisplayName("Unknown Event Type")
    class UnknownEventType {

        @Test
        @DisplayName("SAGA_COMPLETE should not call any orchestrator method")
        void sagaComplete_noOrchestratorMethodCalled() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.SAGA_COMPLETE)
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator, never()).handleDebitSuccess(any());
            verify(sagaOrchestrator, never()).handleDebitFailure(any());
            verify(sagaOrchestrator, never()).handleCreditSuccess(any());
            verify(sagaOrchestrator, never()).handleCreditFailure(any());
            verify(sagaOrchestrator, never()).handleCompensateSuccess(any());
            verify(sagaOrchestrator, never()).handleCompensateFailure(any());
        }

        @Test
        @DisplayName("SAGA_FAIL should not call any orchestrator method")
        void sagaFail_noOrchestratorMethodCalled() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.SAGA_FAIL)
                    .errorMessage("Saga failed")
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator, never()).handleDebitSuccess(any());
            verify(sagaOrchestrator, never()).handleDebitFailure(any());
            verify(sagaOrchestrator, never()).handleCreditSuccess(any());
            verify(sagaOrchestrator, never()).handleCreditFailure(any());
            verify(sagaOrchestrator, never()).handleCompensateSuccess(any());
            verify(sagaOrchestrator, never()).handleCompensateFailure(any());
        }

        @Test
        @DisplayName("Unrecognized event type string should not call any orchestrator method")
        void unrecognizedEventType_noOrchestratorMethodCalled() {
            SagaEvent event = baseEventBuilder()
                    .eventType("UNKNOWN_EVENT_TYPE")
                    .build();

            sagaEventConsumer.handleSagaEvent(event);

            verifyNoInteractions(sagaOrchestrator);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("When handleDebitSuccess throws exception, no exception propagates")
        void handleDebitSuccess_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.DEBIT_SUCCESS)
                    .build();

            doThrow(new RuntimeException("Database connection failed"))
                    .when(sagaOrchestrator).handleDebitSuccess(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleDebitSuccess(event);
            // No exception should propagate - the consumer catches it internally
        }

        @Test
        @DisplayName("When handleDebitFailure throws exception, no exception propagates")
        void handleDebitFailure_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.DEBIT_FAILURE)
                    .build();

            doThrow(new IllegalStateException("Transaction not found"))
                    .when(sagaOrchestrator).handleDebitFailure(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleDebitFailure(event);
        }

        @Test
        @DisplayName("When handleCreditSuccess throws exception, no exception propagates")
        void handleCreditSuccess_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.CREDIT_SUCCESS)
                    .build();

            doThrow(new RuntimeException("Outbox save failed"))
                    .when(sagaOrchestrator).handleCreditSuccess(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCreditSuccess(event);
        }

        @Test
        @DisplayName("When handleCreditFailure throws exception, no exception propagates")
        void handleCreditFailure_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.CREDIT_FAILURE)
                    .build();

            doThrow(new RuntimeException("Service unavailable"))
                    .when(sagaOrchestrator).handleCreditFailure(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCreditFailure(event);
        }

        @Test
        @DisplayName("When handleCompensateSuccess throws exception, no exception propagates")
        void handleCompensateSuccess_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.COMPENSATE_SUCCESS)
                    .build();

            doThrow(new RuntimeException("Entity not found"))
                    .when(sagaOrchestrator).handleCompensateSuccess(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCompensateSuccess(event);
        }

        @Test
        @DisplayName("When handleCompensateFailure throws exception, no exception propagates")
        void handleCompensateFailure_exception_doesNotPropagate() {
            SagaEvent event = baseEventBuilder()
                    .eventType(SagaEvent.EventType.COMPENSATE_FAILURE)
                    .build();

            doThrow(new RuntimeException("Critical failure"))
                    .when(sagaOrchestrator).handleCompensateFailure(event);

            sagaEventConsumer.handleSagaEvent(event);

            verify(sagaOrchestrator).handleCompensateFailure(event);
        }
    }
}
