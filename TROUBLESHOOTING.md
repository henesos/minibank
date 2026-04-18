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
