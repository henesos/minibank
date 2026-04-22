package com.minibank.notification.unit;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.kafka.TransactionEventConsumer;
import com.minibank.notification.repository.NotificationRepository;
import com.minibank.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionEventConsumer}.
 *
 * <p>Validates the three-layer idempotency strategy defined in ADR-013:</p>
 * <ol>
 *   <li>Redis SET NX EX — atomic duplicate detection</li>
 *   <li>DB double-check — fallback when Redis data is lost</li>
 *   <li>Exception rollback — Redis key deleted so event can be retried</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TransactionEventConsumer consumer;

    private UUID testEventId;
    private UUID testSagaId;
    private UUID testFromUserId;
    private UUID testToUserId;
    private TransactionEvent testEvent;
    private String expectedRedisKey;

    @BeforeEach
    void setUp() {
        testEventId = UUID.randomUUID();
        testSagaId = UUID.randomUUID();
        testFromUserId = UUID.randomUUID();
        testToUserId = UUID.randomUUID();

        testEvent = TransactionEvent.builder()
                .eventId(testEventId)
                .sagaId(testSagaId)
                .eventType(TransactionEvent.TransactionEventType.TRANSACTION_COMPLETED)
                .fromUserId(testFromUserId)
                .toUserId(testToUserId)
                .amount(new BigDecimal("500.00"))
                .currency("TRY")
                .build();

        expectedRedisKey = "notification:processed:" + testEventId;

        // Wire up valueOperations mock for all tests
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 1: Redis SET NX EX — Atomic Duplicate Detection
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Layer 1 — Redis SET NX EX Duplicate Detection")
    class RedisIdempotencyTests {

        @Test
        @DisplayName("First-time event: SET NX returns true, DB check false → processes and creates notifications")
        void consumeEvent_FirstTime_ProcessesAndSetsRedisKey() {
            // Arrange — Redis SET NX returns true (new key set), DB check returns false
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(testEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());
            when(notificationService.createFromTransactionEvent(eq(testEvent), eq(false)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testToUserId)
                            .build());

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — Redis SET NX was called with correct key and 24h TTL
            verify(valueOperations).setIfAbsent(
                    eq(expectedRedisKey), eq("1"), eq(Duration.ofHours(24)));

            // Assert — DB double-check was called (sender + receiver keys)
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-sender");
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-receiver");

            // Assert — Notification created and sent for both sender and receiver
            verify(notificationService).createFromTransactionEvent(testEvent, true);
            verify(notificationService).createFromTransactionEvent(testEvent, false);
            verify(notificationService, times(2)).sendNotification(any(UUID.class));

            // Assert — Message acknowledged exactly once (via finally block)
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Duplicate event (Redis hit): SET NX returns false → skips processing entirely")
        void consumeEvent_RedisHit_SkipsProcessing() {
            // Arrange — Redis SET NX returns false (key already exists)
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(false);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — event was skipped, NO DB check, NO notification created
            verify(notificationRepository, never()).existsByIdempotencyKey(anyString());
            verify(notificationService, never()).createFromTransactionEvent(any(), anyBoolean());
            verify(notificationService, never()).sendNotification(any());

            // Assert — Message acknowledged exactly once (via finally block)
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("SET NX returns null (Redis error): skips processing safely")
        void consumeEvent_SetIfAbsentNull_SkipsProcessing() {
            // Arrange — Redis SET NX returns null (connection error / timeout)
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(null);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — event was skipped for safety
            verify(notificationRepository, never()).existsByIdempotencyKey(anyString());
            verify(notificationService, never()).createFromTransactionEvent(any(), anyBoolean());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Redis SET NX is called with correct key format and TTL")
        void consumeEvent_RedisKeyFormat_IsCorrect() {
            // Arrange
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — key format: notification:processed:{eventId}, value: "1", TTL: 24h
            verify(valueOperations).setIfAbsent(
                    eq("notification:processed:" + testEventId),
                    eq("1"),
                    eq(Duration.ofHours(24)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 2: DB Double-Check (Redis Miss Fallback)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Layer 2 — DB Double-Check (Redis Miss Fallback)")
    class DbDoubleCheckTests {

        @Test
        @DisplayName("Redis miss + DB hit (sender): skips processing, acknowledges")
        void consumeEvent_RedisMiss_DbHitSender_SkipsProcessing() {
            // Arrange — Redis SET NX returns true (new), but DB has sender notification
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey("tx-" + testEventId + "-sender"))
                    .thenReturn(true);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — DB check was performed, processing skipped
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-sender");
            verify(notificationService, never()).createFromTransactionEvent(any(), anyBoolean());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Redis miss + DB hit (receiver only): skips processing, acknowledges")
        void consumeEvent_RedisMiss_DbHitReceiver_SkipsProcessing() {
            // Arrange — Redis SET NX true, sender not in DB, but receiver IS in DB
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey("tx-" + testEventId + "-sender"))
                    .thenReturn(false);
            when(notificationRepository.existsByIdempotencyKey("tx-" + testEventId + "-receiver"))
                    .thenReturn(true);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — both keys checked, processing skipped
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-sender");
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-receiver");
            verify(notificationService, never()).createFromTransactionEvent(any(), anyBoolean());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Redis miss + DB miss: processes event normally")
        void consumeEvent_RedisMiss_DbMiss_ProcessesEvent() {
            // Arrange — Redis SET NX true, neither sender nor receiver in DB
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey("tx-" + testEventId + "-sender"))
                    .thenReturn(false);
            when(notificationRepository.existsByIdempotencyKey("tx-" + testEventId + "-receiver"))
                    .thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(testEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());
            when(notificationService.createFromTransactionEvent(eq(testEvent), eq(false)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testToUserId)
                            .build());

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — both DB checks done, event processed
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-sender");
            verify(notificationRepository).existsByIdempotencyKey("tx-" + testEventId + "-receiver");
            verify(notificationService).createFromTransactionEvent(testEvent, true);
            verify(notificationService).createFromTransactionEvent(testEvent, false);
            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Layer 3: Exception Rollback — Redis Key Cleanup on Failure
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Layer 3 — Exception Rollback (Redis Key Cleanup)")
    class ExceptionRollbackTests {

        @Test
        @DisplayName("Exception during processing: Redis key deleted (rollback), message acknowledged")
        void consumeEvent_ExceptionDuringProcessing_DeletesRedisKey() {
            // Arrange — Redis SET NX succeeds, DB check passes, but processing throws
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(any(TransactionEvent.class), anyBoolean()))
                    .thenThrow(new RuntimeException("Kafka serialization failure"));

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — Redis key was deleted (rollback)
            verify(redisTemplate).delete(expectedRedisKey);

            // Assert — Message still acknowledged exactly once (via finally block)
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Exception during processing + Redis delete fails: logged but does not crash")
        void consumeEvent_ExceptionAndRedisDeleteFails_DoesNotCrash() {
            // Arrange — processing throws, AND Redis delete also throws
            when(valueOperations.setIfAbsent(eq(expectedRedisKey), eq("1"), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(any(TransactionEvent.class), anyBoolean()))
                    .thenThrow(new RuntimeException("Processing failure"));
            doThrow(new RuntimeException("Redis connection lost"))
                    .when(redisTemplate).delete(anyString());

            // Act — should NOT throw
            assertDoesNotThrow(() ->
                    consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment));

            // Assert — both operations attempted, message still acknowledged
            verify(redisTemplate).delete(expectedRedisKey);
            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Event Type Processing
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Type Processing")
    class EventTypeTests {

        @Test
        @DisplayName("TRANSACTION_COMPLETED: creates notification for both sender and receiver")
        void completedEvent_CreatesSenderAndReceiverNotifications() {
            // Arrange
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(any(), anyBoolean()))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(UUID.randomUUID())
                            .build());

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — sender and receiver notifications created
            verify(notificationService).createFromTransactionEvent(testEvent, true);
            verify(notificationService).createFromTransactionEvent(testEvent, false);
            verify(notificationService, times(2)).sendNotification(any(UUID.class));
        }

        @Test
        @DisplayName("TRANSACTION_FAILED: creates notification for sender only")
        void failedEvent_CreatesSenderNotificationOnly() {
            // Arrange — failed event
            TransactionEvent failedEvent = TransactionEvent.builder()
                    .eventId(testEventId)
                    .sagaId(testSagaId)
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_FAILED)
                    .fromUserId(testFromUserId)
                    .toUserId(testToUserId)
                    .amount(new BigDecimal("500.00"))
                    .currency("TRY")
                    .build();

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(failedEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());

            // Act
            consumer.consumeTransactionEvent(failedEvent, "test-key", acknowledgment);

            // Assert — only sender notification created
            verify(notificationService).createFromTransactionEvent(failedEvent, true);
            verify(notificationService, never()).createFromTransactionEvent(any(), eq(false));
            verify(notificationService, times(1)).sendNotification(any(UUID.class));
        }

        @Test
        @DisplayName("TRANSACTION_COMPLETED with null toUserId: creates sender notification only")
        void completedEvent_NullToUserId_CreatesSenderNotificationOnly() {
            // Arrange — toUserId is null
            TransactionEvent nullReceiverEvent = TransactionEvent.builder()
                    .eventId(testEventId)
                    .sagaId(testSagaId)
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_COMPLETED)
                    .fromUserId(testFromUserId)
                    .toUserId(null)
                    .amount(new BigDecimal("500.00"))
                    .currency("TRY")
                    .build();

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(nullReceiverEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());

            // Act
            consumer.consumeTransactionEvent(nullReceiverEvent, "test-key", acknowledgment);

            // Assert — only sender notification created (null toUserId check)
            verify(notificationService).createFromTransactionEvent(nullReceiverEvent, true);
            verify(notificationService, never()).createFromTransactionEvent(any(), eq(false));
        }

        @Test
        @DisplayName("COMPENSATION_COMPLETED: creates notification for sender only")
        void compensationEvent_CreatesSenderNotificationOnly() {
            TransactionEvent compensationEvent = TransactionEvent.builder()
                    .eventId(testEventId)
                    .sagaId(testSagaId)
                    .eventType(TransactionEvent.TransactionEventType.COMPENSATION_COMPLETED)
                    .fromUserId(testFromUserId)
                    .toUserId(testToUserId)
                    .amount(new BigDecimal("500.00"))
                    .currency("TRY")
                    .build();

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(compensationEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());

            consumer.consumeTransactionEvent(compensationEvent, "test-key", acknowledgment);

            verify(notificationService).createFromTransactionEvent(compensationEvent, true);
            verify(notificationService, never()).createFromTransactionEvent(any(), eq(false));
        }

        @Test
        @DisplayName("TRANSACTION_INITIATED: creates notification for sender only")
        void initiatedEvent_CreatesSenderNotificationOnly() {
            TransactionEvent initiatedEvent = TransactionEvent.builder()
                    .eventId(testEventId)
                    .sagaId(testSagaId)
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_INITIATED)
                    .fromUserId(testFromUserId)
                    .toUserId(testToUserId)
                    .amount(new BigDecimal("500.00"))
                    .currency("TRY")
                    .build();

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(eq(initiatedEvent), eq(true)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testFromUserId)
                            .build());

            consumer.consumeTransactionEvent(initiatedEvent, "test-key", acknowledgment);

            verify(notificationService).createFromTransactionEvent(initiatedEvent, true);
            verify(notificationService, never()).createFromTransactionEvent(any(), eq(false));
        }

        @Test
        @DisplayName("DEBIT_COMPLETED (default branch): no notification created")
        void debitCompletedEvent_NoNotificationCreated() {
            TransactionEvent debitEvent = TransactionEvent.builder()
                    .eventId(testEventId)
                    .sagaId(testSagaId)
                    .eventType(TransactionEvent.TransactionEventType.DEBIT_COMPLETED)
                    .fromUserId(testFromUserId)
                    .toUserId(testToUserId)
                    .amount(new BigDecimal("500.00"))
                    .currency("TRY")
                    .build();

            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(notificationRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

            consumer.consumeTransactionEvent(debitEvent, "test-key", acknowledgment);

            verify(notificationService, never()).createFromTransactionEvent(any(), anyBoolean());
        }
    }
}
