# Sprint 7 — Güvenlik & Veri Tutarlılık Tasarım Dokümanı

> **Proje:** MiniBank — Digital Wallet System
> **Sprint:** 7 — Security & Hardening
> **Tarih:** 2026-04-22
> **Rol:** Yazılım Mimarı
> **Durum:** Tasarım Aşaması — Dev implementasyonu bekliyor

---

## İçindekiler

1. [Genel Bakış ve Tehdit Modeli](#1-genel-bakis-ve-tehdit-modeli)
2. [Grup A — Authentication & Gateway Tasarımları](#2-grup-a--authentication--gateway-tasarimlari)
   - [ADR-008: User Service SecurityConfig Düzeltmesi (C1)](#adr-008)
   - [ADR-009: JWT Secret Environment Variable Taşınması (C4)](#adr-009)
   - [ADR-010: Tek JWT Filter Mimarisi (H1)](#adr-010)
   - [ADR-011: Inter-Service Authentication (H7)](#adr-011)
3. [Grup B — Authorization & Veri Tutarlılık Tasarımları](#3-grup-b--authorization--veri-tutarlilik-tasarimlari)
   - [ADR-012: Account Service Authorization / IDOR Koruması (C2)](#adr-012)
   - [ADR-013: Redis-Based Idempotency (C5)](#adr-013)
   - [ADR-014: SagaCommandConsumer @Transactional Ekleme (H4)](#adr-014)
   - [ADR-015: parseAmount() String-Based BigDecimal Dönüşümü (H8)](#adr-015)
4. [Uygulama Yol Haritası (Implementation Roadmap)](#4-uygulama-yol-haritasi)
5. [Test Stratejisi](#5-test-stratejisi)

---

## 1. Genel Bakış ve Tehdit Modeli

### 1.1 Çözülecek Sorunlar Özeti

| ID  | Grup | Sorun | CVSS | Öncelik |
|-----|------|-------|------|---------|
| C1  | A    | `anyRequest().permitAll()` — tüm endpointler açık | 9.8 | Kritik |
| C4  | A    | JWT secret hardcoded | 7.5 | Kritik |
| H1  | A    | İki JWT filter, karışıklık | 5.3 | Yüksek |
| H7  | A    | Inter-service auth yok | 8.6 | Yüksek |
| C2  | B    | Authorization yok — IDOR | 9.1 | Kritik |
| C5  | B    | ConcurrentHashMap idempotency | 7.5 | Kritik |
| H4  | B    | @Transactional eksik | 8.2 | Yüksek |
| H8  | B    | Double→BigDecimal precision kaybı | 6.5 | Yüksek |

### 1.2 Saldırı Ağacı (Attack Tree)

```
MiniBank Saldırı Yüzeyi
│
├─ [C1] Gateway bypass edilmeden, user-service doğrudan erişilebilir
│   └─ Tüm kullanıcı endpointleri açık: profil okuma, güncelleme, silme
│   └─ Herhangi biri herhangi bir kullanıcıyı silebilir
│
├─ [C4] JWT secret biliniyorsa
│   └─ Herhangi bir kullanıcı token'ı伪造 edilebilir
│   └─ Admin yetkisi ile tüm servislere erişim
│
├─ [H7] Servisler arası doğrudan erişim (gateway bypass)
│   └─ account-service:8082 doğrudan erişilir → C2 ile birleşerek IDOR
│   └─ transaction-service:8083 → bakiye transferi başlatma
│   └─ notification-service:8084 → bildirim okuma/silme
│
├─ [C2] IDOR — authorization yok
│   └─ GET /api/v1/accounts/{id} → herhangi bir hesabı görme
│   └─ POST /api/v1/accounts/{id}/withdraw → para çekme
│   └─ DELETE /api/v1/accounts/{id} → hesap kapatma
│
├─ [C5] Notification idempotency kırılgan
│   └─ Service restart → duplicate bildirimler
│   └─ Multi-instance deploy → her instance farklı memory
│
├─ [H4] Saga consumer'da @Transactional yok
│   └─ Bakiye düşüldü ama DB commit olmadan hata → mesaj yeniden işlenir → DOUBLE DEBIT
│   └─ Bakiye artırıldı ama response publish olmadan hata → SAGA STATE INCONSISTENCY
│
└─ [H8] Double→BigDecimal precision kaybı
    └─ Kafka mesajında amount=10.33 → Double 10.3299999... → BigDecimal 10.3299999
    └─ Milyonlarca işlemde mikrosal差异 → denetim uyumsuzluğu
```

### 1.3 Mevcut Güvenlik Katmanı Analizi

```
GÜNCEL DURUM:
┌──────────┐     ┌──────────────┐     ┌─────────────────┐
│ Frontend │────>│ API Gateway  │────>│ Downstream Svc  │
│ (:80)    │     │ (:8080)      │     │                 │
│          │     │              │     │ user    (:8081) │
│ React    │     │ ✅ JWT auth  │     │ account (:8082) │
│ SPA      │     │ ❌ H1: 2 fil │     │ txn     (:8083) │
│          │     │ ❌ H7: no    │     │ notif   (:8084) │
│          │     │   svc auth   │     │                 │
└──────────┘     └──────────────┘     │ ❌ C2: no authz │
                                       │ ❌ H7: open     │
                                       │ ❌ H4: no tx    │
                                       │ ❌ H8: precision│
                                       └─────────────────┘

HEDEF DURUM:
┌──────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Frontend │────>│ API Gateway      │────>│ Downstream Svc   │
│ (:80)    │     │ (:8080)          │     │                  │
│          │     │ ✅ JWT auth      │     │ user    (:8081)  │
│ React    │     │ ✅ Tek filter    │     │ account (:8082)  │
│ SPA      │     │ ✅ Env secret    │     │ txn     (:8083)  │
│          │     │ ✅ Rate limit    │     │ notif   (:8084)  │
│          │     │ ✅ X-Internal    │     │                  │
└──────────┘     └──────────────────┘     │ ✅ Header valid  │
                                          │ ✅ Resource authz│
                                          │ ✅ @Transactional│
                                          │ ✅ String→BigDec │
                                          └──────────────────┘
```

---

## 2. Grup A — Authentication & Gateway Tasarımları

---

### ADR-008: User Service SecurityConfig Düzeltmesi

**Durum:** Kabul Edildi
**Sorun ID:** C1
**CVSS:** 9.8 (Kritik)

#### Context

`user-service`'de `SecurityConfig.java:56` satırında `anyRequest().permitAll()` tanımlı. Bu durum, `@EnableWebSecurity` ve `@EnableMethodSecurity` anotasyonları var olmasına rağmen, Spring Security'in **tüm endpointlere erişime izin vermesi** anlamına geliyor. Kodda `/register`, `/login`, `/refresh`, `/health` için `permitAll()` zaten tanımlı, ancak sonrasındaki `anyRequest().permitAll()` kalan endpointleri de açıyor.

Mevcut kod:
```java
// SecurityConfig.java:39-57
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/v1/users/register",
        "/api/v1/users/login",
        "/api/v1/users/refresh",
        "/api/v1/users/health",
        "/actuator/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/v3/api-docs.yaml"
    ).permitAll()
    // TODO: Change to .authenticated() after implementing JWT filter
    .anyRequest().permitAll()  // <-- KRITIK SORUN
);
```

#### Tehdit Modeli

| Tehdit | Vektör | Etki | Olasılık |
|--------|--------|------|----------|
| Kimlik doğrulamasız profil okuma | `GET /api/v1/users/{id}` (herhangi bir id) | Kullanıcı bilgileri sızdırma (PII) | Kesin |
| Kimlik doğrulamasız profil güncelleme | `PUT /api/v1/users/{id}` | Başka kullanıcının profilini değiştirme | Kesin |
| Kimlik doğrulamasız hesap silme | `DELETE /api/v1/users/{id}` | Başka kullanıcının hesabını silme (GDPR ihlali) | Kesin |
| Toplu kullanıcı tarama | Brute-force UUID ile `GET /api/v1/users/{uuid}` | Tüm kullanıcı verilerini çıkarma | Yüksek |

#### Decision

`anyRequest().permitAll()` → `anyRequest().authenticated()` olarak değiştirilecek. Gateway'den gelen `X-User-ID` header'ı kullanılarak, token doğrulama Gateway'de yapıldığı için user-service'de tekrar JWT parse **yapılmayacak**. Bunun yerine, request context'e güvenilecek.

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Tüm endpointler koruma altına alınır | Gateway bypass edildiğinde user-service hala korumasız (H7 ile çözülecek) |
| `@EnableMethodSecurity` aktif hale gelir | Doğrudan erişim senaryosunda ek katman gerekli |
| Authorization annotation'ları (ör: `@PreAuthorize`) çalışır | Test profili ayarları güncellenmeli |

#### Uygulama Adımları

**Dosya:** `user-service/src/main/java/com/minibank/user/config/SecurityConfig.java`

**Adım 1:** `anyRequest().permitAll()` → `anyRequest().authenticated()` değiştir

```java
// SATIR 54-56 DEĞİŞTİR:
// ESKİ:
.anyRequest().permitAll()

// YENİ:
.anyRequest().authenticated()
```

**Adım 2:** CSRF ve CORS mevcut durumunu koru (CSRF disabled doğru, JWT tabanlı)

**Adım 3:** `UserController.java`'da `@RequestHeader` ile `X-User-ID` header'ını okuyan bir utility metodu ekle. GET/PUT/DELETE endpoint'lerinde path variable `{id}` ile `X-User-ID` header'ını karşılaştır:

```java
// UserController.java'ya eklenecek private metod:
private UUID validateUserAccess(UUID pathUserId, HttpServletRequest request) {
    String userIdHeader = request.getHeader("X-User-ID");
    if (userIdHeader == null) {
        throw new UserServiceException("Unauthorized", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
    UUID authenticatedUserId = UUID.fromString(userIdHeader);
    if (!authenticatedUserId.equals(pathUserId)) {
        throw new UserServiceException("Access denied", HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
    return authenticatedUserId;
}
```

**Adım 4:** Şu endpoint'lere authorization ekle:
- `GET /{id}` → Kendi profilini görebilir (veya admin)
- `PUT /{id}` → Kendi profilini güncelleyebilir
- `DELETE /{id}` → Kendi hesabını silebilir
- `POST /{id}/verify-email` → Kendi email'ini doğrulayabilir
- `POST /{id}/verify-phone` → Kendi telefonunu doğrulayabilir
- `GET /me` → Zaten token'dan geliyor, sorun yok

**Adım 5:** Test profilinde (`application-test.yml`) security'i devre dışı bırak veya test profile özel SecurityConfig oluştur:

```java
// test profile için:
@TestConfiguration
@Profile("test")
static class TestSecurityConfig {
    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

---

### ADR-009: JWT Secret Environment Variable Taşınması

**Durum:** Kabul Edildi
**Sorun ID:** C4
**CVSS:** 7.5 (Kritik)

#### Context

JWT secret şu anda iki yerde tanımlı:

1. **api-gateway `application.yml` (default profile):** `jwt.secret: minibank-super-secret-key-for-development-only-min-256-bits` — **hardcoded**
2. **api-gateway `application-docker.yml`:** `jwt.secret: ${JWT_SECRET:minibank-super-secret-key-for-development-only-min-256-bits}` — **environment variable ile override var, ama default hardcoded**
3. **user-service `application.yml`:** `jwt.secret: ${JWT_SECRET:minibank-super-secret-key-for-development-only-min-256-bits}` — **aynı sorun**
4. **docker-compose.yml:** `JWT_SECRET=${JWT_SECRET:-minibank-super-secret-key-for-development-only-min-256-bits}` — **container seviyesinde de default hardcoded**

#### Tehdit Modeli

| Tehdit | Vektör | Etki | Olasılık |
|--------|--------|------|----------|
| Source code'dan secret elde etme | Git repo'ya erişim | Her kullanıcı token'ı伪造 edilebilir | Kesin |
| Production'da default secret kullanma | Environment variable set edilmediğinde | Tüm sistem compromised | Yüksek |
| Docker image'dan secret okuma | `docker inspect` | Secret exposure | Orta |
| Secret leakage (log, error trace) | Hata durumlarında stack trace | Secret bilinir | Orta |

#### Decision

**Üç katmanlı strateji uygulanacak:**

1. **Default profile'dan hardcoded secret kaldırılacak** — `application.yml`'de `${JWT_SECRET:}` (boş default) olacak. Secret set edilmeden uygulama **başlamayacak**.
2. **docker-compose.yml'de `.env` file referansı** eklenecek.
3. **Secret validation** — uygulama başlangıcında secret uzunluğu kontrol edilecek (minimum 256 bit / 32 karakter).

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Production'da default secret kullanımı engellenir | Local geliştirme için `.env` dosyası gerekli |
| Secret rotation kolaylaşır | CI/CD pipeline'da secret management gerekli |
| Audit compliance sağlanır | Ek konfigürasyon adımı |

#### Uygulama Adımları

**Adım 1:** `.env` dosyası oluştur (proje root):

```env
# .env (git'e eklenmeyecek — .gitignore'a ekle)
JWT_SECRET=<64-karakter-rastgele-string>
```

Secret üretme komutu:
```bash
openssl rand -base64 48
```

**Adım 2:** `docker-compose.yml` güncelle — env_file ekle ve default kaldır:

```yaml
# Her servis container'ına ekle:
env_file:
  - .env

# VEYA environment kısmından default'u kaldır:
environment:
  - JWT_SECRET=${JWT_SECRET}  # default YOK
```

**Adım 3:** `api-gateway/src/main/resources/application.yml` güncelle:

```yaml
# ESKİ:
jwt:
  secret: minibank-super-secret-key-for-development-only-min-256-bits

# YENİ:
jwt:
  secret: ${JWT_SECRET}  # ZORUNLU — set edilmezse uygulama başlamaz
```

**Adım 4:** `user-service/src/main/resources/application.yml` güncelle — aynı şekilde.

**Adım 5:** `api-gateway` ve `user-service`'e başlangıçta secret validation ekle:

```java
// ApiGatewayApplication.java'ya @PostConstruct metodu:
@Value("${jwt.secret}")
private String jwtSecret;

@PostConstruct
public void validateJwtSecret() {
    if (jwtSecret == null || jwtSecret.length() < 32) {
        throw new IllegalStateException(
            "JWT_SECRET must be at least 32 characters. Set JWT_SECRET environment variable.");
    }
}
```

**Adım 6:** `.gitignore` dosyasına `.env` ekle:

```
.env
.env.local
```

**Adım 7:** `.env.example` oluştur (placeholder, git'e eklenecek):

```env
# Copy this to .env and set your own values
JWT_SECRET=change-me-to-at-least-32-characters-random-string
```

---

### ADR-010: Tek JWT Filter Mimarisi

**Durum:** Kabul Edildi
**Sorun ID:** H1
**CVSS:** 5.3 (Yüksek)

#### Context

API Gateway'de şu anda **iki adet** JWT doğrulama bileşeni var:

1. **`JwtAuthenticationFilter.java`** — `GlobalFilter` interface'ini implement ediyor. `parseSignedClaims()` kullanıyor (modern jjwt API). Order: -100. **Aktif ve çalışan** filter. Header'lara `X-User-ID`, `X-User-Email`, `X-User-Role` ekliyor.

2. **`AuthenticationFilter.java`** — `AbstractGatewayFilterFactory` extend ediyor. `JwtUtil` sınıfını kullanıyor. Header'lara sadece `X-User-Username` ekliyor. **RouteConfig'de hiçbir route'da kullanılmıyor**, dead code.

3. **`JwtUtil.java`** — `parseClaimsJws()` kullanıyor (deprecated jjwt API). Sadece `AuthenticationFilter` tarafından kullanılıyor. Dead code.

**Sorun:** İki farklı JWT parsing mantığı, farklı header isimleri, deprecated API. Karışıklık riski.

#### Decision

- `AuthenticationFilter.java` ve `JwtUtil.java` **silecek**.
- `JwtAuthenticationFilter.java` tek ve tekil JWT filter olarak kalacak.
- `JwtAuthenticationFilter`'de ek bir iyileştirme yapılacak: `X-User-Username` header'ı da eklenecek (backward compatibility).

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Tek doğrulama noktası, net akış | Yok — tamamen temizlik |
| Dead code kaldırıldı | Yok |
| Deprecated API kaldırıldı | Yok |
| Bakım kolaylığı arttı | Yok |

#### Uygulama Adımları

**Adım 1:** `AuthenticationFilter.java` SİL

**Dosya:** `api-gateway/src/main/java/com/minibank/gateway/filter/AuthenticationFilter.java`
→ **Dosyayı tamamen sil.**

**Adım 2:** `JwtUtil.java` SİL

**Dosya:** `api-gateway/src/main/java/com/minibank/gateway/filter/JwtUtil.java`
→ **Dosyayı tamamen sil.**

**Adım 3:** `JwtAuthenticationFilter.java`'yi iyileştir — `X-User-Username` header'ı ekle:

```java
// JwtAuthenticationFilter.java — validateToken sonrasına ekle:
// SATIR 96-107 arası:
String userId = claims.getSubject();
String email = claims.get("email", String.class);
String role = claims.get("role", String.class);

// YENİ EKLE:
String username = email; // username = email (MiniBank'da login credential email)

ServerHttpRequest mutatedRequest = request.mutate()
        .header("X-User-ID", userId)
        .header("X-User-Email", email)
        .header("X-User-Role", role != null ? role : "USER")
        .header("X-User-Username", username)  // YENİ
        .build();
```

**Adım 4:** `/api/v1/users/refresh` endpoint'ini public endpoint listesine ekle:

```java
// JwtAuthenticationFilter.java — PUBLIC_ENDPOINTS listesine ekle:
private static final List<String> PUBLIC_ENDPOINTS = List.of(
    "/api/v1/users/login",
    "/api/v1/users/register",
    "/api/v1/users/refresh",   // YENİ EKLE
    "/api/auth/login",
    "/api/auth/register",
    "/actuator",
    "/swagger-ui",
    "/v3/api-docs",
    "/swagger-user",
    "/swagger-account",
    "/swagger-transaction",
    "/swagger-notification",
    "/fallback",
    "/health"
);
```

**Adım 5:** Test dosyalarını güncelle:
- `JwtAuthenticationFilterTest.java` — mevcut testler korunabilir
- `AuthenticationFilterTest.java` — **SİL** (sınıf artık yok)
- `FallbackHandlerTest.java` — etkilenmez
- `CorsConfigTest.java` — etkilenmez
- `RequestLoggingFilterTest.java` — etkilenmez

---

### ADR-011: Inter-Service Authentication

**Durum:** Kabul Edildi
**Sorun ID:** H7
**CVSS:** 8.6 (Yüksek)

#### Context

Şu anda API Gateway, JWT doğrulaması yapıp `X-User-ID` header'ı ile downstream servislerine iletiyor. Ancak **servisler doğrudan erişilebilir** (portlar dışarı açık: 8081, 8082, 8083, 8084). Gateway bypass edildiğinde:

- `account-service:8082`'ye doğrudan istek gönderilebilir
- `transaction-service:8083`'e doğrudan transfer başlatılabilir
- `notification-service:8084`'e doğrudan bildirim okunabilir

Docker Compose'ta portlar host'a publish ediliyor (`ports: - "8081:8081"`).

#### Tehdit Modeli

| Tehdit | Vektör | Etki | Olasılık |
|--------|--------|------|----------|
| Gateway bypass ile bakiye görme | `curl http://host:8082/api/v1/accounts/{id}/balance` | Finansal veri exposure | Kesin (docker'da) |
| Gateway bypass ile para çekme | `curl -X POST http://host:8082/api/v1/accounts/{id}/withdraw` | Para hırsızlığı | Kesin |
| Gateway bypass ile transfer | `curl -X POST http://host:8083/api/v1/transactions/` | Yetkisiz transfer | Kesin |
| Internal network saldırısı | Aynı Docker network'ünde başka container | Lateral movement | Orta |

#### Decision

**İki katmanlı koruma stratejisi:**

1. **Gateway'de `X-Internal-Token` header'ı ekleme** — Gateway, downstream servislere giden her isteğe HMAC-bazlı bir internal token ekler. Servisler bu token'ı doğrular.
2. **Downstream servislerde Internal Token Validation Filter** — Her servis, gelen isteği `X-Internal-Token` header'ı için doğrular. Token yoksa veya geçersizse 401 döner.
3. **Docker Compose'da servis portlarını internal yapma** (isteğe bağlı, production için).

**Neden mTLS değil?** MiniBank bir portfolio projesi. mTLS complexity'si (certificate management, rotation) bu kapsamın dışında. Internal token basit, etkili ve spring-boot-starter ile kolay implementasyon sağlar.

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Gateway bypass engellenir | Ek filter complexity |
| Basit implementasyon | Secret rotation yönetimi gerekli |
| Internal header spoofing koruması | Her servise filter eklemek gerekli |
| Production-ready pattern | Performans overhead (HMAC hesaplama — ~0.1ms) |

#### Mimarı Akış

```
Client Request
     │
     ▼
┌──────────────┐
│ API Gateway  │
│              │  1. JWT doğrula
│              │  2. X-User-ID, X-User-Email ekle
│              │  3. X-Internal-Token hesapla ve ekle
│              │     Token = HMAC-SHA256(timestamp + servicePath, INTERNAL_SECRET)
└──────┬───────┘
       │  Headers: X-User-ID, X-User-Email, X-Internal-Token
       ▼
┌──────────────┐
│ Account Svc  │
│              │  4. InternalAuthFilter: X-Internal-Token doğrula
│              │     - Token yok → 401
│              │     - Token geçersiz → 401
│              │     - Token expired (5dk) → 401
│              │  5. Authorization: X-User-ID ile resource owner match
└──────────────┘
```

#### Uygulama Adımları

**Adım 1:** Internal token generation için utility sınıfı oluştur:

**Dosya:** `api-gateway/src/main/java/com/minibank/gateway/filter/InternalTokenGenerator.java` (YENİ)

```java
package com.minibank.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class InternalTokenGenerator {

    @Value("${internal.auth.secret}")
    private String internalSecret;

    @Value("${internal.auth.token-ttl:300000}") // 5 dakika (ms)
    private long tokenTtl;

    /**
     * Generates an internal auth token for service-to-service communication.
     * Token format: timestamp:hmac(timestamp + requestPath)
     */
    public String generateToken(String requestPath) {
        long timestamp = System.currentTimeMillis();
        String data = timestamp + ":" + requestPath;
        String hmac = calculateHmac(data);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((timestamp + ":" + hmac).getBytes(StandardCharsets.UTF_8));
    }

    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }
}
```

**Adım 2:** `JwtAuthenticationFilter`'a internal token ekleme:

**Dosya:** `api-gateway/src/main/java/com/minibank/gateway/filter/JwtAuthenticationFilter.java`

```java
// Constructor'a ekle:
private final InternalTokenGenerator internalTokenGenerator;

// Satır 103-109 arası (mutatedRequest oluşturma) GÜNCELLE:
ServerHttpRequest mutatedRequest = request.mutate()
        .header("X-User-ID", userId)
        .header("X-User-Email", email)
        .header("X-User-Role", role != null ? role : "USER")
        .header("X-User-Username", username)
        .header("X-Internal-Token", internalTokenGenerator.generateToken(path))  // YENİ
        .build();
```

**Adım 3:** Her downstream servise `InternalAuthFilter` ekle. **Örnek account-service:**

**Dosya:** `account-service/src/main/java/com/minibank/account/config/InternalAuthFilter.java` (YENİ)

```java
package com.minibank.account.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "internal.auth.enabled", havingValue = "true", matchIfMissing = true)
public class InternalAuthFilter implements Filter {

    @Value("${internal.auth.secret}")
    private String internalSecret;

    @Value("${internal.auth.token-ttl:300000}")
    private long tokenTtl;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws java.io.IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Health check ve actuator endpoint'lerini atla
        if (path.startsWith("/actuator") || path.startsWith("/health")
                || path.endsWith("/health") || path.contains("swagger")
                || path.contains("api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        String token = httpRequest.getHeader("X-Internal-Token");

        if (token == null || !validateToken(token, path)) {
            log.warn("Invalid or missing internal auth token for path: {}", path);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"Unauthorized: Invalid internal token\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean validateToken(String token, String requestPath) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int colonIndex = decoded.indexOf(':');
            if (colonIndex < 0) return false;

            long timestamp = Long.parseLong(decoded.substring(0, colonIndex));

            // Token TTL kontrolü
            if (System.currentTimeMillis() - timestamp > tokenTtl) {
                log.warn("Internal token expired");
                return false;
            }

            // HMAC doğrulama
            String data = timestamp + ":" + requestPath;
            String expectedHmac = calculateHmac(data);
            String actualHmac = decoded.substring(colonIndex + 1);

            return expectedHmac.equals(actualHmac);
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC calculation failed", e);
        }
    }
}
```

**Adım 4:** Her servisin `application.yml` ve `application-docker.yml`'ine ekle:

```yaml
# Tüm servislerin application.yml'ine:
internal:
  auth:
    secret: ${INTERNAL_AUTH_SECRET}
    enabled: true
    token-ttl: 300000  # 5 dakika
```

```yaml
# docker-compose.yml environment kısmına:
- INTERNAL_AUTH_SECRET=${INTERNAL_AUTH_SECRET}
```

**Adım 5:** `.env.example` güncelle:

```env
JWT_SECRET=<64-karakter-rastgele-string>
INTERNAL_AUTH_SECRET=<farkli-64-karakter-rastgele-string>
```

**Adım 6:** Aynı filter'ı diğer servisler için kopyala:
- `transaction-service/src/main/java/com/minibank/transaction/config/InternalAuthFilter.java`
- `notification-service/src/main/java/com/minibank/notification/config/InternalAuthFilter.java`
- `user-service/src/main/java/com/minibank/user/config/InternalAuthFilter.java` (C1 ile birlikte)

**Adım 7:** Docker Compose'ta servis portlarını internal yap (production önerisi):

```yaml
# Docker Compose'da port'u sadece gateway dışarı açmalı:
# ESKİ:
account-service:
  ports:
    - "8082:8082"

# YENİ (production):
account-service:
  expose:
    - "8082"  # Sadece internal network'e açık
```

---

## 3. Grup B — Authorization & Veri Tutarlılık Tasarımları

---

### ADR-012: Account Service Authorization / IDOR Koruması

**Durum:** Kabul Edildi
**Sorun ID:** C2
**CVSS:** 9.1 (Kritik)

#### Context

`AccountController.java`'da şu endpoint'lerde authorization yok — herhangi bir kullanıcı, herhangi bir hesaba erişebilir:

- `GET /{id}` — Herhangi bir hesabı görme
- `GET /number/{accountNumber}` — Hesap numarasından hesap görme
- `GET /{id}/balance` — Herhangi bir hesabın bakiyesini görme
- `POST /{id}/deposit` — Herhangi bir hesaba para yükleme
- `POST /{id}/withdraw` — **Herhangi bir hesaptan para çekme**
- `POST /{id}/activate` — Herhangi bir hesabı aktifleştirme
- `POST /{id}/suspend` — Herhangi bir hesabı askıya alma
- `DELETE /{id}` — **Herhangi bir hesabı kapatma**

Ancak `AccountService.java:326-329`'da `isAccountOwner(UUID accountId, UUID userId)` metodu **zaten mevcut** ama hiçbir yerde çağrılmıyor.

#### Tehdit Modeli

| Tehdit | Vektör | Etki | Olasılık |
|--------|--------|------|----------|
| Başka hesabın bakiyesini görme | `GET /api/v1/accounts/{id}/balance` | Finansal gizlilik ihlali | Kesin |
| Başka hesaptan para çekme | `POST /api/v1/accounts/{id}/withdraw` | **DOĞRUDAN PARA HIRSIZLIĞI** | Kesin |
| Başka hesabı kapatma | `DELETE /api/v1/accounts/{id}` | Hizmet reddi | Kesin |
| Toplu hesap tarama | UUID brute-force | Tüm hesap bilgileri exposure | Yüksek |

#### Decision

**Authorization stratejisi:** Her account endpoint'inde, `X-User-ID` header'ı ile hesabın `userId`'si eşleştirilecek. `isAccountOwner()` metodu kullanılacak.

**İstisna:** Transfer sırasında alıcı hesap bilgisi gerektiği için `GET /number/{accountNumber}` endpoint'ine özel muamele yapılacak — sadece accountNumber ve userId döndürülecek, bakiye bilgisi **döndürülmeyecek**.

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| IDOR tamamen engellenir | Her endpoint'de auth check complexity |
| Mevcut `isAccountOwner()` metodu kullanılacak | Transfer için alıcı hesap erişimi özel tasarım |
| Basit ve test edilebilir | Admin rolü olmadan admin işlemleri yapılamaz |

#### Uygulama Adımları

**Adım 1:** `AccountController.java`'da authorization utility metodu ekle:

```java
// AccountController.java — sınıf seviyesine ekle:
/**
 * Validates that the authenticated user owns the requested account.
 * Throws exception if not authorized.
 */
private void validateAccountOwnership(UUID accountId, HttpServletRequest request) {
    String userIdHeader = request.getHeader("X-User-ID");
    if (userIdHeader == null || userIdHeader.isEmpty()) {
        throw new AccountServiceException(
            "Unauthorized: X-User-ID header missing",
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED"
        );
    }
    UUID userId = UUID.fromString(userIdHeader);
    if (!accountService.isAccountOwner(accountId, userId)) {
        throw new AccountServiceException(
            "Access denied: Account does not belong to user",
            HttpStatus.FORBIDDEN,
            "FORBIDDEN"
        );
    }
}
```

**Adım 2:** Şu endpoint'lere authorization ekle:

```java
// GET /{id}
@GetMapping("/{id}")
public ResponseEntity<AccountResponse> getAccountById(
        @PathVariable UUID id, HttpServletRequest request) {
    validateAccountOwnership(id, request);
    AccountResponse response = accountService.getAccountById(id);
    return ResponseEntity.ok(response);
}

// GET /{id}/balance
@GetMapping("/{id}/balance")
public ResponseEntity<BalanceResponse> getBalance(
        @PathVariable UUID id, HttpServletRequest request) {
    validateAccountOwnership(id, request);
    BigDecimal balance = accountService.getBalance(id);
    BigDecimal availableBalance = accountService.getAvailableBalance(id);
    return ResponseEntity.ok(BalanceResponse.builder()
            .accountId(id).balance(balance)
            .availableBalance(availableBalance).build());
}

// POST /{id}/deposit
@PostMapping("/{id}/deposit")
public ResponseEntity<AccountResponse> deposit(
        @PathVariable UUID id, @Valid @RequestBody BalanceUpdateRequest request,
        HttpServletRequest httpRequest) {
    validateAccountOwnership(id, httpRequest);
    // ... mevcut kod
}

// POST /{id}/withdraw
@PostMapping("/{id}/withdraw")
public ResponseEntity<AccountResponse> withdraw(
        @PathVariable UUID id, @Valid @RequestBody BalanceUpdateRequest request,
        HttpServletRequest httpRequest) {
    validateAccountOwnership(id, httpRequest);
    // ... mevcut kod
}

// POST /{id}/activate
@PostMapping("/{id}/activate")
public ResponseEntity<AccountResponse> activateAccount(
        @PathVariable UUID id, HttpServletRequest request) {
    validateAccountOwnership(id, request);
    // ... mevcut kod
}

// POST /{id}/suspend
@PostMapping("/{id}/suspend")
public ResponseEntity<AccountResponse> suspendAccount(
        @PathVariable UUID id, HttpServletRequest request) {
    validateAccountOwnership(id, request);
    // ... mevcut kod
}

// DELETE /{id}
@DeleteMapping("/{id}")
public ResponseEntity<Void> closeAccount(
        @PathVariable UUID id, HttpServletRequest request) {
    validateAccountOwnership(id, request);
    // ... mevcut kod
}
```

**Adım 3:** `GET /number/{accountNumber}` endpoint'ini özel tasarla — IDOR-safe versiyon:

```java
// ESKİ: Tüm hesap bilgilerini döndürüyor
@GetMapping("/number/{accountNumber}")
public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
    AccountResponse response = accountService.getAccountByNumber(accountNumber);
    return ResponseEntity.ok(response);
}

// YENİ: Sadece transfer için gerekli bilgiyi döndür (bakiye YOK)
@GetMapping("/number/{accountNumber}")
public ResponseEntity<Map<String, String>> getAccountByNumber(
        @PathVariable String accountNumber, HttpServletRequest request) {
    String userIdHeader = request.getHeader("X-User-ID");
    if (userIdHeader == null || userIdHeader.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    AccountResponse account = accountService.getAccountByNumber(accountNumber);
    // Sadece ID ve userId döndür — bakiye, durum vb. YOK
    Map<String, String> safeResponse = new HashMap<>();
    safeResponse.put("accountId", account.getId().toString());
    safeResponse.put("accountNumber", account.getAccountNumber());
    safeResponse.put("userId", account.getUserId());
    safeResponse.put("accountType", account.getAccountType());
    safeResponse.put("status", account.getStatus());
    return ResponseEntity.ok(safeResponse);
}
```

**Adım 4:** `GET /user/{userId}` endpoint'ini kaldır veya auth ekle:

```java
// Bu endpoint zaten X-User-ID ile path variable'ı karşılaştırmalı
// VEYA tamamen kaldır (GET / zaten kendi hesaplarını döndürüyor)
@GetMapping("/user/{userId}")
public ResponseEntity<List<AccountResponse>> getAccountsByUserId(
        @PathVariable UUID userId, HttpServletRequest request) {
    String authenticatedUserId = request.getHeader("X-User-ID");
    if (authenticatedUserId == null || !authenticatedUserId.equals(userId.toString())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    // ... mevcut kod
}
```

**Adım 5:** Aynı authorization pattern'ini diğer servislere uygula:

- **TransactionController.java:**
  - `GET /{id}` → X-User-ID ile transaction'ın fromUserId/toUserId kontrolü
  - `GET /saga/{sagaId}` → X-User-ID ile transaction kontrolü
  - `GET /user/{userId}` → X-User-ID ile path match
  - `GET /account/{accountId}` → X-User-ID ile account ownership kontrolü

- **NotificationController.java:**
  - `GET /{notificationId}` → X-User-ID ile notification.userId kontrolü
  - `GET /user/{userId}/**` → X-User-ID ile path match
  - `PUT /{notificationId}/read` → X-User-ID ile notification.userId kontrolü
  - `DELETE /{notificationId}` → X-User-ID ile notification.userId kontrolü

---

### ADR-013: Redis-Based Idempotency (Notification Service)

**Durum:** Kabul Edildi
**Sorun ID:** C5
**CVSS:** 7.5 (Kritik)

#### Context

`TransactionEventConsumer.java:34`'te idempotency için `ConcurrentHashMap` kullanılıyor:

```java
private final ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>();
```

**Sorunlar:**
1. **Restart kaybı:** Service restart olduğunda tüm processed event bilgisi kaybolur → duplicate bildirimler
2. **Multi-instance:** Horizontal scaling'de (2+ instance) her instance farklı memory'ye sahip → aynı event birden fazla instance tarafından işlenir
3. **Memory leak:** Sürekli yeni eventId ekleniyor, hiç temizlenmiyor → OOM riski (production'da)

**Mevcut notification entity'de zaten `idempotencyKey` field'ı var** (`Notification.java`). Bu DB-level idempotency mekanizması kullanılabilir ama Kafka consumer seviyesinde hızlı bir kontrol de gerekli.

#### Decision

**Redis-based idempotency + DB double-check** stratejisi:

1. **Redis SET NX EX:** Event geldiğinde `SET processed:{eventId} 1 EX 86400` (24 saat TTL)
2. **Redis miss → DB check:** `idempotencyKey` ile notification tablosunda sorgula
3. **DB'de yok → İşle:** Notification oluştur
4. **Memory leak yok:** Redis TTL ile otomatik temizlik

Bu strateji, transaction-service'teki distributed idempotency pattern'i ile tutarlıdır.

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Restart sonrası idempotency korunur | Redis dependency |
| Multi-instance uyumlu | Ek Redis round-trip (~0.5ms) |
| Memory leak yok (TTL) | Redis outage'da DB double-check devreye girer |
| Mevcut pattern ile tutarlı | notification-service'e Redis bağımlılığı ekle |

#### Uygulama Adımları

**Adım 1:** notification-service POM'a Redis dependency ekle:

**Dosya:** `notification-service/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Adım 2:** Redis config ekle:

**Dosya:** `notification-service/src/main/java/com/minibank/notification/config/RedisConfig.java` (YENİ)

```java
package com.minibank.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

**Adım 3:** `application.yml` ve `application-docker.yml`'e Redis config ekle:

```yaml
# application.yml:
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

# application-docker.yml:
spring:
  data:
    redis:
      host: redis
      port: 6379
```

**Adım 4:** `TransactionEventConsumer.java`'yi güncelle:

```java
package com.minibank.notification.kafka;

import com.minibank.notification.dto.NotificationResponse;
import com.minibank.notification.dto.TransactionEvent;
import com.minibank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;  // YENİ

    private static final String IDEMPOTENCY_PREFIX = "notification:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @KafkaListener(/* ... mevcut konfigürasyon ... */)
    public void consumeTransactionEvent(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        String eventId = event.getEventId().toString();
        String redisKey = IDEMPOTENCY_PREFIX + eventId;

        try {
            // ADIM 1: Redis hızlı kontrol
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", IDEMPOTENCY_TTL);

            if (Boolean.FALSE.equals(isNew)) {
                log.warn("Duplicate event detected (Redis), skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // ADIM 2: DB double-check (Redis kaybolması durumuna karşı)
            if (notificationService.existsByIdempotencyKey(eventId)) {
                log.warn("Duplicate event detected (DB), skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // ADIM 3: Event'i işle
            processEvent(event);

            log.info("Successfully processed transaction event: {}", eventId);

        } catch (Exception e) {
            // Hata durumunda Redis key'i sil (retry'a izin ver)
            redisTemplate.delete(redisKey);
            log.error("Error processing transaction event {}: {}", eventId, e.getMessage(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    // ... processEvent() ve diğer metodlar AYNI KALIR ...

    // clearProcessedEvents() metodu ARTIK GEREKSIZ — kaldır veya no-op yap
    public void clearProcessedEvents() {
        // Redis TTL ile otomatik temizleniyor
        log.info("Clear processed events is a no-op with Redis-based idempotency");
    }
}
```

**Adım 5:** `NotificationService`'e `existsByIdempotencyKey` metodu ekle:

**Dosya:** `notification-service/src/main/java/com/minibank/notification/service/NotificationService.java`

```java
// NotificationService'e ekle:
@Transactional(readOnly = true)
public boolean existsByIdempotencyKey(String idempotencyKey) {
    return notificationRepository.existsByIdempotencyKey(idempotencyKey);
}
```

**Adım 6:** `NotificationRepository`'e query ekle:

**Dosya:** `notification-service/src/main/java/com/minibank/notification/repository/NotificationRepository.java`

```java
@Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM Notification n WHERE n.idempotencyKey = :key")
boolean existsByIdempotencyKey(@Param("key") String idempotencyKey);
```

---

### ADR-014: SagaCommandConsumer @Transactional Ekleme

**Durum:** Kabul Edildi
**Sorun ID:** H4
**CVSS:** 8.2 (Yüksek)

#### Context

`SagaCommandConsumer.java:59-85`'te Kafka consumer metodu `@Transactional` anotasyonu içermiyor. Account service'in `KafkaConfig.java:43-44`'te `AUTO_OFFSET_RESET_CONFIG = "earliest"` ve `enable.auto.commit = true` (varsayılan) ayarları var.

**Senaryo:** Bakiye düşme (withdraw) işlemi sırasında:
1. `accountService.withdraw()` çağrılıyor → DB'de bakiye düşüyor
2. `publishEvent()` çağrılıyor → Kafka'ya DEBIT_SUCCESS gönderiliyor
3. Eğer adım 2'de hata olursa → bakiye düşüldü ama orchestrator bilmiyor
4. Kafka consumer retry → `withdraw()` tekrar çağrılıyor → **DOUBLE DEBIT**

**Veya:**
1. `withdraw()` başarılı → `publishEvent()` başarısız → exception fırlatılıyor
2. Kafka offset commit olmadı → mesaj tekrar consume edilecek
3. `withdraw()` tekrar → insufficient balance exception → DEBIT_FAILURE
4. **Orchestrator yanlış durumda** (bakiye aslında düşüldü)

#### Decision

**Consumer metodu `@Transactional` ile sarmalanacak.** Ayrıca `handleDebitRequest`, `handleCreditRequest`, `handleCompensateDebit` metodları da `@Transactional` olacak. Kafka consumer'ın ack modu MANUAL olarak değiştirilecek — commit, transaction commit sonrası yapılacak.

**Kritik kural:** Bakiye update ve Kafka publish **aynı mantıksal transaction'da** olmalı. Ancak Kafka ile DB transaction birbirinden bağımsız. Bu durumda strateji:

1. DB update transactionally yapılır
2. Kafka publish yapılır
3. Kafka publish başarısız olursa → exception → DB rollback (Spring @Transactional)
4. Consumer ack → `MANUAL_IMMEDIATE`, acknowledgment Spring transaction commit sonrası yapılır

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| DB tutarlılığı sağlanır | Kafka publish hata durumunda DB rollback → bakiye geri gelir (kabul edilebilir, saga retry) |
| Double-debit engellenir | Manual acknowledgment ek complexity |
| Idempotent saga ile uyumlu | Saga zaten idempotent design'da |

#### Uygulama Adımları

**Adım 1:** `KafkaConfig.java`'da manual acknowledgment ekle:

**Dosya:** `account-service/src/main/java/com/minibank/account/config/KafkaConfig.java`

```java
// kafkaListenerContainerFactory() metoduna ekle:
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
```

**Adım 2:** `SagaCommandConsumer.java`'yi güncelle — Kafka listener'a Acknowledgment ekle:

```java
@KafkaListener(topics = SAGA_COMMANDS_TOPIC, groupId = "account-service-group")
public void consumeSagaCommand(
        Map<String, Object> event,
        Acknowledgment acknowledgment) {  // YENİ PARAMETRE

    String eventType = (String) event.get("eventType");
    UUID sagaId = parseUUID(event.get("sagaId"));
    UUID transactionId = parseUUID(event.get("transactionId"));

    log.info("Received saga command: type={}, sagaId={}, transactionId={}",
            eventType, sagaId, transactionId);

    try {
        switch (eventType) {
            case DEBIT_REQUEST:
                handleDebitRequest(event);
                break;
            case CREDIT_REQUEST:
                handleCreditRequest(event);
                break;
            case COMPENSATE_DEBIT:
                handleCompensateDebit(event);
                break;
            default:
                log.warn("Unknown event type: {}", eventType);
        }
        // Transaction başarılı → acknowledge
        acknowledgment.acknowledge();
    } catch (Exception e) {
        log.error("Error processing saga command: {}", e.getMessage(), e);
        // Transaction başarısız → acknowledge YOK → mesaj yeniden consume edilecek
        // Spring @Transactional DB'yi rollback edecek
    }
}
```

**Adım 3:** `handleDebitRequest` metoduna `@Transactional` ekle:

```java
@Transactional
public void handleDebitRequest(Map<String, Object> event) {
    // ... mevcut kod değişmeden kalır
    // accountService.withdraw() zaten @Transactional
    // YENİ: publishEvent hata olursa exception fırlatılır → DB rollback
    publishEvent(createResponseEvent(...));  // exception fırlatabilir
}
```

**Adım 4:** `publishEvent` metodunu update edilebilir hale getir:

```java
private void publishEvent(Map<String, Object> event) {
    String key = event.get("sagaId").toString();
    try {
        kafkaTemplate.send(SAGA_EVENTS_TOPIC, key, event).get(10, TimeUnit.SECONDS);
        log.info("Published event: type={}, sagaId={}", event.get("eventType"), key);
    } catch (Exception e) {
        log.error("Failed to publish saga event: {}", e.getMessage());
        throw new RuntimeException("Failed to publish saga event", e);
        // Bu exception @Transactional'ı tetikler → DB rollback
    }
}
```

**Adım 5:** `handleCreditRequest` ve `handleCompensateDebit`'e aynı `@Transactional` pattern'ini uygula.

**Adım 6:** Import'ları güncelle:

```java
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.support.Acknowledgment;
```

---

### ADR-015: parseAmount() String-Based BigDecimal Dönüşümü

**Durum:** Kabul Edildi
**Sorun ID:** H8
**CVSS:** 6.5 (Yüksek)

#### Context

`SagaCommandConsumer.java:227-232`'de `parseAmount()` metodu şöyle:

```java
private BigDecimal parseAmount(Object value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal) return (BigDecimal) value;
    if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());  // SORUN
    return new BigDecimal(value.toString());
}
```

**Sorun:** `((Number) value).doubleValue()` satırında Double→BigDecimal dönüşümü precision kaybına neden olur.

**Kanıt:**
```
Double: 10.33
doubleValue(): 10.33
BigDecimal.valueOf(10.33): 10.329999999999998 (hidden precision issue)
new BigDecimal("10.33"): 10.33 (doğru)
```

Kafka mesajlarında amount JSON olarak gelir. Jackson, JSON number'ı `Double` veya `Integer` olarak deserialize eder. `Map<String, Object>` kullanıldığı için amount genellikle `Double` olarak gelir.

#### Decision

`parseAmount()` metodu Double/Float'tan BigDecimal'a dönüşüm yapmayacak. Bunun yerine:

1. `toString()` metodu ile String'e çevrilecek
2. `new BigDecimal(String)` ile parse edilecek (financial precision korunur)
3. Scale kontrolü eklenecek (DECIMAL(19,4) ile uyumlu)

#### Trade-off'lar

| Artı | Eksi |
|------|------|
| Precision kaybı tamamen engellenir | `toString()` formatına bağımlılık (Double.toString() bilimsel gösterim kullanabilir) |
| DB DECIMAL(19,4) ile uyumlu | Yok (kabul edilebilir) |
| Bankacılık standartlarına uygun | |

#### Uygulama Adımları

**Adım 1:** `parseAmount()` metodunu güncelle:

**Dosya:** `account-service/src/main/java/com/minibank/account/kafka/SagaCommandConsumer.java`

```java
// ESKİ:
private BigDecimal parseAmount(Object value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal) return (BigDecimal) value;
    if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
    return new BigDecimal(value.toString());
}

// YENİ:
private static final int AMOUNT_SCALE = 4; // DECIMAL(19,4) ile uyumlu
private static final BigDecimal HUNDRED = new BigDecimal("100");

private BigDecimal parseAmount(Object value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal bd) return bd.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    if (value instanceof Number num) {
        // Double/Float → DOĞRU YÖNTEM: BigDecimal.valueOf(double) yerine
        // String representation üzerinden parse et
        // BigDecimal.valueOf(num.doubleValue()) precision kaybı yapabilir
        // Bunun yerine.Double.toString() → new BigDecimal(String) kullan
        // AMA Double.toString() bilimsel notation kullanabilir (1E+10 gibi)
        String strValue;
        if (num instanceof Double || num instanceof Float) {
            // DecimalFormat ile bilimsel notation'dan kaçın
            strValue = new java.text.DecimalFormat("#.####################").format(num);
        } else {
            strValue = num.toString();
        }
        return new BigDecimal(strValue).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
    return new BigDecimal(value.toString()).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
}
```

**Adım 2:** Import ekle:

```java
import java.math.RoundingMode;
```

**Adım 3:** Aynı `parseAmount()` pattern'ini `SagaOrchestrator.java`'da da kontrol et. `SagaOrchestrator` `BigDecimal` kullanıyor (amount direkt entity'den geliyor), bu yüzden sorun yok. Ama `SagaEvent` deserialize ederken dikkat edilmeli.

**Adım 4:** Long-term iyileştirme (bu sprint dışında): `Map<String, Object>` yerine typed DTO kullan:

```java
// İleride (Sprint 8+): Map yerine typed DTO
public record SagaCommand(
    String eventType,
    UUID sagaId,
    UUID transactionId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,  // Jackson direkt BigDecimal olarak deserialize eder
    String currency
) {}
```

Jackson, JSON field'ını `BigDecimal` olarak deserialize ettiğinde **String yolu ile** yapar, yani precision kaybı olmaz. Bu yüzden typed DTO kullanmak kalıcı çözümdür.

---

## 4. Uygulama Yol Haritası

### 4.1 Sıralama (Dependency Graph)

```
ADR-009 (JWT Secret) ──────────────────────────────────┐
                                                        │
ADR-010 (Tek JWT Filter) ──> ADR-011 (Inter-Service)   │
                                                        │
ADR-008 (SecurityConfig) ──> ADR-012 (Authorization)   │
                                                        ▼
ADR-014 (@Transactional) ──> ADR-015 (parseAmount) ──> ADR-013 (Redis Idempotency)

ÖNERİLEN SIRALAMA:
┌──────────────────────────────────────────────────────────────────┐
│ Phase 1 (Foundation):        Phase 2 (Service Level):           │
│                                                               │
│ 1. ADR-009 (JWT Secret)      5. ADR-008 (SecurityConfig)     │
│ 2. ADR-010 (Tek JWT Filter)  6. ADR-012 (Authorization)      │
│ 3. ADR-011 (Inter-Service)   7. ADR-014 (@Transactional)     │
│                              8. ADR-015 (parseAmount)        │
│                              9. ADR-013 (Redis Idempotency)  │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 Adım Adım Uygulama Planı

| Adım | ADR | Dosya | Değişiklik | Tahmini Süre |
|------|-----|-------|-----------|-------------|
| 1 | 009 | `.env`, `.env.example`, `.gitignore` | Oluştur | 5 dk |
| 2 | 009 | `api-gateway/application.yml`, `application-docker.yml` | JWT secret env var | 5 dk |
| 3 | 009 | `user-service/application.yml` | JWT secret env var | 5 dk |
| 4 | 009 | `docker-compose.yml` | env_file ekle | 10 dk |
| 5 | 009 | `ApiGatewayApplication.java` | Secret validation | 10 dk |
| 6 | 010 | `AuthenticationFilter.java` | SİL | 2 dk |
| 7 | 010 | `JwtUtil.java` | SİL | 2 dk |
| 8 | 010 | `JwtAuthenticationFilter.java` | X-User-Username, /refresh ekle | 15 dk |
| 9 | 010 | `AuthenticationFilterTest.java` | SİL | 2 dk |
| 10 | 011 | `InternalTokenGenerator.java` | YENİ | 30 dk |
| 11 | 011 | `JwtAuthenticationFilter.java` | X-Internal-Token ekle | 10 dk |
| 12 | 011 | `InternalAuthFilter.java` (x4 servis) | YENİ | 45 dk |
| 13 | 011 | Tüm servis `application.yml` | Internal auth config | 20 dk |
| 14 | 011 | `docker-compose.yml` | INTERNAL_AUTH_SECRET ekle | 5 dk |
| 15 | 008 | `SecurityConfig.java` | `.authenticated()` değişikliği | 5 dk |
| 16 | 008 | `UserController.java` | `validateUserAccess()` metodu | 20 dk |
| 17 | 008 | `application-test.yml` | Test security config | 10 dk |
| 18 | 012 | `AccountController.java` | `validateAccountOwnership()` | 30 dk |
| 19 | 012 | `TransactionController.java` | Authorization ekle | 25 dk |
| 20 | 012 | `NotificationController.java` | Authorization ekle | 25 dk |
| 21 | 014 | `account-service/KafkaConfig.java` | Manual ack | 5 dk |
| 22 | 014 | `SagaCommandConsumer.java` | @Transactional + manual ack | 20 dk |
| 23 | 015 | `SagaCommandConsumer.java` | parseAmount() güncelle | 15 dk |
| 24 | 013 | `notification-service/pom.xml` | Redis dependency | 5 dk |
| 25 | 013 | `RedisConfig.java` | YENİ | 10 dk |
| 26 | 013 | `TransactionEventConsumer.java` | Redis-based idempotency | 20 dk |
| 27 | 013 | `NotificationService.java` | existsByIdempotencyKey | 10 dk |
| 28 | 013 | `NotificationRepository.java` | Query ekle | 5 dk |
| 29 | 013 | `notification-service/application.yml` | Redis config | 5 dk |
| | | | **TOPLAM:** | **~6 saat** |

---

## 5. Test Stratejisi

### 5.1 Her ADR İçin Test Senaryoları

| ADR | Test Tipi | Senaryo |
|-----|-----------|---------|
| 008 | Unit | `GET /users/{id}` header yoksa → 401 |
| 008 | Unit | `GET /users/{id}` farklı user → 403 |
| 008 | Unit | `GET /users/{id}` doğru user → 200 |
| 008 | Unit | `POST /register` → 200 (public) |
| 009 | Unit | JWT_SECRET boş → uygulama başlamaz |
| 009 | Unit | JWT_SECRET < 32 karakter → exception |
| 009 | Integration | `.env` ile secret yükleme |
| 010 | Unit | JwtAuthenticationFilter valid token → 200 |
| 010 | Unit | JwtAuthenticationFilter expired token → 401 |
| 010 | Unit | AuthenticationFilter sınıfı yok → compile hatası yok |
| 011 | Unit | Internal token yok → 401 |
| 011 | Unit | Internal token expired → 401 |
| 011 | Unit | Internal token geçersiz HMAC → 401 |
| 011 | Unit | Internal token geçerli → 200 |
| 011 | Integration | Gateway → Service tam akış testi |
| 012 | Unit | `GET /accounts/{id}` farklı user → 403 |
| 012 | Unit | `POST /accounts/{id}/withdraw` farklı user → 403 |
| 012 | Unit | `GET /accounts/{id}` aynı user → 200 |
| 012 | Unit | `GET /number/{accountNumber}` → bakiye YOK |
| 013 | Integration | Redis duplicate event → atla |
| 013 | Integration | Redis yok → DB double-check |
| 013 | Integration | Yeni event → işle |
| 014 | Integration | withdraw + publish başarısız → DB rollback |
| 014 | Integration | withdraw + publish başarılı → commit |
| 015 | Unit | `parseAmount(10.33 Double)` → `10.3300` |
| 015 | Unit | `parseAmount(BigDecimal)` → scale ayarlı |
| 015 | Unit | `parseAmount(null)` → `0.0000` |
| 015 | Unit | `parseAmount("100.123")` → `100.1230` |

### 5.2 Integration Test Infrastructure

Tüm inter-service auth testleri için:
- **Testcontainers** (PostgreSQL, Redis, Kafka)
- **WireMock** (API Gateway mock)
- Test profile'inde `internal.auth.enabled=false` veya mock filter

---

## Ek: Kabul Kriterleri Kontrol Listesi

- [x] Her mikroservis için SecurityConfig yapısı tanımlanmış (ADR-008, ADR-011)
- [x] JWT secret environment variable'a taşınma planı hazır (ADR-009)
- [x] JWT filter karışıklığı çözülmüş — tek filter, net akış (ADR-010)
- [x] Inter-service auth mekanizması tasarlanmış — HMAC-based internal token (ADR-011)
- [x] Her servis için authorization yapısı tasarlanmış — IDOR koruması (ADR-012)
- [x] Redis-based idempotency tasarımı hazır (ADR-013)
- [x] @Transactional ekleme planı spesifik (ADR-014)
- [x] parseAmount() String-based dönüşüm tasarımı hazır (ADR-015)
- [x] Tehdit modeli oluşturulmuş (Bölüm 1.2)
- [x] Uygulama adımları sıralı ve spesifik (Bölüm 4.2)
- [x] Her karar için trade-off'lar belirtilmiş (her ADR'de)

---

*Bu doküman, MiniBank projesi Sprint 7 — Security & Hardening için mimari tasarımı içerir. Dev implementasyonu beklemektedir.*
