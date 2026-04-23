package com.minibank.transaction.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minibank.transaction.config.OutboxPublisher;
import com.minibank.transaction.outbox.OutboxEvent;
import com.minibank.transaction.outbox.OutboxRepository;
import com.minibank.transaction.saga.SagaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, SagaEvent> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "commandsTopic", "saga-commands");
        ReflectionTestUtils.setField(outboxPublisher, "eventsTopic", "saga-events");
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
    }

    @Nested
    @DisplayName("Publish Pending Events")
    class PublishPendingEventsTests {

        @Test
        @DisplayName("Should publish pending events successfully")
        void publishPendingEvents_Success() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(outboxRepository).save(argThat(e -> e.getStatus() == OutboxEvent.EventStatus.SENT));
        }

        @Test
        @DisplayName("Should not publish when no pending events")
        void publishPendingEvents_NoEvents() {
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should mark event as failed on publish error")
        void publishPendingEvents_Failure_MarksAsFailed() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka unavailable"));

            outboxPublisher.publishPendingEvents();

            verify(outboxRepository).save(argThat(e -> e.getStatus() == OutboxEvent.EventStatus.FAILED));
        }

        @Test
        @DisplayName("Should publish DEBIT_REQUESTED to commands topic")
        void publishPendingEvents_DebitRequested_ToCommandsTopic() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("saga-commands"), anyString(), any(SagaEvent.class));
        }

        @Test
        @DisplayName("Should publish CREDIT_REQUESTED to commands topic")
        void publishPendingEvents_CreditRequested_ToCommandsTopic() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.CREDIT_REQUESTED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("saga-commands"), anyString(), any(SagaEvent.class));
        }

        @Test
        @DisplayName("Should publish COMPENSATION_REQUESTED to commands topic")
        void publishPendingEvents_CompensationRequested_ToCommandsTopic() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.COMPENSATION_REQUESTED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("saga-commands"), anyString(), any(SagaEvent.class));
        }

        @Test
        @DisplayName("Should publish SAGA_COMPLETED to events topic")
        void publishPendingEvents_SagaCompleted_ToEventsTopic() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.SAGA_COMPLETED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("saga-events"), anyString(), any(SagaEvent.class));
        }

        @Test
        @DisplayName("Should publish SAGA_FAILED to events topic")
        void publishPendingEvents_SagaFailed_ToEventsTopic() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.SAGA_FAILED);
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send(eq("saga-events"), anyString(), any(SagaEvent.class));
        }
    }

    @Nested
    @DisplayName("Retry Failed Events")
    class RetryFailedEventsTests {

        @Test
        @DisplayName("Should retry failed events successfully")
        void retryFailedEvents_Success() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            event.setStatus(OutboxEvent.EventStatus.FAILED);
            event.setRetryCount(1);
            when(outboxRepository.findRetryableEvents(3)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.retryFailedEvents();

            verify(outboxRepository).save(argThat(e -> e.getStatus() == OutboxEvent.EventStatus.SENT));
            assertEquals(2, event.getRetryCount());
        }

        @Test
        @DisplayName("Should not retry when no failed events")
        void retryFailedEvents_NoEvents() {
            when(outboxRepository.findRetryableEvents(3)).thenReturn(List.of());

            outboxPublisher.retryFailedEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should mark as failed when retry also fails")
        void retryFailedEvents_Failure_MarksAsFailed() throws Exception {
            OutboxEvent event = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            event.setStatus(OutboxEvent.EventStatus.FAILED);
            event.setRetryCount(1);
            when(outboxRepository.findRetryableEvents(3)).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka still unavailable"));

            outboxPublisher.retryFailedEvents();

            verify(outboxRepository).save(argThat(e -> e.getStatus() == OutboxEvent.EventStatus.FAILED));
        }

        @Test
        @DisplayName("Should handle multiple failed events")
        void retryFailedEvents_MultipleEvents() throws Exception {
            OutboxEvent event1 = createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED);
            event1.setStatus(OutboxEvent.EventStatus.FAILED);
            OutboxEvent event2 = createOutboxEvent(OutboxEvent.EventType.CREDIT_REQUESTED);
            event2.setStatus(OutboxEvent.EventStatus.FAILED);
            when(outboxRepository.findRetryableEvents(3)).thenReturn(List.of(event1, event2));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            outboxPublisher.retryFailedEvents();

            verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
        }
    }

    @Nested
    @DisplayName("Cleanup Old Events")
    class CleanupOldEventsTests {

        @Test
        @DisplayName("Should cleanup old sent events")
        void cleanupOldEvents_DeletesOldEvents() {
            when(outboxRepository.deleteOldEvents(any(LocalDateTime.class))).thenReturn(5);

            outboxPublisher.cleanupOldEvents();

            verify(outboxRepository).deleteOldEvents(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should not log when nothing to cleanup")
        void cleanupOldEvents_NoEventsToDelete() {
            when(outboxRepository.deleteOldEvents(any(LocalDateTime.class))).thenReturn(0);

            outboxPublisher.cleanupOldEvents();

            verify(outboxRepository).deleteOldEvents(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty pending events list")
        void publishPendingEvents_EmptyList() {
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(List.of());

            assertDoesNotThrow(() -> outboxPublisher.publishPendingEvents());
        }

        @Test
        @DisplayName("Should handle all event types correctly")
        void publishPendingEvents_AllEventTypes() throws Exception {
            List<OutboxEvent> events = List.of(
                    createOutboxEvent(OutboxEvent.EventType.DEBIT_REQUESTED),
                    createOutboxEvent(OutboxEvent.EventType.CREDIT_REQUESTED),
                    createOutboxEvent(OutboxEvent.EventType.COMPENSATION_REQUESTED),
                    createOutboxEvent(OutboxEvent.EventType.SAGA_COMPLETED),
                    createOutboxEvent(OutboxEvent.EventType.SAGA_FAILED)
            );
            when(outboxRepository.findPendingEventsWithLimit(100)).thenReturn(events);
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            assertDoesNotThrow(() -> outboxPublisher.publishPendingEvents());
            verify(kafkaTemplate, times(5)).send(anyString(), anyString(), any(SagaEvent.class));
        }
    }

    private OutboxEvent createOutboxEvent(OutboxEvent.EventType eventType) {
        UUID sagaId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        SagaEvent sagaEvent = SagaEvent.builder()
                .eventId(UUID.randomUUID())
                .sagaId(sagaId)
                .transactionId(transactionId)
                .eventType(SagaEvent.EventType.DEBIT_REQUEST)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("TRY")
                .timestamp(LocalDateTime.now())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String payload;
        try {
            payload = mapper.writeValueAsString(sagaEvent);
        } catch (Exception e) {
            payload = "{}";
        }

        return OutboxEvent.builder()
                .sagaId(sagaId)
                .transactionId(transactionId)
                .eventType(eventType)
                .aggregateType("Transaction")
                .aggregateId(transactionId)
                .payload(payload)
                .status(OutboxEvent.EventStatus.PENDING)
                .build();
    }
}