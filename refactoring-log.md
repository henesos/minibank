# S9 Refactoring Log — SonarLint Refactoring Sprint

> Proje: MiniBank Digital Wallet System
> Sprint: S9 — SonarLint Refactoring
> Tarih: 2026-04-21
> Toplam Düzeltme: 20 kayıt

---

## Refactoring Kayıtları

### Security Hotspot Düzeltmeleri (6 adet)

| # | Dosya | Kural ID | Tip | Önceki | Sonraki |
|---|-------|----------|-----|--------|---------|
| 1 | `account-service/.../config/KafkaConfig.java` | S2755 | Security | `TRUSTED_PACKAGES = "*"` (arbitrary class deserialization) | `TRUSTED_PACKAGES = "com.minibank.account.dto,java.util,java.lang"` |
| 2 | `transaction-service/.../config/KafkaConfig.java` | S2755 | Security | `TRUSTED_PACKAGES = "*"` (arbitrary class deserialization) | `TRUSTED_PACKAGES = "com.minibank.transaction.saga,java.util,java.lang"` |
| 3 | `transaction-service/.../saga/SagaOrchestrator.java` | S112 | Security | `throw new RuntimeException("Failed to serialize saga event", e)` | `throw new TransactionServiceException("Failed to serialize saga event", INTERNAL_SERVER_ERROR, "SAGA_SERIALIZATION_FAILED", e)` |
| 4 | `transaction-service/.../config/OutboxPublisher.java` | S112 | Security | `throw new RuntimeException("Failed to publish event to Kafka", e)` | `throw new TransactionServiceException("Failed to publish event to Kafka", INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILED", e)` |
| 5 | `api-gateway/.../filter/JwtAuthenticationFilter.java` | S3329 | Security | `String.format("{...}", message, path)` — JSON injection riski | `message` ve `path` değerleri sanitization (escape backslash, quote, newline) yapıldıktan sonra format'a ekleniyor |
| 6 | `transaction-service/.../exception/GlobalExceptionHandler.java` | S2139 | Security | `handleGenericException` metodunda exception loglanmıyor — stack trace kayboluyor | `log.error("Unexpected error: ", ex)` eklendi, stack trace loglanıyor |

---

### Code Smell Düzeltmeleri (10 adet)

| # | Dosya | Kural ID | Tip | Önceki | Sonraki |
|---|-------|----------|-----|--------|---------|
| 7 | `transaction-service/.../controller/TransactionController.java` | S1192 | Smell | `request.getHeader("X-User-ID")` inline string literal | `private static final String X_USER_ID_HEADER = "X-User-ID"` constant'ına çıkarıldı |
| 8 | `transaction-service/.../service/TransactionService.java` | S1192 | Smell | `request.getCurrency() != null ? request.getCurrency() : "TRY"` — hardcoded currency | `private static final String DEFAULT_CURRENCY = "TRY"` constant'ına çıkarıldı |
| 9 | `account-service/.../kafka/SagaCommandConsumer.java` | S1192 | Smell | `event.put("currency", "TRY")` — hardcoded currency | `private static final String DEFAULT_CURRENCY = "TRY"` constant'ına çıkarıldı |
| 10 | `user-service/.../repository/UserRepository.java` | S1128 | Smell | `java.util.List<User> findLockedAccountsToUnlock(...)` — FQN | `import java.util.List;` eklendi, `List<User>` olarak değiştirildi |
| 11 | `user-service/.../repository/UserRepository.java` | S1128 | Smell | `java.util.List<User> findDormantAccounts(...)` — FQN | `List<User>` olarak değiştirildi (import ile) |
| 12 | `transaction-service/.../repository/TransactionRepository.java` | S1128 | Smell | `java.math.BigDecimal getDailyTransferTotal(...)` — FQN | `import java.math.BigDecimal;` eklendi, `BigDecimal` olarak değiştirildi |
| 13 | `transaction-service/.../service/TransactionService.java` | S1128 | Smell | `new java.util.ArrayList<>(...)` — FQN | `import java.util.ArrayList;` eklendi, `new ArrayList<>(...)` olarak değiştirildi |
| 14 | `user-service/.../config/RedisConfig.java` | S1128 | Smell | `java.util.Map<...> cacheConfigurations = new java.util.HashMap<>()` — FQN | `import java.util.HashMap; import java.util.Map;` eklendi |
| 15 | `notification-service/.../exception/DuplicateNotificationException.java` | S1128 | Smell | `java.util.UUID existingId` — FQN | `import java.util.UUID;` eklendi |
| 16 | `notification-service/.../exception/NotificationNotFoundException.java` | S1128 | Smell | `java.util.UUID notificationId` — FQN | `import java.util.UUID;` eklendi |

---

### Bug Pattern Düzeltmeleri (3 adet)

| # | Dosya | Kural ID | Tip | Önceki | Sonraki |
|---|-------|----------|-----|--------|---------|
| 17 | `api-gateway/.../filter/JwtUtil.java` | S2225 | Bug | `public Boolean isTokenExpired(String token)` — boxed Boolean, NPE riski | `public boolean isTokenExpired(String token)` — primitive boolean |
| 18 | `api-gateway/.../filter/JwtUtil.java` | S2225 | Bug | `public Boolean validateToken(String token)` — boxed Boolean, NPE riski | `public boolean validateToken(String token)` — primitive boolean |
| 19 | `api-gateway/.../filter/AuthenticationFilter.java` | S3 | Bug | `@Autowired` field injection — test edilebilirlik ve immutability sorunu | Constructor injection via `@RequiredArgsConstructor` |
| 20 | `account-service/.../exception/InactiveAccountException.java` | S1144 | Bug | `public InactiveAccountException(UUID accountId)` — unused constructor (hiçbir yerde çağrılmıyor) | Kullanılmayan constructor kaldırıldı |

---

### Test Eklentileri (1 adet)

| # | Dosya | Tip | Açıklama |
|---|-------|-----|----------|
| 21 | `transaction-service/.../unit/SagaOrchestratorTest.java` | Test | `@Nested class HandleCreditSuccessTests` eklendi — 3 yeni test: `handleCreditSuccess_CompletesSaga` (happy path), `handleCreditSuccess_TransactionNotFound` (error path), `handleCreditSuccess_SerializationFails` (exception path) |

---

### Not: False Positive (Değiştirilmedi)

| Durum | Neden |
|-------|-------|
| `GlobalExceptionHandler`'daki `catch(Exception.class)` | Kasıtlı olarak tüm exception'ları yakalamak için tasarlanmıştır — Spring'in son safety net'i |
| Kafka consumer `catch(Exception e)` blokları | Message processing safety net — DLQ implementation sonrası daraltılacak |
| Lombok `@Data` entity'lerdeki equals/hashCode | Spring Data JPA proxy mekanizması gereği mevcut kalıyor |

---

*Toplam: 20 refactoring kaydı + 1 test eklentisi*
