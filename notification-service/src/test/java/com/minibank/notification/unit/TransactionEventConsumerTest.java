package com.minibank.notification.unit;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.kafka.TransactionEventConsumer;
import com.minibank.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for TransactionEventConsumer.
 *
 * <p>Focuses on Redis-based idempotency behavior:
 * duplicate detection, key setting with TTL, and cache clearing.</p>
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TransactionEventConsumer consumer;

    private UUID testEventId;
    private TransactionEvent testEvent;

    @BeforeEach
    void setUp() {
        testEventId = UUID.randomUUID();
        testEvent = TransactionEvent.builder()
                .eventId(testEventId)
                .sagaId(UUID.randomUUID())
                .eventType(TransactionEvent.TransactionEventType.TRANSACTION_COMPLETED)
                .fromUserId(UUID.randomUUID())
                .toUserId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("TRY")
                .build();

        // Wire up valueOperations mock
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Idempotency Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency — Redis-based Duplicate Detection")
    class IdempotencyTests {

        @Test
        @DisplayName("First-time event: processes normally and sets Redis key")
        void consumeEvent_FirstTime_ProcessesAndSetsRedisKey() {
            // Arrange — Redis hasKey returns false (not processed before)
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(any(TransactionEvent.class)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testEvent.getFromUserId())
                            .build());

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — event was processed, Redis key was set
            verify(redisTemplate).hasKey("notification:event:" + testEventId);
            verify(notificationService).createFromTransactionEvent(testEvent);
            verify(notificationService).sendNotification(any(UUID.class));
            verify(valueOperations).set(eq("notification:event:" + testEventId), eq("1"), any(Duration.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("Duplicate event: skips processing, does not call notificationService")
        void consumeEvent_Duplicate_SkipsProcessing() {
            // Arrange — Redis hasKey returns true (already processed)
            when(redisTemplate.hasKey(anyString())).thenReturn(true);

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — event was skipped
            verify(redisTemplate).hasKey("notification:event:" + testEventId);
            verify(notificationService, never()).createFromTransactionEvent(any());
            verify(notificationService, never()).sendNotification(any());
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("Redis key is set with correct TTL duration")
        void consumeEvent_SetsKeyWithTTL() {
            // Arrange
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(notificationService.createFromTransactionEvent(any(TransactionEvent.class)))
                    .thenReturn(NotificationResponse.builder()
                            .id(UUID.randomUUID())
                            .userId(testEvent.getFromUserId())
                            .build());

            // Act
            consumer.consumeTransactionEvent(testEvent, "test-key", acknowledgment);

            // Assert — verify TTL is set via Duration argument
            ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations).set(
                    eq("notification:event:" + testEventId),
                    eq("1"),
                    durationCaptor.capture());

            // Default TTL is 24 hours (from @Value default)
            assertEquals(24, durationCaptor.getValue().toHours());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Utility Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {

        @Test
        @DisplayName("redisKey() produces correct format: notification:event:{eventId}")
        void redisKey_Format() {
            String result = consumer.redisKey("550e8400-e29b-41d4-a716-446655440000");
            assertEquals("notification:event:550e8400-e29b-41d4-a716-446655440000", result);
        }

        @Test
        @DisplayName("clearProcessedEvents() deletes Redis keys with correct pattern")
        void clearProcessedEvents_DeletesRedisKeys() {
            // Act
            consumer.clearProcessedEvents();

            // Assert — should call keys() with the correct pattern and then delete()
            verify(redisTemplate).keys("notification:event:*");
            verify(redisTemplate).delete(anySet());
        }
    }
}
