package com.minibank.user.unit;

import com.minibank.user.controller.UserController;
import com.minibank.user.dto.UserLoginRequest;
import com.minibank.user.dto.UserRegistrationRequest;
import com.minibank.user.dto.UserResponse;
import com.minibank.user.dto.UserUpdateRequest;
import com.minibank.user.exception.*;
import com.minibank.user.filter.JwtAuthenticationFilter;
import com.minibank.user.service.JwtService;
import com.minibank.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("Handle UserServiceException")
    class HandleUserServiceExceptionTests {

        @Test
        @DisplayName("Should handle UserServiceException")
        void handleUserServiceException_Success() {
            UserServiceException ex = new UserServiceException(
                    "Test error",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "TEST_ERROR"
            );

            var response = exceptionHandler.handleUserServiceException(ex);

            assertNotNull(response);
            assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("TEST_ERROR", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("Handle AccessDeniedException")
    class HandleAccessDeniedExceptionTests {

        @Test
        @DisplayName("Should handle AccessDeniedException")
        void handleAccessDeniedException_Success() {
            AccessDeniedException ex = new AccessDeniedException("Access denied");

            var response = exceptionHandler.handleAccessDeniedException(ex);

            assertNotNull(response);
            assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("ACCESS_DENIED", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("Handle BadCredentialsException")
    class HandleBadCredentialsExceptionTests {

        @Test
        @DisplayName("Should handle BadCredentialsException")
        void handleBadCredentialsException_Success() {
            BadCredentialsException ex = new BadCredentialsException("Bad credentials");

            var response = exceptionHandler.handleBadCredentialsException(ex);

            assertNotNull(response);
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("INVALID_CREDENTIALS", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("Handle Generic Exception")
    class HandleGenericExceptionTests {

        @Test
        @DisplayName("Should handle generic exception")
        void handleGenericException_Success() {
            Exception ex = new RuntimeException("Unexpected error");

            var response = exceptionHandler.handleGenericException(ex);

            assertNotNull(response);
            assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("ErrorResponse Structure")
    class ErrorResponseTests {

        @Test
        @DisplayName("Should create error response with all fields")
        void errorResponse_AllFields_Success() {
            var response = exceptionHandler.handleUserServiceException(
                    new UserServiceException("Test", org.springframework.http.HttpStatus.BAD_REQUEST, "CODE")
            );

            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
            assertNotNull(response.getBody().getMessage());
        }
    }
}