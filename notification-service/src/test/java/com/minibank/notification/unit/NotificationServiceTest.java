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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

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
            // Arrange
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

            // Act
            NotificationResponse response = notificationService.createNotification(request);

            // Assert
            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertEquals(NotificationType.EMAIL, response.getType());
            assertEquals(NotificationStatus.PENDING, response.getStatus());
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("createNotification - should throw DuplicateNotificationException for duplicate idempotency key")
        void createNotification_DuplicateIdempotencyKey() {
            // Arrange
            String idempotencyKey = "test-key-123";
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .content("Test Content")
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(notificationRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(testNotification));

            // Act & Assert
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
            // Arrange
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            // Act
            NotificationResponse response = notificationService.sendNotification(notificationId);

            // Assert
            assertNotNull(response);
            verify(emailService).send(any(Notification.class));
        }

        @Test
        @DisplayName("sendNotification - should throw NotificationNotFoundException for invalid ID")
        void sendNotification_NotFound() {
            // Arrange
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotificationNotFoundException.class, 
                    () -> notificationService.sendNotification(notificationId));
        }

        @Test
        @DisplayName("sendNotification - should handle send failure")
        void sendNotification_SendFailure() {
            // Arrange
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(false);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            // Act
            NotificationResponse response = notificationService.sendNotification(notificationId);

            // Assert
            assertNotNull(response);
            assertEquals(NotificationStatus.FAILED, testNotification.getStatus());
        }

        @Test
        @DisplayName("sendNotification - should return early if already sent")
        void sendNotification_AlreadySent() {
            // Arrange
            testNotification.markAsSent();
            testNotification.setStatus(NotificationStatus.SENT);
            
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));

            // Act
            NotificationResponse response = notificationService.sendNotification(notificationId);

            // Assert
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
            // Arrange
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));

            // Act
            NotificationResponse response = notificationService.getNotification(notificationId);

            // Assert
            assertNotNull(response);
            assertEquals(notificationId, response.getId());
        }

        @Test
        @DisplayName("getNotification - should throw NotificationNotFoundException")
        void getNotification_NotFound() {
            // Arrange
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            // Act & Assert
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
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Notification> notifications = List.of(testNotification);
            Page<Notification> page = new PageImpl<>(notifications);

            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                    .thenReturn(page);

            // Act
            Page<NotificationResponse> response = notificationService
                    .getUserNotifications(userId, pageable);

            // Assert
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
            // Arrange
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

            // Act
            NotificationResponse response = notificationService.createFromTransactionEvent(event);

            // Assert
            assertNotNull(response);
            assertEquals(userId, response.getUserId());
            assertNotNull(response.getSubject());
            assertNotNull(response.getContent());
        }

        @Test
        @DisplayName("createFromTransactionEvent - should create notification from failed event")
        void createFromTransactionEvent_Failed() {
            // Arrange
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

            // Act
            NotificationResponse response = notificationService.createFromTransactionEvent(event);

            // Assert
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
            // Arrange
            List<Notification> pendingNotifications = List.of(testNotification);
            
            when(notificationRepository.findPendingNotifications(100))
                    .thenReturn(pendingNotifications);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(emailService.send(any(Notification.class)))
                    .thenReturn(true);
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(testNotification);

            // Act
            int processed = notificationService.processPendingNotifications();

            // Assert
            assertEquals(1, processed);
        }
    }

    @Nested
    @DisplayName("Notification Status Tests")
    class NotificationStatusTests {

        @Test
        @DisplayName("canRetry - should return true when retries remaining")
        void canRetry_True() {
            // Act & Assert
            assertTrue(testNotification.canRetry());
        }

        @Test
        @DisplayName("canRetry - should return false when max retries reached")
        void canRetry_False() {
            // Arrange
            testNotification.setRetryCount(3);

            // Act & Assert
            assertFalse(testNotification.canRetry());
        }

        @Test
        @DisplayName("markAsSent - should set sent status and timestamp")
        void markAsSent() {
            // Act
            testNotification.markAsSent();

            // Assert
            assertEquals(NotificationStatus.SENT, testNotification.getStatus());
            assertNotNull(testNotification.getSentAt());
        }

        @Test
        @DisplayName("markAsFailed - should set failed status and error message")
        void markAsFailed() {
            // Act
            testNotification.markAsFailed("Test error");

            // Assert
            assertEquals(NotificationStatus.FAILED, testNotification.getStatus());
            assertEquals("Test error", testNotification.getErrorMessage());
        }
    }
}
