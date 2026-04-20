package com.minibank.account.exception;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to access an account they do not own.
 *
 * <p>This exception is used in the authorization layer of AccountController
 * to enforce ownership checks before performing any account operation.</p>
 *
 * <p>HTTP Status: 403 FORBIDDEN
 * Error Code: ACCESS_DENIED</p>
 */
public class AccessDeniedException extends AccountServiceException {

    /** Constructor. */
    public AccessDeniedException(UUID accountId, UUID userId) {
        super(
                String.format("User %s does not have access to account %s", userId, accountId),
                org.springframework.http.HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
        );
    }
}
