package com.minibank.notification.unit;

import com.minibank.notification.controller.NotificationController;
import com.minibank.notification.dto.NotificationRequest;
import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.entity.Notification;
import com.minibank.notification.entity.Notification.NotificationStatus;
import com.minibank.notification.entity.Notification.NotificationType;
import com.minibank.notification.service.NotificationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private UUID userId;
    private UUID notificationId;
    private NotificationResponse testResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        testResponse = NotificationResponse.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("Test Subject")
                .content("Test Content")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", userId.toString());
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @DisplayName("Get Notification Tests")
    class GetNotificationTests {

        @Test
        @DisplayName("getNotification - should return notification when owned by user")
        void getNotification_Success() {
            when(notificationService.getNotification(notificationId))
                    .thenReturn(testResponse);

            ResponseEntity<NotificationResponse> response = notificationController.getNotification(notificationId);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(notificationService).getNotification(notificationId);
        }

        @Test
        @DisplayName("getNotification - should throw 403 when not owner")
        void getNotification_Forbidden() {
            when(notificationService.getNotification(notificationId))
                    .thenReturn(testResponse);

            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.getNotification(notificationId));
        }
    }

    @Nested
    @DisplayName("Get User Notifications Tests")
    class GetUserNotificationsTests {

        @Test
        @DisplayName("getUserNotifications - should return notifications when user owns")
        void getUserNotifications_Success() {
            Page<NotificationResponse> page = new PageImpl<>(List.of(testResponse));
            when(notificationService.getUserNotifications(eq(userId), any()))
                    .thenReturn(page);

            ResponseEntity<Page<NotificationResponse>> response = notificationController
                    .getUserNotifications(userId, null);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("getUserNotifications - should throw 403 when not owner")
        void getUserNotifications_Forbidden() {
            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.getUserNotifications(userId, null));
        }
    }

    @Nested
    @DisplayName("Get Pending Notifications Tests")
    class GetPendingNotificationsTests {

        @Test
        @DisplayName("getPendingNotifications - should return pending notifications for admin")
        void getPendingNotifications_Success() {
            when(notificationService.getPendingNotifications())
                    .thenReturn(List.of(testResponse));

            ResponseEntity<List<NotificationResponse>> response = notificationController.getPendingNotifications();

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Process Pending Notifications Tests")
    class ProcessPendingNotificationsTests {

        @Test
        @DisplayName("processPendingNotifications - should process pending for admin")
        void processPendingNotifications_Success() {
            when(notificationService.processPendingNotifications())
                    .thenReturn(5);

            ResponseEntity<Integer> response = notificationController.processPendingNotifications();

            assertNotNull(response);
            assertEquals(5, response.getBody());
        }
    }

    @Nested
    @DisplayName("Send Notification Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("sendNotification - should send notification for admin")
        void sendNotification_Success() {
            when(notificationService.sendNotification(notificationId))
                    .thenReturn(testResponse);

            ResponseEntity<NotificationResponse> response = notificationController.sendNotification(notificationId);

            assertNotNull(response);
            verify(notificationService).sendNotification(notificationId);
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("health - should return healthy status")
        void health_Success() {
            ResponseEntity<String> response = notificationController.health();

            assertNotNull(response);
            assertEquals("Notification Service is healthy", response.getBody());
        }
    }

    @Nested
    @DisplayName("Get Unread Count Tests")
    class GetUnreadCountTests {

        @Test
        @DisplayName("getUnreadCount - should return count when user owns")
        void getUnreadCount_Success() {
            when(notificationService.getUnreadCount(userId))
                    .thenReturn(10L);

            ResponseEntity<Long> response = notificationController.getUnreadCount(userId);

            assertNotNull(response);
            assertEquals(10L, response.getBody());
        }

        @Test
        @DisplayName("getUnreadCount - should throw 403 when not owner")
        void getUnreadCount_Forbidden() {
            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.getUnreadCount(userId));
        }
    }

    @Nested
    @DisplayName("Get Unread Notifications Tests")
    class GetUnreadNotificationsTests {

        @Test
        @DisplayName("getUnreadNotifications - should return unread when user owns")
        void getUnreadNotifications_Success() {
            when(notificationService.getUnreadNotifications(userId))
                    .thenReturn(List.of(testResponse));

            ResponseEntity<List<NotificationResponse>> response = notificationController.getUnreadNotifications(userId);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("getUnreadNotifications - should throw 403 when not owner")
        void getUnreadNotifications_Forbidden() {
            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.getUnreadNotifications(userId));
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("markAsRead - should mark as read when owned")
        void markAsRead_Success() {
            when(notificationService.markAsRead(notificationId))
                    .thenReturn(testResponse);

            ResponseEntity<NotificationResponse> response = notificationController.markAsRead(notificationId);

            assertNotNull(response);
            verify(notificationService).markAsRead(notificationId);
        }

        @Test
        @DisplayName("markAsRead - should throw exception when not authenticated")
        void markAsRead_Unauthenticated() {
            RequestContextHolder.resetRequestAttributes();

            when(notificationService.markAsRead(notificationId))
                    .thenReturn(testResponse);

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.markAsRead(notificationId));
        }
    }

    @Nested
    @DisplayName("Mark All As Read Tests")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("markAllAsRead - should mark all when user owns")
        void markAllAsRead_Success() {
            when(notificationService.markAllAsRead(userId))
                    .thenReturn(5);

            ResponseEntity<Integer> response = notificationController.markAllAsRead(userId);

            assertNotNull(response);
            assertEquals(5, response.getBody());
        }

        @Test
        @DisplayName("markAllAsRead - should throw 403 when not owner")
        void markAllAsRead_Forbidden() {
            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.markAllAsRead(userId));
        }
    }

    @Nested
    @DisplayName("Delete Notification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("deleteNotification - should delete when owned")
        void deleteNotification_Success() {
            when(notificationService.getNotification(notificationId))
                    .thenReturn(testResponse);

            ResponseEntity<Void> response = notificationController.deleteNotification(notificationId);

            assertNotNull(response);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(notificationService).deleteNotification(notificationId);
        }

        @Test
        @DisplayName("deleteNotification - should throw 403 when not owner")
        void deleteNotification_Forbidden() {
            when(notificationService.getNotification(notificationId))
                    .thenReturn(testResponse);

            UUID differentUserId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-ID", differentUserId.toString());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.deleteNotification(notificationId));
        }
    }

    @Nested
    @DisplayName("Create Notification Tests")
    class CreateNotificationTests {

        @Test
        @DisplayName("createNotification - should create for internal request")
        void createNotification_InternalSuccess() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Test")
                    .content("Test content")
                    .build();

            when(notificationService.createNotification(any()))
                    .thenReturn(testResponse);

            ResponseEntity<NotificationResponse> response = notificationController.createNotification(request);

            assertNotNull(response);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }

        @Test
        @DisplayName("createNotification - should throw 403 for non-internal request")
        void createNotification_Forbidden() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Test")
                    .content("Test content")
                    .build();

            MockHttpServletRequest httpRequest = new MockHttpServletRequest();
            httpRequest.addHeader("X-User-ID", userId.toString());
            httpRequest.addHeader("X-User-Role", "USER");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

            assertThrows(ResponseStatusException.class, () ->
                    notificationController.createNotification(request));
        }
    }
}