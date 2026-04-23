package com.minibank.notification.unit;

import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.entity.Notification;
import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
import com.minibank.notification.exception.DuplicateNotificationException;
import com.minibank.notification.exception.NotificationNotFoundException;
import com.minibank.notification.repository.NotificationRepository;
import com.minibank.notification.service.EmailService;
import com.minibank.notification.exception.NotificationServiceException;
import com.minibank.notification.service.NotificationService;
import com.minibank.notification.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UUID notificationId;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        testNotification = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("Test Subject")
                .content("Test Content")
                .maxRetries(3)
                .retryCount(0)
                .build();

        ReflectionTestUtils.setField(notificationService, "defaultMaxRetries", 3);
    }

    @Nested
    @DisplayName("Create Notification Tests")
    class CreateNotificationTests {

        @Test
        @DisplayName("createNotification - should create notification successfully")
        void createNotification_Success() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Test Subject")
                    .content("Test Content")
                    .build();

            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createNotification(request);

            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertEquals(NotificationType.EMAIL, response.getType());
            assertEquals(NotificationStatus.PENDING, response.getStatus());
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("createNotification - should throw DuplicateNotificationException for duplicate idempotency key")
        void createNotification_DuplicateIdempotencyKey() {
            String idempotencyKey = "test-key-123";
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .content("Test Content")
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(notificationRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(testNotification));

            assertThrows(DuplicateNotificationException.class, 
                    () -> notificationService.createNotification(request));
            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Send Notification Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("sendNotification - should send email notification successfully")
        void sendNotification_EmailSuccess() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            verify(emailService).send(any(Notification.class));
        }

        @Test
        @DisplayName("sendNotification - should throw NotificationNotFoundException for invalid ID")
        void sendNotification_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class, 
                    () -> notificationService.sendNotification(notificationId));
        }

        @Test
        @DisplayName("sendNotification - should handle send failure")
        void sendNotification_SendFailure() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            assertEquals(NotificationStatus.FAILED, testNotification.getStatus());
        }

        @Test
        @DisplayName("sendNotification - should return early if already sent")
        void sendNotification_AlreadySent() {
            testNotification.markAsSent();
            testNotification.setStatus(NotificationStatus.SENT);
            
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            verify(emailService, never()).send(any());
        }
    }

    @Nested
    @DisplayName("Get Notification Tests")
    class GetNotificationTests {

        @Test
        @DisplayName("getNotification - should return notification by ID")
        void getNotification_Success() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));

            NotificationResponse response = notificationService.getNotification(notificationId);

            assertNotNull(response);
            assertEquals(notificationId, response.getId());
        }

        @Test
        @DisplayName("getNotification - should throw NotificationNotFoundException")
        void getNotification_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class, 
                    () -> notificationService.getNotification(notificationId));
        }
    }

    @Nested
    @DisplayName("Get User Notifications Tests")
    class GetUserNotificationsTests {

        @Test
        @DisplayName("getUserNotifications - should return paginated notifications")
        void getUserNotifications_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            List<Notification> notifications = List.of(testNotification);
            Page<Notification> page = new PageImpl<>(notifications);

            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                    .thenReturn(page);

            Page<NotificationResponse> response = notificationService
                    .getUserNotifications(userId, pageable);

            assertNotNull(response);
            assertEquals(1, response.getTotalElements());
            assertEquals(1, response.getContent().size());
        }
    }

    @Nested
    @DisplayName("Transaction Event Tests")
    class TransactionEventTests {

        @Test
        @DisplayName("createFromTransactionEvent - should create notification from completed event")
        void createFromTransactionEvent_Completed() {
            TransactionEvent event = TransactionEvent.builder()
                    .eventId(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_COMPLETED)
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            when(notificationRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createFromTransactionEvent(event);

            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertNotNull(response.getSubject());
            assertNotNull(response.getContent());
        }

        @Test
        @DisplayName("createFromTransactionEvent - should create notification from failed event")
        void createFromTransactionEvent_Failed() {
            TransactionEvent event = TransactionEvent.builder()
                    .eventId(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_FAILED)
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .failureReason("Insufficient balance")
                    .build();

            when(notificationRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createFromTransactionEvent(event);

            assertNotNull(response);
            assertTrue(response.getSubject().contains("Başarısız"));
        }
    }

    @Nested
    @DisplayName("Process Pending Notifications Tests")
    class ProcessPendingTests {

        @Test
        @DisplayName("processPendingNotifications - should process all pending notifications")
        void processPendingNotifications_Success() {
            List<Notification> pendingNotifications = List.of(testNotification);
            
            when(notificationRepository.findPendingNotifications(100))
                    .thenReturn(pendingNotifications);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            int processed = notificationService.processPendingNotifications();

            assertEquals(1, processed);
        }
    }

    @Nested
    @DisplayName("Notification Status Tests")
    class NotificationStatusTests {

        @Test
        @DisplayName("canRetry - should return true when retries remaining")
        void canRetry_True() {
            assertTrue(testNotification.canRetry());
        }

        @Test
        @DisplayName("canRetry - should return false when max retries reached")
        void canRetry_False() {
            testNotification.setRetryCount(3);

            assertFalse(testNotification.canRetry());
        }

        @Test
        @DisplayName("markAsSent - should set sent status and timestamp")
        void markAsSent() {
            testNotification.markAsSent();

            assertEquals(NotificationStatus.SENT, testNotification.getStatus());
            assertNotNull(testNotification.getSentAt());
        }

        @Test
        @DisplayName("markAsFailed - should set failed status and error message")
        void markAsFailed() {
            testNotification.markAsFailed("Test error");

            assertEquals(NotificationStatus.FAILED, testNotification.getStatus());
            assertEquals("Test error", testNotification.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Delete Notification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("deleteNotification - should soft delete notification")
        void deleteNotification_Success() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            notificationService.deleteNotification(notificationId);

            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("deleteNotification - should throw NotificationNotFoundException")
        void deleteNotification_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.deleteNotification(notificationId));
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("markAsRead - should mark notification as read")
        void markAsRead_Success() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            NotificationResponse response = notificationService.markAsRead(notificationId);

            assertNotNull(response);
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("markAsRead - should throw NotificationNotFoundException")
        void markAsRead_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.markAsRead(notificationId));
        }
    }

    @Nested
    @DisplayName("Unread Count Tests")
    class UnreadCountTests {

        @Test
        @DisplayName("getUnreadCount - should return unread count")
        void getUnreadCount_Success() {
            when(notificationRepository.countByUserIdAndReadFalse(userId))
                    .thenReturn(5L);

            long count = notificationService.getUnreadCount(userId);

            assertEquals(5L, count);
        }

        @Test
        @DisplayName("getUnreadCount - should return 0 when no unread")
        void getUnreadCount_Zero() {
            when(notificationRepository.countByUserIdAndReadFalse(userId))
                    .thenReturn(0L);

            long count = notificationService.getUnreadCount(userId);

            assertEquals(0L, count);
        }
    }

    @Nested
    @DisplayName("SMS Notification Tests")
    class SmsNotificationTests {

        @Test
        @DisplayName("sendNotification - should send SMS notification successfully")
        void sendNotification_SmsSuccess() {
            testNotification.setType(NotificationType.SMS);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(smsService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            verify(smsService).send(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("Push Notification Tests")
    class PushNotificationTests {

        @Test
        @DisplayName("sendNotification - should send push notification successfully")
        void sendNotification_PushSuccess() {
            testNotification.setType(NotificationType.PUSH);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
        }
    }

    @Nested
    @DisplayName("Retry Tests")
    class RetryTests {

        @Test
        @DisplayName("sendNotification - should throw exception when max retries reached")
        void sendNotification_MaxRetriesReached() {
            testNotification.setRetryCount(3);
            testNotification.setMaxRetries(3);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));

            assertThrows(NotificationServiceException.class,
                    () -> notificationService.sendNotification(notificationId));
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("sendNotification - should retry on first failure and succeed on second attempt")
        void sendNotification_RetryThenSuccess() {
            Notification failedNotification = Notification.builder()
                    .id(notificationId)
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .subject("Test Subject")
                    .content("Test Content")
                    .maxRetries(3)
                    .retryCount(0)
                    .build();

            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(failedNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(false, true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            assertEquals(1, failedNotification.getRetryCount());
            verify(emailService, times(2)).send(any(Notification.class));
        }

        @Test
        @DisplayName("sendNotification - should mark as failed after max retries")
        void sendNotification_FailAfterMaxRetries() {
            Notification failedNotification = Notification.builder()
                    .id(notificationId)
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .subject("Test Subject")
                    .content("Test Content")
                    .maxRetries(3)
                    .retryCount(0)
                    .build();

            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(failedNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReflectionTestUtils.setField(notificationService, "dlqTopic", "notification-events-dlq");

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            assertEquals(NotificationStatus.FAILED, failedNotification.getStatus());
            assertEquals(3, failedNotification.getRetryCount());
            verify(kafkaTemplate).send(eq("notification-events-dlq"), any());
        }

        @Test
        @DisplayName("sendNotification - should send to DLQ after 3 failed attempts")
        void sendNotification_SendToDLQAfterThreeFailures() {
            Notification failedNotification = Notification.builder()
                    .id(notificationId)
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .subject("Test Subject")
                    .content("Test Content")
                    .maxRetries(3)
                    .retryCount(2)
                    .build();

            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(failedNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReflectionTestUtils.setField(notificationService, "dlqTopic", "notification-events-dlq");

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            assertEquals(NotificationStatus.FAILED, failedNotification.getStatus());
            verify(kafkaTemplate).send(eq("notification-events-dlq"), any(NotificationRequest.class));
        }

        @Test
        @DisplayName("sendNotification - should succeed on first attempt")
        void sendNotification_SuccessOnFirstAttempt() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            NotificationResponse response = notificationService.sendNotification(notificationId);

            assertNotNull(response);
            assertEquals(NotificationStatus.SENT, testNotification.getStatus());
            verify(kafkaTemplate, never()).send(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Mark All As Read Tests")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("markAllAsRead - should mark all notifications as read")
        void markAllAsRead_Success() {
            when(notificationRepository.markAllAsRead(userId))
                    .thenReturn(10);

            int count = notificationService.markAllAsRead(userId);

            assertEquals(10, count);
            verify(notificationRepository).markAllAsRead(userId);
        }

        @Test
        @DisplayName("markAllAsRead - should return 0 when no notifications")
        void markAllAsRead_Zero() {
            when(notificationRepository.markAllAsRead(userId))
                    .thenReturn(0);

            int count = notificationService.markAllAsRead(userId);

            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("Get Unread Notifications Tests")
    class GetUnreadNotificationsTests {

        @Test
        @DisplayName("getUnreadNotifications - should return unread notifications")
        void getUnreadNotifications_Success() {
            List<Notification> unread = List.of(testNotification);
            when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(unread);

            List<NotificationResponse> result = notificationService.getUnreadNotifications(userId);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getUnreadNotifications - should return empty list")
        void getUnreadNotifications_Empty() {
            when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of());

            List<NotificationResponse> result = notificationService.getUnreadNotifications(userId);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Get Pending Notifications Tests")
    class GetPendingNotificationsTests {

        @Test
        @DisplayName("getPendingNotifications - should return pending notifications")
        void getPendingNotifications_Success() {
            List<Notification> pending = List.of(testNotification);
            when(notificationRepository.findPendingNotifications())
                    .thenReturn(pending);

            List<NotificationResponse> result = notificationService.getPendingNotifications();

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getPendingNotifications - should return empty list")
        void getPendingNotifications_Empty() {
            when(notificationRepository.findPendingNotifications())
                    .thenReturn(List.of());

            List<NotificationResponse> result = notificationService.getPendingNotifications();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Transaction Event with Receiver Tests")
    class TransactionEventReceiverTests {

        @Test
        @DisplayName("createFromTransactionEvent - should create receiver notification")
        void createFromTransactionEvent_Receiver() {
            TransactionEvent event = TransactionEvent.builder()
                    .eventId(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_COMPLETED)
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            when(notificationRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createFromTransactionEvent(event, false);

            assertNotNull(response);
            assertEquals(event.getToUserId(), response.getUserId());
            assertTrue(response.getSubject().contains("Transfer"));
        }

        @Test
        @DisplayName("createFromTransactionEvent - should create initiated notification")
        void createFromTransactionEvent_Initiated() {
            TransactionEvent event = TransactionEvent.builder()
                    .eventId(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .eventType(TransactionEvent.TransactionEventType.TRANSACTION_INITIATED)
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .currency("TRY")
                    .build();

            when(notificationRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createFromTransactionEvent(event, true);

            assertNotNull(response);
            assertTrue(response.getSubject().contains("Başlatıldı"));
        }

        @Test
        @DisplayName("createFromTransactionEvent - should create compensation notification")
        void createFromTransactionEvent_Compensation() {
            TransactionEvent event = TransactionEvent.builder()
                    .eventId(UUID.randomUUID())
                    .sagaId(UUID.randomUUID())
                    .eventType(TransactionEvent.TransactionEventType.COMPENSATION_COMPLETED)
                    .fromUserId(userId)
                    .toUserId(UUID.randomUUID())
                    .amount(new BigDecimal("75.00"))
                    .currency("TRY")
                    .build();

            when(notificationRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.setId(notificationId);
                        return n;
                    });

            NotificationResponse response = notificationService.createFromTransactionEvent(event, true);

            assertNotNull(response);
            assertTrue(response.getSubject().contains("İptal"));
        }
    }

    @Nested
    @DisplayName("Retry Pending Notifications Tests")
    class RetryPendingNotificationsTests {

        @Test
        @DisplayName("retryPendingNotifications - should retry pending notifications")
        void retryPendingNotifications_Success() {
            List<Notification> pendingRetries = List.of(testNotification);
            when(notificationRepository.findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0)))
                    .thenReturn(pendingRetries);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> {
                        Notification n = invocation.getArgument(0);
                        n.markAsSent();
                        return n;
                    });

            notificationService.retryPendingNotifications();

            verify(notificationRepository).findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0));
        }

        @Test
        @DisplayName("retryPendingNotifications - should handle empty list")
        void retryPendingNotifications_Empty() {
            when(notificationRepository.findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0)))
                    .thenReturn(List.of());

            notificationService.retryPendingNotifications();

            verify(notificationRepository).findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0));
        }
    }
}