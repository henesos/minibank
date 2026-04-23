package com.minibank.notification.unit;

import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.entity.Notification;
import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalCoverageTests {

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
    @DisplayName("Get Notifications Tests")
    class GetAllNotificationsTests {

        @Test
        @DisplayName("getPendingNotifications - should return pending notifications")
        void getPendingNotifications_ReturnsList() {
            List<Notification> pending = List.of(testNotification);
            when(notificationRepository.findPendingNotifications())
                    .thenReturn(pending);

            List<NotificationResponse> result = notificationService.getPendingNotifications();

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getUnreadNotifications - should return unread notifications")
        void getUnreadNotifications_ReturnsList() {
            List<Notification> unread = List.of(testNotification);
            when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(unread);

            List<NotificationResponse> result = notificationService.getUnreadNotifications(userId);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("markAllAsRead - should return updated count")
        void markAllAsRead_ReturnsCount() {
            when(notificationRepository.markAllAsRead(userId))
                    .thenReturn(10);

            int count = notificationService.markAllAsRead(userId);

            assertEquals(10, count);
            verify(notificationRepository).markAllAsRead(userId);
        }
    }

    @Nested
    @DisplayName("Retry Notification Tests")
    class RetryNotificationTests {

        @Test
        @DisplayName("retryPendingNotifications - should process pending with retry")
        void retryPendingNotifications_ProcessesPending() {
            testNotification.setRetryCount(1);
            testNotification.setStatus(NotificationStatus.PENDING);
            
            List<Notification> pendingRetries = List.of(testNotification);
            when(notificationRepository.findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0)))
                    .thenReturn(pendingRetries);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            notificationService.retryPendingNotifications();

            verify(notificationRepository).findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0));
        }

        @Test
        @DisplayName("retryPendingNotifications - should handle exception during retry")
        void retryPendingNotifications_HandlesException() {
            testNotification.setRetryCount(1);
            testNotification.setStatus(NotificationStatus.PENDING);
            
            List<Notification> pendingRetries = List.of(testNotification);
            when(notificationRepository.findByStatusAndRetryCountGreaterThan(
                    eq(NotificationStatus.PENDING), eq(0)))
                    .thenReturn(pendingRetries);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new RuntimeException("Error"));

            assertDoesNotThrow(() -> notificationService.retryPendingNotifications());
        }
    }

    @Nested
    @DisplayName("Process Pending Tests")
    class ProcessPendingTests {

        @Test
        @DisplayName("processPendingNotifications - should process all pending")
        void processPendingNotifications_ProcessesAll() {
            List<Notification> pending = List.of(testNotification);
            when(notificationRepository.findPendingNotifications(100))
                    .thenReturn(pending);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            int processed = notificationService.processPendingNotifications();

            assertEquals(1, processed);
        }

        @Test
        @DisplayName("processPendingNotifications - should handle exception for each")
        void processPendingNotifications_HandlesException() {
            List<Notification> pending = List.of(testNotification);
            when(notificationRepository.findPendingNotifications(100))
                    .thenReturn(pending);
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new RuntimeException("Error"));

            int processed = notificationService.processPendingNotifications();

            assertEquals(0, processed);
        }
    }

    @Nested
    @DisplayName("Notification Not Found Tests")
    class NotificationNotFoundTests {

        @Test
        @DisplayName("deleteNotification - should throw for not found")
        void deleteNotification_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.deleteNotification(notificationId));
        }

        @Test
        @DisplayName("markAsRead - should throw for not found")
        void markAsRead_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.markAsRead(notificationId));
        }

        @Test
        @DisplayName("getNotification - should throw for not found")
        void getNotification_NotFound() {
            when(notificationRepository.findById(notificationId))
                    .thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.getNotification(notificationId));
        }
    }
}