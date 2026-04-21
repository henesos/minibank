# Task: MiniBank User Service — Critical Security Vulnerability Fixes

## Agent: Senior Software Engineer
## Task ID: security-fix-001

---

## Summary of Changes

6 files modified/created to close critical security vulnerabilities in MiniBank User Service.

### Files Changed

| # | File | Action | Critical Changes |
|---|------|--------|-----------------|
| 1 | `GatewayAuthenticationFilter.java` | **NEW** | API Gateway header filter (X-User-ID) |
| 2 | `SecurityConfig.java` | MODIFIED | `permitAll()` → `authenticated()`, CORS, filter chain |
| 3 | `UserController.java` | MODIFIED | IDOR protection, `/me` endpoint, helper methods |
| 4 | `UserService.java` | MODIFIED | PENDING login block, auto-unlock, nationalId check |
| 5 | `application.yml` | MODIFIED | JWT secret from env variable |
| 6 | `GlobalExceptionHandler.java` | MODIFIED | AccessDeniedException handler |

---

## Detailed Changes

### 1. GatewayAuthenticationFilter.java (NEW)
**Path:** `config/GatewayAuthenticationFilter.java`

- Reads X-User-ID, X-User-Email, X-User-Role headers set by API Gateway
- Returns 401 if X-User-ID is missing on protected endpoints
- Sets PreAuthenticatedAuthenticationToken in SecurityContext
- Skips auth check for public endpoints (register, login, refresh, health, actuator, swagger)
- Does NOT perform JWT validation (API Gateway's responsibility)

### 2. SecurityConfig.java
**Path:** `config/SecurityConfig.java`

- CRITICAL FIX: `anyRequest().permitAll()` → `anyRequest().authenticated()`
- Public endpoints remain `permitAll()`: register, login, refresh, health, actuator, swagger
- Added GatewayAuthenticationFilter before UsernamePasswordAuthenticationFilter
- Added CORS configuration (only API Gateway origins: localhost:8080, api-gateway:8080)
- Constructor injection for GatewayAuthenticationFilter

### 3. UserController.java
**Path:** `controller/UserController.java`

- IDOR FIX: `validateUserIdMatch(id, request)` on all `/{id}` endpoints
  - GET /{id}, PUT /{id}, DELETE /{id}, POST /{id}/verify-email, POST /{id}/verify-phone
  - Returns 403 Forbidden if X-User-ID doesn't match path variable
- `/me` endpoint: Reads X-User-ID header directly instead of parsing JWT
- Removed: `@RequestHeader(value = "Authorization", required = false)` anti-pattern
- Added: `getAuthenticatedUserId(HttpServletRequest)` helper method
- Added: `validateUserIdMatch(UUID, HttpServletRequest)` helper method
- IDOR attempts are logged at WARN level with both user IDs

### 4. UserService.java
**Path:** `service/UserService.java`

- PENDING Login Block: Only ACTIVE users can login. PENDING users get 403 with ACCOUNT_NOT_ACTIVE
  - Old code: `user.getStatus() != ACTIVE && user.getStatus() != PENDING` (PENDING could login!)
  - New code: `user.getStatus() != ACTIVE` (only ACTIVE allowed)
- Auto-Unlock: If lockedUntil has expired, automatically reset lock before checking
  - Checks `lockedUntil.isBefore(LocalDateTime.now())` and calls `resetFailedLoginAttempts()`
- Duplicate nationalId Check: Register method checks if nationalId already exists
  - Returns 409 CONFLICT with NATIONAL_ID_EXISTS error code
- Case-Insensitive Email Lookup: `findByEmail()` → `findByEmailIgnoreCase()` in login

### 5. application.yml
**Path:** `resources/application.yml`

- JWT Secret: Changed from hardcoded value to env variable with dev-only default
  - Old: `${JWT_SECRET:minibank-super-secret-key-for-development-only-min-256-bits}`
  - New: `${JWT_SECRET:minibank-dev-only-secret-key-not-for-production-min-256-bits-long}`
  - Added comments explaining the default is for development only

### 6. GlobalExceptionHandler.java
**Path:** `exception/GlobalExceptionHandler.java`

- Added: `AccessDeniedException` handler returning 403 Forbidden
  - Catches Spring Security AccessDeniedException (for IDOR and authorization failures)
  - Returns consistent error response format with ACCESS_DENIED error code

---

## Security Vulnerabilities Closed

| Vulnerability | Severity | Fix |
|---|---|---|
| All endpoints accessible without auth | CRITICAL | authenticated() + Gateway filter |
| IDOR — any user can access/modify others | CRITICAL | X-User-ID vs path ID validation |
| PENDING users could login | HIGH | Status check changed to ACTIVE-only |
| Locked accounts never auto-unlock | MEDIUM | Auto-unlock on expired lock duration |
| Duplicate nationalId possible | MEDIUM | Check before registration |
| JWT secret hardcoded in config | MEDIUM | Environment variable with dev-only default |
| No CORS restriction | LOW | Only API Gateway origins allowed |
| Missing 403 exception handler | LOW | AccessDeniedException handler added |
| Case-sensitive email login | LOW | findByEmailIgnoreCase |
