# MiniBank - Troubleshooting Log

Bu dosya, MiniBank projesi geliştirme sürecinde karşılaşılan hataları ve çözümlerini içerir.

---

## Sprint 6 - User Service

### Hata 1: UserServiceException Sınıfı Bulunamadı

**Hata Mesajı:**
```
Cannot resolve symbol 'UserServiceException'
```

**Neden:**
`UserServiceException.java` (base exception sınıfı) dosyası eksikti. Diğer tüm custom exception sınıfları bu sınıftan extend ediliyor, ancak base class fiziksel olarak dosya sisteminde mevcut değildi.

**Çözüm:**
```java
// UserServiceException.java
package com.minibank.user.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UserServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public UserServiceException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
```

**Dosya:** `src/main/java/com/minibank/user/exception/UserServiceException.java`

---

### Hata 2: Spring Security - "Can't configure anyRequest after itself"

**Hata Mesajı:**
```
org.springframework.beans.factory.UnsatisfiedDependencyException: 
Error creating bean with name 'securityFilterChain'
...
Failed to instantiate [org.springframework.security.web.SecurityFilterChain]: 
Factory method 'securityFilterChain' threw exception with message: 
Can't configure anyRequest after itself
```

**Neden:**
`SecurityConfig.java` dosyasında `authorizeHttpRequests` içinde `anyRequest()` iki kez çağrılmıştı:

```java
// YANLIŞ - anyRequest() iki kez
.authorizeHttpRequests(auth -> auth
    .requestMatchers(...).permitAll()
    .anyRequest().authenticated()  // 1. anyRequest()
);
http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());  // 2. anyRequest()
```

Spring Security 6.x'te `anyRequest()` sadece bir kez kullanılabilir.

**Çözüm:**
```java
// DOĞRU - Tek bir authorizeHttpRequests çağrısı
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/v1/users/register",
        "/api/v1/users/login",
        "/api/v1/users/refresh",
        "/api/v1/users/health",
        "/actuator/**"
    ).permitAll()
    .anyRequest().permitAll()  // Tek anyRequest()
);
```

**Dosya:** `src/main/java/com/minibank/user/config/SecurityConfig.java`

**Not:** Production'da `.anyRequest().permitAll()` yerine `.anyRequest().authenticated()` kullanılmalı. JWT filter implementasyonundan sonra değiştirilecek.

---

## Öğrenilen Dersler

1. **Base Exception Sınıfı:** Custom exception hiyerarşisi oluştururken base class'ın fiziksel olarak var olduğundan emin olun.

2. **Spring Security 6.x anyRequest():** `anyRequest()` metodu sadece bir kez çağrılabilir. Birden fazla authorization kuralı tek bir `authorizeHttpRequests()` bloğu içinde tanımlanmalı.

---

*Tarih: 18 Nisan 2026*

---

## Sprint 7 - Account Service

### Hata 1: Mockito eq() Import Eksik

**Hata Mesajı:**
```
Cannot resolve method 'eq(java.math.BigDecimal)'
```

**Neden:**
`AccountServiceTest.java` dosyasında `eq()` metodu kullanılmış ancak gerekli static import eksikti.

**Çözüm:**
```java
// Eksik import eklendi
import static org.mockito.ArgumentMatchers.eq;
```

**Dosya:** `account-service/src/test/java/com/minibank/account/unit/AccountServiceTest.java`

---

### Hata 2: UnnecessaryStubbingException - Gereksiz Mock Tanımları

**Hata Mesajı:**
```
Unnecessary stubbings detected.
Following stubbings are unnecessary:
  1. -> at AccountServiceTest.java:181
  2. -> at AccountServiceTest.java:183
```

**Neden:**
`deposit_Success` ve `withdraw_Success` testlerinde `getBalanceById` ve `getAvailableBalanceById` metodları mock'lanmış ancak bu metodlar test sırasında çağrılmamış. `deposit()` ve `withdraw()` metodları sonunda `getAccountById()` çağırıyor, o da `findById()` kullanıyor.

**Çözüm:**
Gereksiz stub tanımlarını kaldırdık:
```java
// KALDIRILDI - Gereksiz stub'lar:
// when(accountRepository.getBalanceById(testAccountId)).thenReturn(...)
// when(accountRepository.getAvailableBalanceById(testAccountId)).thenReturn(...)
```

---

### Hata 3: transfer_Compensation Test - Yanlış Verify

**Hata Mesajı:**
```
Wanted 2 times:
-> at AccountServiceTest.java:277
But was 1 time:
-> at AccountService.transfer(AccountService.java:236)
```

**Neden:**
Test yanlış verify yapmış:
```java
// YANLIŞ - Aynı ID ile 2 kez çağrılmasını beklemek
verify(accountRepository, times(2)).addBalance(testAccountId, ...);
```

Aslında:
- `addBalance(toAccountId, ...)` → destination için 1 kez (başarısız)
- `addBalance(testAccountId, ...)` → compensation için 1 kez

**Çözüm:**
Her çağrıyı ayrı ayrı verify ettik:
```java
// DOĞRU
verify(accountRepository).addBalance(toAccountId, new BigDecimal("500.00"));   // Destination
verify(accountRepository).addBalance(testAccountId, new BigDecimal("500.00")); // Compensation
```

---

## Öğrenilen Dersler (Sprint 7)

1. **Mockito Static Imports:** `any()`, `eq()`, `mock()` gibi metodlar için static import gereklidir:
   ```java
   import static org.mockito.ArgumentMatchers.any;
   import static org.mockito.ArgumentMatchers.eq;
   import static org.mockito.Mockito.*;
   ```

2. **Testcontainers JDBC URL:** Test ortamında PostgreSQL için Testcontainers kullanırken JDBC URL formatı:
   ```
   jdbc:tc:postgresql:15:///account_test_db
   ```

3. **Unnecessary Stubbing:** Mockito strict mode'da gereksiz stub'lar hata verir. Sadece gerçekten çağrılacak metodları mock'layın.

4. **Verify Doğruluğu:** `times(n)` kullanırken hangi ID/parametre ile kaç kez çağrıldığını doğru hesaplayın.

---

## Test Sonuçları

### Sprint 6 - User Service Tests

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
Status: ✅ PASS
```

**Test Coverage:**
- Unit Tests: UserService (Registration, Login, Get, Update, Delete)
- Integration Tests: Full HTTP stack with Testcontainers

**Test Komutları:**
```bash
cd user-service
mvn test                    # Tüm testleri çalıştır
mvn test -Dtest=UserServiceTest  # Sadece unit testler
mvn verify                   # Integration testler dahil
mvn jacoco:report           # Coverage raporu
```

---

## Sprint 8 - Transaction Service

### Hata 1: BeanDefinitionOverrideException - jpaAuditingHandler

**Hata Mesajı:**
```
BeanDefinitionOverrideException: Invalid bean definition with name 'jpaAuditingHandler'
The bean 'jpaAuditingHandler' could not be registered. A bean with that name has already been defined
```

**Neden:**
`@EnableJpaAuditing` annotasyonu iki farklı yerde tanımlanmıştı:
- `TransactionServiceApplication.java` (doğru yer)
- `RedisConfig.java` (gereksiz)

**Çözüm:**
```java
// RedisConfig.java - @EnableJpaAuditing KALDIRILDI
@Configuration
// @EnableJpaAuditing  <- KALDIRILDI
public class RedisConfig {
    // ...
}
```

**Dosya:** `transaction-service/src/main/java/com/minibank/transaction/config/RedisConfig.java`

---

### Hata 2: Docker Build Failed - JAR Not Found

**Hata Mesajı:**
```
failed to solve: lstat /target: no such file or directory
```

**Neden:**
Transaction-service Dockerfile'ı multi-stage build kullanmıyordu. Pre-built JAR dosyası arıyordu:
```dockerfile
# YANLIŞ - Pre-built JAR gerekiyor
FROM eclipse-temurin:17-jre-alpine
COPY target/transaction-service-*.jar app.jar
```

**Çözüm:**
Multi-stage build kullanıldı:
```dockerfile
# DOĞRU - Multi-stage build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
```

**Dosya:** `transaction-service/Dockerfile`

---

## Sprint 9 - Notification Service

### Hata Yok

Sprint 9 Notification Service sorunsuz tamamlandı.

**Oluşturulan Bileşenler:**
- Notification Entity (status lifecycle ile)
- NotificationRepository (custom queries)
- NotificationService (create, send, retry)
- EmailService / SmsService (mock implementations)
- Kafka Consumer (TransactionEventConsumer)
- NotificationController (REST API)
- Unit Tests + Integration Tests
- Dockerfile + docker-compose.yml update

---

## Test Sonuçları (Güncel)

| Sprint | Service | Tests | Status |
|--------|---------|-------|--------|
| Sprint 6 | User Service | 23 tests | ✅ PASS |
| Sprint 7 | Account Service | 13 tests | ✅ PASS |
| Sprint 8 | Transaction Service | 15 tests | ✅ PASS |
| Sprint 9 | Notification Service | 15 tests | ✅ PASS |

---

## Öğrenilen Dersler (Genel)

1. **JPA Auditing:** `@EnableJpaAuditing` sadece main application class'ında tanımlanmalı. Config class'larında tekrar tanımlamak conflict'e neden olur.

2. **Docker Multi-Stage Build:** Her microservice için Dockerfile multi-stage build kullanmalı. Bu sayede:
   - Build ortamı (Maven, JDK) runtime'da yer kaplamaz
   - Pre-built JAR gereksinimi ortadan kalkar
   - Image boyutu küçülür

3. **Kafka Consumer Idempotency:** Event-driven sistemlerde duplicate processing'i önlemek için idempotency key kullanılmalı.

4. **Notification Patterns:** Transaction-based notifications için:
   - Event type'a göre farklı template'ler
   - Retry mechanism (max attempts)
   - Status tracking (PENDING → SENT → DELIVERED)

---

*Tarih: 18 Nisan 2026 - Güncellendi*
