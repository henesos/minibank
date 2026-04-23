package com.minibank.user.unit;

import com.minibank.user.controller.UserController;
import com.minibank.user.dto.*;
import com.minibank.user.exception.UserServiceException;
import com.minibank.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UUID testUserId;
    private String testEmail;
    private UserResponse testUserResponse;
    private AuthResponse testAuthResponse;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@minibank.com";

        testUserResponse = UserResponse.builder()
                .id(testUserId)
                .email(testEmail)
                .firstName("Test")
                .lastName("User")
                .status("ACTIVE")
                .build();

        testAuthResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .user(testUserResponse)
                .expiresIn(86400000L)
                .build();

        httpRequest = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("Register")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully")
        void register_Success() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .email(testEmail)
                    .password("Password123!")
                    .firstName("Test")
                    .lastName("User")
                    .build();

            when(userService.register(any(UserRegistrationRequest.class))).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.register(request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testEmail, response.getBody().getEmail());
            verify(userService).register(any(UserRegistrationRequest.class));
        }
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Should login user successfully")
        void login_Success() {
            UserLoginRequest request = UserLoginRequest.builder()
                    .email(testEmail)
                    .password("Password123!")
                    .build();

            when(userService.login(any(UserLoginRequest.class))).thenReturn(testAuthResponse);

            ResponseEntity<AuthResponse> response = userController.login(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("access-token", response.getBody().getAccessToken());
            verify(userService).login(any(UserLoginRequest.class));
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh token successfully")
        void refreshToken_Success() {
            UserController.RefreshTokenRequest request = new UserController.RefreshTokenRequest();
            request.setRefreshToken("refresh-token");

            when(userService.refreshToken("refresh-token")).thenReturn(testAuthResponse);

            ResponseEntity<AuthResponse> response = userController.refreshToken(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            verify(userService).refreshToken("refresh-token");
        }

        @Test
        @DisplayName("Should throw exception for invalid refresh token")
        void refreshToken_InvalidToken_ThrowsException() {
            UserController.RefreshTokenRequest request = new UserController.RefreshTokenRequest();
            request.setRefreshToken("invalid-token");

            when(userService.refreshToken("invalid-token"))
                    .thenThrow(new UserServiceException("Invalid token", HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));

            assertThrows(UserServiceException.class, () -> userController.refreshToken(request));
        }
    }

    @Nested
    @DisplayName("Get User By ID")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should get user by ID successfully")
        void getUserById_Success() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            when(userService.getUserById(testUserId)).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.getUserById(testUserId, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            verify(userService).getUserById(testUserId);
        }

        @Test
        @DisplayName("Should throw exception when X-User-ID header is missing")
        void getUserById_MissingHeader_ThrowsException() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(null);

            assertThrows(UserServiceException.class, () ->
                    userController.getUserById(testUserId, httpRequest));
        }

        @Test
        @DisplayName("Should throw exception when X-User-ID header is invalid")
        void getUserById_InvalidHeader_ThrowsException() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn("invalid-uuid");

            assertThrows(UserServiceException.class, () ->
                    userController.getUserById(testUserId, httpRequest));
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void getUserById_IDORMismatch_ThrowsException() {
            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.getUserById(differentId, httpRequest));
        }
    }

    @Nested
    @DisplayName("Get Current User")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should get current user successfully")
        void getCurrentUser_Success() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            when(userService.getUserById(testUserId)).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.getCurrentUser(httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            verify(userService).getUserById(testUserId);
        }

        @Test
        @DisplayName("Should throw exception when X-User-ID header is missing")
        void getCurrentUser_MissingHeader_ThrowsException() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(null);

            assertThrows(UserServiceException.class, () ->
                    userController.getCurrentUser(httpRequest));
        }
    }

    @Nested
    @DisplayName("Update Profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update profile successfully")
        void updateProfile_Success() {
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .firstName("Updated")
                    .lastName("Name")
                    .build();

            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            when(userService.updateProfile(testUserId, request)).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.updateProfile(testUserId, request, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).updateProfile(testUserId, request);
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void updateProfile_IDORMismatch_ThrowsException() {
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .firstName("Updated")
                    .build();

            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.updateProfile(differentId, request, httpRequest));
        }
    }

    @Nested
    @DisplayName("Delete Account")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should delete account successfully")
        void deleteAccount_Success() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            doNothing().when(userService).deleteAccount(testUserId);

            ResponseEntity<Void> response = userController.deleteAccount(testUserId, httpRequest);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(userService).deleteAccount(testUserId);
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void deleteAccount_IDORMismatch_ThrowsException() {
            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.deleteAccount(differentId, httpRequest));
        }
    }

    @Nested
    @DisplayName("Verify Email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully")
        void verifyEmail_Success() {
            UserController.VerifyCodeRequest request = new UserController.VerifyCodeRequest();
            request.setCode("123456");

            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            when(userService.verifyEmail(testUserId, "123456")).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.verifyEmail(testUserId, request, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).verifyEmail(testUserId, "123456");
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void verifyEmail_IDORMismatch_ThrowsException() {
            UserController.VerifyCodeRequest request = new UserController.VerifyCodeRequest();
            request.setCode("123456");

            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.verifyEmail(differentId, request, httpRequest));
        }
    }

    @Nested
    @DisplayName("Verify Phone")
    class VerifyPhoneTests {

        @Test
        @DisplayName("Should verify phone successfully")
        void verifyPhone_Success() {
            UserController.VerifyCodeRequest request = new UserController.VerifyCodeRequest();
            request.setCode("123456");

            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            when(userService.verifyPhone(testUserId, "123456")).thenReturn(testUserResponse);

            ResponseEntity<UserResponse> response = userController.verifyPhone(testUserId, request, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).verifyPhone(testUserId, "123456");
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void verifyPhone_IDORMismatch_ThrowsException() {
            UserController.VerifyCodeRequest request = new UserController.VerifyCodeRequest();
            request.setCode("123456");

            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.verifyPhone(differentId, request, httpRequest));
        }
    }

    @Nested
    @DisplayName("Request Email Verification")
    class RequestEmailVerificationTests {

        @Test
        @DisplayName("Should request email verification successfully")
        void requestEmailVerification_Success() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            doNothing().when(userService).requestEmailVerification(testUserId);

            ResponseEntity<Void> response = userController.requestEmailVerification(testUserId, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).requestEmailVerification(testUserId);
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void requestEmailVerification_IDORMismatch_ThrowsException() {
            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.requestEmailVerification(differentId, httpRequest));
        }
    }

    @Nested
    @DisplayName("Request Phone Verification")
    class RequestPhoneVerificationTests {

        @Test
        @DisplayName("Should request phone verification successfully")
        void requestPhoneVerification_Success() {
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());
            doNothing().when(userService).requestPhoneVerification(testUserId);

            ResponseEntity<Void> response = userController.requestPhoneVerification(testUserId, httpRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).requestPhoneVerification(testUserId);
        }

        @Test
        @DisplayName("Should throw exception when IDOR attempt detected")
        void requestPhoneVerification_IDORMismatch_ThrowsException() {
            UUID differentId = UUID.randomUUID();
            when(httpRequest.getHeader("X-User-ID")).thenReturn(testUserId.toString());

            assertThrows(UserServiceException.class, () ->
                    userController.requestPhoneVerification(differentId, httpRequest));
        }
    }

    @Nested
    @DisplayName("Health")
    class HealthTests {

        @Test
        @DisplayName("Should return health status")
        void health_Success() {
            ResponseEntity<UserController.HealthResponse> response = userController.health();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("UP", response.getBody().getStatus());
            assertEquals("user-service", response.getBody().getService());
        }
    }
}