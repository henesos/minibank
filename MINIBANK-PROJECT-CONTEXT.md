# MINIBANK — Proje Context Dokumani

> **Bu dokuman, projeyi sifirdan ogrenecek bir AI (veya developer) icin hazirlanmistir.**
> Proje klasoru ile birlikte bu dosya verildiginde, AI projeyi tam anlamyla kavramali
> ve sonra gorev emri beklemelidir.

---

## 0. KULLANIM TALIMATI (AI ICIN)

```
1. Bu dosyayi bastan sona oku.
2. Proje klasorundeki kodlari incele, bu dokumanla caprazla.
3. Projenin mevcut durumunu, eksiklerini ve mimari kararlarini anla.
4. Hazir oldugunda sadece sunu soyle: "Proje context'ini anladim. Gorev emri bekliyorum."
5. Kullanici sana bir rol verecek (reviewer, developer, architect, tester, vs.).
6. O rolde calismaya basla, ama ASLA bu dokumandaki bilgileri kullanicinin yuzune vurma;
   bunlari biliyor gibi, dogal bir sekilde uygula.
```

---

## 1. PROJE OZETI

| Alan              | Deger                                                        |
|-------------------|--------------------------------------------------------------|
| Proje Adi         | MiniBank — Digital Wallet System                             |
| Proje Tipi        | Microservices-based banking application                      |
| Amac              | Bankada Java Developer pozisyonu icin portfolio projesi     |
| Hedef             | Production-ready seviyede bir dijital cuzdan uygulamasi     |
| Varsayilan Para Birimi | TRY (Turk Lira)                                        |
| Mevcut Versiyon   | 1.0.0-SNAPSHOT                                               |
| Java Versiyonu    | 17 (LTS)                                                     |
| Spring Boot       | 3.2.5                                                        |
| Spring Cloud      | 2023.0.0 / 2023.0.1                                         |

---

## 2. IS ILANI GEREGI (NEDEN BU PROJE VAR)

Bu proje, Istanbul Avrupaka yakasindaki bir bankanin Java Developer ilani icin hazirlaniyor.
Ilanin gereksinimleri ve bu projenin karsiliklari:

| Ilan Gereksinimi                          | Projede Karsiligi                                       |
|-------------------------------------------|---------------------------------------------------------|
| Java 8/11/17/21                           | Java 17 LTS kullanildi                                  |
| Spring Boot, Spring MVC, Hibernate        | Spring Boot 3.2.5, JPA/Hibernate                       |
| React, Git                                | React 19 + Vite 6 frontend                              |
| RESTful, SOAP                             | RESTful API tasarlandi, SOAP yok                        |
| RDBMS (Oracle, SQL Server)                | PostgreSQL 15 (database-per-service)                    |
| Design patterns, full SDLC                | Saga, Outbox, Idempotency, CQRS-lite                    |
| CI/CD (Jenkins), Git (BitBucket)          | CI/CD HENUZ YOK — eklenmeli                             |
| Agile, TDD, BDD                           | Sprint plani var, TDD/BDD HENUZ UYGULANMADI             |
| AWS, Azure, Cloud Native                  | Docker Compose local, Kubernetes planlanmis              |
| Finance/banking experience (plus)         | Banking domain: hesap, bakiye, transfer, saga           |
| Guide developers, review designs          | Mimari dokumanlar mevcut                                |
| Lead migrations to microservices           | Zaten microservices mimarisinde                         |

---

## 3. MIMARI TASARIM

### 3.1 Microservices Mimarisi

```
                        +-------------+
                        |  Frontend   |  (React + Vite + Nginx, Port 80)
                        |  localhost:80|
                        +------+------+
                               |
                        +------+------+
                        | API Gateway |  (Spring Cloud Gateway, Port 8080)
                        |  JWT Auth   |
                        |  Rate Limit |
                        |  Routing    |
                        +------+------+
                               |
          +----------+---------+----------+----------+
          |          |                    |          |
   +------v--+  +----v----+  +-----------v--+  +----v--------+
   |  User    |  | Account |  | Transaction  |  | Notification|
   |  Service |  | Service |  | Service      |  | Service     |
   |  :8081   |  | :8082   |  | :8083        |  | :8084       |
   +------+--+  +----+----+  +------+-------+  +----+--------+
          |          |               |                |
   +------v--+  +----v----+  +------v-------+  +----v--------+
   | user_db |  |account_ |  |transaction_  |  |notification_|
   | :5432   |  |db :5433 |  |db :5434      |  |db :5435     |
   +---------+  +---------+  +--------------+  +-------------+
```

### 3.2 Servis Sorumluluklari

| Servis              | Port | Sorumluluk                                          | Kafka | Redis | Security        |
|---------------------|------|-----------------------------------------------------|-------|-------|-----------------|
| user-service        | 8081 | Kayit, giris, JWT, profil yonetimi                  | Hayir | Evet  | Spring Security |
| account-service     | 8082 | Hesap CRUD, bakiye islemleri, saga participant      | Evet  | Hayir | Hayir           |
| transaction-service | 8083 | Transfer, saga orchestrator, outbox, idempotency    | Evet  | Evet  | Hayir           |
| notification-service| 8084 | Email/SMS/Push bildirimler, Kafka consumer          | Evet  | Hayir | Hayir           |
| api-gateway         | 8080 | Routing, JWT auth, rate limit, circuit breaker      | Hayir | Evet  | Gateway filter  |
| frontend            | 80   | React SPA, Nginx serve                              | Hayir | Hayir | Hayir           |

### 3.3 Database-per-Service Pattern

Her servisin kendi PostgreSQL veritabani var. Servisler birbirinin veritabanina DOGRUDAN erisemez.
Ihtiyaclar API cagrilari veya Kafka event'leri ile giderilir.

| Servis              | Database          | Port |
|---------------------|-------------------|------|
| user-service        | user_db           | 5432 |
| account-service     | account_db        | 5433 |
| transaction-service | transaction_db    | 5434 |
| notification-service| notification_db   | 5435 |

---

## 4. KRITIK MIMARI PATTERN'LER

### 4.1 Saga Pattern (Orchestration Yaklasimi)

Para transferi icin orchestration-based saga kullanilir. Transaction Service orchestrator'dur.

```
Transfer Baslatildi
       |
       v
[Transaction Service (Orchestrator)]
       |
       |--- DEBIT_REQUEST (saga-commands topic) -->
       |                                            [Account Service]
       |<-- DEBIT_SUCCESS/FAILURE (saga-events) ---|
       |
       | (Basarili ise)
       |--- CREDIT_REQUEST (saga-commands topic) -->
       |                                            [Account Service]
       |<-- CREDIT_SUCCESS/FAILURE (saga-events) --|
       |
       | (Credit basarisiz ise -> COMPENSATION)
       |--- COMPENSATE_DEBIT (saga-commands topic) -->
       |                                                [Account Service]
       |<-- COMPENSATE_SUCCESS/FAILURE (saga-events) --|
```

**Saga State Machine:**
```
PENDING -> PROCESSING -> DEBIT_PENDING -> DEBIT_COMPLETED
  -> CREDIT_PENDING -> CREDIT_COMPLETED -> COMPLETED

HATA YOLLARI:
  DEBIT_PENDING -> DEBIT_FAILED -> FAILED
  CREDIT_PENDING -> CREDIT_FAILED -> COMPENSATING -> COMPENSATED
  COMPENSATING -> COMPENSATE_FAILURE -> FAILED (manual intervention)
```

**Kafka Topic'leri:**
- `saga-commands` — Orchestrator'dan participant'lara (account-service)
- `saga-events` — Participant'lardan orchestrator'a (transaction-service)
- `transaction-events` — Transaction service'ten notification service'e

### 4.2 Outbox Pattern

Transaction Service'te event publishing guvenilirligi icin kullanilir.

```
1. SagaOrchestrator, event'i outbox tablosuna yazar (ayni DB transaction icinde)
2. OutboxPublisher (@Scheduled, 1 saniyede bir) PENDING event'leri okur
3. Kafka'ya publish eder
4. Basarili -> SENT, Basarisiz -> retry (max 3), sonra FAILED
5. 7 gunden eski event'ler temizlenir (hourly job)
```

### 4.3 Distributed Idempotency

Transaction Service'te para transferi icin uygulanir.

```
1. Redis'te idempotency key kontrol et
2. DB'de double-check (Redis kaybolsa bile)
3. Gunluk limit kontrolu (50.000 TRY varsayilan)
4. Redis SET NX EX ile atomic lock (race condition onleme)
5. Transaction olustur, saga'yi baslat
6. Tamamlaninca Redis'teki key'e transaction ID yaz
7. TTL: 24 saat
```

### 4.4 Bakiye Guvenlik Kurallari (KRITIK)

```
KURAL 1: BAKIYE ASLA CACHE'LENMEZ
  - Her zaman veritabanindan okunur
  - getBalance() / getAvailableBalance() -> DB query
  - @Cacheable YOK bakiye metodlarinda

KURAL 2: ATOMIC SQL UPDATE
  - deductBalance(): UPDATE ... SET balance = balance - :amount
    WHERE id = :id AND status = 'ACTIVE' AND balance >= :amount
  - addBalance(): UPDATE ... SET balance = balance + :amount
    WHERE id = :id AND status = 'ACTIVE'
  - Tek SQL, race condition yok

KURAL 3: DECIMAL(19,4)
  - BigDecimal kullanilir, double/float YASAK
  - Veritabaninda DECIMAL(19,4) precision

KURAL 4: CHECK CONSTRAINTS
  - balance >= 0
  - available_balance >= 0
  - available_balance <= balance
```

### 4.5 Cache Stratejisi

| Veri Turu        | Cache Stratejisi     | TTL   | Neden                       |
|------------------|----------------------|-------|-----------------------------|
| Bakiye           | ASLA cache'leme      | -     | Tutarlilik kritik           |
| Kullanici profili| Write-Through        | 5 dk  | Sik erisim, az degisim      |
| Session data     | Redis                | 30 dk | Stateless services          |
| Islem gecmisi    | Cache'leme           | -     | Pagination, historical data |

---

## 5. PROJE DOSYA YAPISI

```
minibank/
├── pom.xml                          # Parent POM (multi-module)
├── docker-compose.yml               # 13 container (5 servis + 4 DB + Redis + Kafka + Zookeeper + KafkaUI + Zipkin)
├── init-scripts/                    # PostgreSQL init SQL
│   ├── 01-init-user-db.sql
│   ├── 02-init-account-db.sql
│   ├── 03-init-transaction-db.sql
│   └── 04-init-notification-db.sql
│
├── user-service/                    # Port 8081
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/minibank/user/
│       ├── UserServiceApplication.java
│       ├── config/
│       │   ├── RedisConfig.java          # User cache TTL: 5dk, Session TTL: 30dk
│       │   └── SecurityConfig.java       # ⚠️ anyRequest().permitAll() — KRITIK SORUN
│       ├── controller/
│       │   └── UserController.java       # /api/v1/users/** (11 endpoint)
│       ├── dto/
│       │   ├── AuthResponse.java
│       │   ├── UserLoginRequest.java
│       │   ├── UserRegistrationRequest.java
│       │   ├── UserResponse.java
│       │   └── UserUpdateRequest.java
│       ├── entity/
│       │   └── User.java                 # UUID id, soft delete, GDPR anonimizasyon
│       ├── exception/
│       │   ├── UserServiceException.java # Base: HttpStatus + errorCode
│       │   ├── EmailAlreadyExistsException.java (409)
│       │   ├── InvalidCredentialsException.java (401)
│       │   ├── AccountLockedException.java (423)
│       │   ├── UserNotFoundException.java (404)
│       │   └── GlobalExceptionHandler.java
│       ├── repository/
│       │   └── UserRepository.java       # findByEmail, findByPhone, existsBy...
│       └── service/
│           ├── JwtService.java           # HMAC-SHA256, Access: 24h, Refresh: 7d
│           └── UserService.java          # BCrypt(12), lockout, caching
│
├── account-service/                 # Port 8082
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/minibank/account/
│       ├── AccountServiceApplication.java
│       ├── config/
│       │   └── KafkaConfig.java          # JsonDeserializer -> Map<String,Object>
│       ├── controller/
│       │   └── AccountController.java    # /api/v1/accounts/** (12 endpoint)
│       ├── dto/
│       │   ├── AccountCreateRequest.java
│       │   ├── AccountResponse.java
│       │   └── BalanceUpdateRequest.java
│       ├── entity/
│       │   └── Account.java              # @Version, @SQLRestriction, atomic balance
│       ├── exception/
│       │   ├── AccountServiceException.java
│       │   ├── AccountNotFoundException.java (404)
│       │   ├── InactiveAccountException.java (400)
│       │   ├── InsufficientBalanceException.java (400)
│       │   └── GlobalExceptionHandler.java
│       ├── kafka/
│       │   └── SagaCommandConsumer.java  # saga-commands consumer, ⚠️ @Transactional eksik
│       ├── repository/
│       │   └── AccountRepository.java    # ATOMIC: deductBalance, addBalance, lockFunds
│       └── service/
│           └── AccountService.java       # Bakiye asla cache'lenmez, isAccountOwner VAR AMA KULLANILMIYOR
│
├── transaction-service/             # Port 8083
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/minibank/transaction/
│       ├── TransactionServiceApplication.java
│       ├── config/
│       │   ├── KafkaConfig.java
│       │   ├── OutboxPublisher.java      # @Scheduled: 1s publish, 5s retry, 1h cleanup
│       │   ├── RedisConfig.java
│       │   └── SagaEventConsumer.java    # saga-events consumer -> SagaOrchestrator
│       ├── controller/
│       │   └── TransactionController.java # /api/v1/transactions/** (6 endpoint)
│       ├── dto/
│       │   ├── TransactionResponse.java
│       │   └── TransferRequest.java      # idempotencyKey, fromAccountId, toAccountId, amount
│       ├── entity/
│       │   └── Transaction.java          # Saga state machine, @Version
│       ├── exception/
│       │   ├── TransactionServiceException.java
│       │   ├── TransactionNotFoundException.java (404)
│       │   ├── DuplicateTransactionException.java (409)
│       │   ├── DailyLimitExceededException.java (400)
│       │   └── GlobalExceptionHandler.java
│       ├── outbox/
│       │   ├── OutboxEvent.java          # PENDING -> SENT/FAILED, JSONB payload
│       │   └── OutboxRepository.java
│       ├── repository/
│       │   └── TransactionRepository.java
│       ├── saga/
│       │   ├── SagaEvent.java            # Kafka event DTO, EventType enum
│       │   └── SagaOrchestrator.java     # CORE: startSaga, handleDebit/Credit/Compensate
│       └── service/
│           └── TransactionService.java   # Idempotency, daily limit, saga initiation
│
├── notification-service/            # Port 8084
│   ├── Dockerfile
│   ├── pom.xml                         # ⚠️ Parent: minibank (digerleri spring-boot-starter-parent)
│   └── src/main/java/com/minibank/notification/
│       ├── NotificationServiceApplication.java
│       ├── config/
│       │   ├── KafkaConfig.java          # MANUAL_IMMEDIATE ack, 3 concurrent consumers
│       │   └── KafkaEnableConfig.java    # @Profile("!test")
│       ├── controller/
│       │   └── NotificationController.java # /api/v1/notifications/** (13 endpoint, Swagger annotations)
│       ├── dto/
│       │   ├── NotificationRequest.java
│       │   ├── NotificationResponse.java
│       │   └── TransactionEvent.java
│       ├── entity/
│       │   └── Notification.java         # EMAIL/SMS/PUSH/IN_APP, retry mekanizmasi
│       ├── exception/
│       │   ├── NotificationServiceException.java
│       │   ├── DuplicateNotificationException.java (409)
│       │   ├── NotificationNotFoundException.java (404)
│       │   └── GlobalExceptionHandler.java
│       ├── kafka/
│       │   └── TransactionEventConsumer.java # ⚠️ ConcurrentHashMap idempotency — PRODUCTION DEGIL
│       ├── repository/
│       │   └── NotificationRepository.java
│       └── service/
│           ├── NotificationService.java
│           ├── EmailService.java (interface)
│           ├── EmailServiceImpl.java     # Mock: console log, %1 random failure
│           ├── SmsService.java (interface)
│           └── SmsServiceImpl.java       # Mock: console log, Turk tel validation
│
├── api-gateway/                     # Port 8080
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/minibank/gateway/
│       ├── ApiGatewayApplication.java    # ipKeyResolver bean
│       ├── config/
│       │   ├── CorsConfig.java           # localhost origins
│       │   ├── FallbackRouterConfig.java
│       │   └── RouteConfig.java          # Java-based route config
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       ├── filter/
│       │   ├── JwtAuthenticationFilter.java  # GlobalFilter, order -100, ✅ parseSignedClaims
│       │   ├── AuthenticationFilter.java     # ⚠️ GEREKSIZ — JwtUtil kullaniyor, deprecated API
│       │   ├── JwtUtil.java                  # ⚠️ parseClaimsJws — DEPRECATED
│       │   ├── RateLimitFilter.java          # Order -50, ama rate limiting DISABLED
│       │   └── RequestLoggingFilter.java     # Order -200, X-Request-ID
│       └── handler/
│           └── FallbackHandler.java          # Circuit breaker fallback (503)
│
└── frontend/                        # Port 80 (React + Vite + Nginx)
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json                 # React 19, Vite 6, TanStack Query, Zustand, React Hook Form, Zod
    ├── vite.config.ts
    ├── tailwind.config.js
    └── src/
        ├── App.tsx
        ├── main.tsx
        ├── api/                     # Axios client, accounts, auth, notifications, transactions
        ├── components/              # common/*, layout/*, notifications/*
        ├── hooks/                   # useAuth, useNotification
        ├── pages/                   # accounts, auth, dashboard, profile, transactions
        ├── store/                   # authStore (Zustand)
        ├── types/                   # TypeScript type definitions
        └── utils/                   # cn (clsx+tailwind-merge), format
```

---

## 6. KONFIGURASYON DETAYLARI

### 6.1 Application Properties

Her servis `default` ve `docker` profillerine sahip:

| Profil   | Amac                                  | DB            | Kafka        | Redis         |
|----------|---------------------------------------|---------------|--------------|---------------|
| default  | Lokal gelistirme                      | localhost:543x| localhost:9092| localhost:6379|
| docker   | Docker Compose ortami                 | *-db:5432     | kafka:9092   | redis:6379    |
| test     | Test ortami                           | H2 in-memory  | Disabled     | Embedded/None |

### 6.2 Onemli Konfigurasyon Degerleri

| Ayar                           | Deger          | Servis              |
|--------------------------------|----------------|---------------------|
| JWT Access Token Expiry        | 24 saat        | user-service        |
| JWT Refresh Token Expiry       | 7 gun          | user-service        |
| JWT Secret                     | ⚠️ Hardcoded   | api-gateway, user-svc|
| Max Daily Transfer             | 50.000 TRY     | transaction-service |
| Idempotency TTL                | 24 saat        | transaction-service |
| Saga Retry Count               | 3              | transaction-service |
| BCrypt Strength                | 12             | user-service        |
| Max Failed Login Attempts      | 5              | user-service        |
| Account Lock Duration          | 30 dk          | user-service        |
| User Cache TTL                 | 5 dk           | user-service        |
| Session Cache TTL              | 30 dk          | user-service        |
| Outbox Batch Size              | 100            | transaction-service |
| Kafka Consumer Concurrency     | 3              | notification-service|
| JPA DDL Auto                   | validate (docker: update ⚠️ notification) | All |

---

## 7. DOCKER COMPOSE ALTYAPISI

| Container              | Image                              | Port  | Dependencies               |
|------------------------|------------------------------------|-------|----------------------------|
| frontend               | Custom (Vite -> Nginx)            | 80    | api-gateway                |
| api-gateway            | Custom (Spring Cloud Gateway)     | 8080  | redis, all services        |
| user-service           | Custom (Spring Boot)              | 8081  | user-db, redis             |
| user-db                | postgres:15-alpine                | 5432  | -                          |
| account-service        | Custom (Spring Boot)              | 8082  | account-db, redis, kafka   |
| account-db             | postgres:15-alpine                | 5433  | -                          |
| transaction-service    | Custom (Spring Boot)              | 8083  | transaction-db, redis, kafka|
| transaction-db         | postgres:15-alpine                | 5434  | -                          |
| notification-service   | Custom (Spring Boot)              | 8084  | notification-db, redis, kafka|
| notification-db        | postgres:15-alpine                | 5435  | -                          |
| redis                  | redis:7-alpine                    | 6379  | -                          |
| zipkin                 | openzipkin/zipkin:latest          | 9411  | -                          |
| zookeeper              | cp-zookeeper:7.5.0               | 2181  | -                          |
| kafka                  | cp-kafka:7.5.0                   | 9092/9093 | zookeeper              |
| kafka-ui               | provectuslabs/kafka-ui:latest     | 9000  | kafka, zookeeper           |

**Network:** minibank-network (bridge)
**Volumes:** 8 named volume (data persistence)

---

## 8. ENTITY TASARIMLARI

### 8.1 User Entity

| Field                  | Type            | Constraints                    |
|------------------------|-----------------|--------------------------------|
| id                     | UUID            | PK, auto-generated             |
| email                  | String(255)     | NOT NULL, UNIQUE               |
| passwordHash           | String(255)     | NOT NULL                       |
| phone                  | String(20)      | UNIQUE                         |
| firstName              | String(100)     | -                              |
| lastName               | String(100)     | -                              |
| nationalId             | String(20)      | UNIQUE                         |
| status                 | Enum            | PENDING, ACTIVE, SUSPENDED, LOCKED, CLOSED |
| emailVerified          | Boolean         | DEFAULT false                  |
| phoneVerified          | Boolean         | DEFAULT false                  |
| failedLoginAttempts    | Integer         | DEFAULT 0                      |
| lockedUntil            | LocalDateTime   | -                              |
| lastLoginAt            | LocalDateTime   | -                              |
| deleted                | Boolean         | DEFAULT false, @SQLRestriction |
| createdAt/updatedAt    | LocalDateTime   | @CreatedDate/@LastModifiedDate |
| createdBy/updatedBy    | String          | @CreatedBy/@LastModifiedBy     |

**Soft Delete:** GDPR anonimizasyonu (email -> deleted_{id}@deleted.minibank.com, phone/nationalId -> null)

### 8.2 Account Entity

| Field                  | Type            | Constraints                    |
|------------------------|-----------------|--------------------------------|
| id                     | UUID            | PK, auto-generated             |
| userId                 | UUID            | NOT NULL                       |
| accountNumber          | String(20)      | NOT NULL, UNIQUE               |
| accountType            | Enum            | SAVINGS, CHECKING, BUSINESS    |
| balance                | DECIMAL(19,4)   | >= 0 (DB CHECK)                |
| availableBalance       | DECIMAL(19,4)   | >= 0, <= balance (DB CHECK)    |
| currency               | String(3)       | DEFAULT "TRY"                  |
| status                 | Enum            | PENDING, ACTIVE, DORMANT, SUSPENDED, CLOSED |
| name                   | String          | -                              |
| description            | String          | -                              |
| version                | Integer         | @Version (optimistic locking)  |
| deleted                | Boolean         | @SQLRestriction                |

### 8.3 Transaction Entity

| Field                  | Type            | Constraints                    |
|------------------------|-----------------|--------------------------------|
| id                     | UUID            | PK, auto-generated             |
| sagaId                 | UUID            | UNIQUE                         |
| idempotencyKey         | String          | UNIQUE                         |
| fromAccountId          | UUID            | NOT NULL                       |
| toAccountId            | UUID            | NOT NULL                       |
| fromUserId             | UUID            | -                              |
| toUserId               | UUID            | -                              |
| amount                 | DECIMAL(19,4)   | > 0 (DB CHECK)                 |
| currency               | String(3)       | DEFAULT "TRY"                  |
| status                 | Enum            | PENDING, PROCESSING, DEBITED, COMPLETED, FAILED, COMPENSATING, COMPENSATED |
| sagaStep               | Enum            | STARTED, DEBIT_PENDING/COMPLETED/FAILED, CREDIT_PENDING/COMPLETED/FAILED, COMPENSATION_PENDING/COMPLETED, COMPLETED, FAILED |
| failureReason          | String          | -                              |
| retryCount             | Integer         | DEFAULT 0                      |
| version                | Integer         | @Version                       |

### 8.4 Outbox Event Entity

| Field                  | Type            | Constraints                    |
|------------------------|-----------------|--------------------------------|
| id                     | UUID            | PK                            |
| sagaId                 | UUID            | -                             |
| transactionId          | UUID            | -                             |
| eventType              | Enum            | DEBIT_REQUESTED/COMPLETED/FAILED, CREDIT_REQUESTED/COMPLETED/FAILED, COMPENSATION_REQUESTED/COMPLETED, SAGA_COMPLETED/FAILED |
| aggregateType          | String          | "Transaction"                 |
| aggregateId            | UUID            | -                             |
| payload                | JSONB           | Serialized SagaEvent           |
| status                 | Enum            | PENDING, SENT, FAILED          |
| retryCount             | Integer         | DEFAULT 0, max 3               |
| errorMessage           | String          | -                             |
| sentAt                 | LocalDateTime   | -                             |

### 8.5 Notification Entity

| Field                  | Type            | Constraints                    |
|------------------------|-----------------|--------------------------------|
| id                     | UUID            | PK                            |
| userId                 | UUID            | NOT NULL                      |
| type                   | Enum            | EMAIL, SMS, PUSH, IN_APP      |
| status                 | Enum            | PENDING, SENDING, SENT, DELIVERED, FAILED, CANCELLED |
| subject                | String          | -                             |
| content                | TEXT            | -                             |
| referenceId            | UUID            | -                             |
| idempotencyKey         | String          | UNIQUE                        |
| retryCount/maxRetries  | Integer         | max: 3                        |
| read                   | Boolean         | DEFAULT false                 |

---

## 9. API ENDPOINT'LERI

### 9.1 User Service (`/api/v1/users`)

| Method | Endpoint              | Auth  | Aciklama                     |
|--------|-----------------------|-------|------------------------------|
| POST   | /register             | Hayir | Yeni kullanici kaydi         |
| POST   | /login                | Hayir | Giris, JWT donus             |
| POST   | /refresh              | Hayir | Token yenileme               |
| GET    | /{id}                 | ⚠️ Evet (ama permitAll) | Kullanici detay    |
| GET    | /me                   | ⚠️ Evet (ama permitAll) | Mevcut kullanici   |
| PUT    | /{id}                 | ⚠️ Evet (ama permitAll) | Profil guncelle    |
| DELETE | /{id}                 | ⚠️ Evet (ama permitAll) | Hesap silme        |
| POST   | /{id}/verify-email    | ⚠️ Evet (ama permitAll) | Email dogrulama    |
| POST   | /{id}/verify-phone    | ⚠️ Evet (ama permitAll) | Tel dogrulama      |
| GET    | /health               | Hayir | Health check                 |

### 9.2 Account Service (`/api/v1/accounts`)

| Method | Endpoint              | Auth  | Aciklama                     |
|--------|-----------------------|-------|------------------------------|
| GET    | /                     | X-User-ID | Kullanicinin hesaplari  |
| POST   | /                     | X-User-ID | Yeni hesap olustur      |
| GET    | /{id}                 | ⚠️ Yok | Hesap detay (auth yok!)      |
| GET    | /number/{accountNumber}| ⚠️ Yok | Hesap no ile detay          |
| GET    | /user/{userId}        | ⚠️ Yok | Kullanicinin hesaplari       |
| GET    | /{id}/balance         | ⚠️ Yok | Bakiye sorgula               |
| POST   | /{id}/deposit         | ⚠️ Yok | Para yukle                   |
| POST   | /{id}/withdraw        | ⚠️ Yok | Para cek                     |
| POST   | /{id}/activate        | ⚠️ Yok | Hesap aktiflestir            |
| POST   | /{id}/suspend         | ⚠️ Yok | Hesak askiya al              |
| DELETE | /{id}                 | ⚠️ Yok | Hesap kapat                  |
| GET    | /health               | Hayir | Health check                 |

### 9.3 Transaction Service (`/api/v1/transactions`)

| Method | Endpoint              | Auth  | Aciklama                     |
|--------|-----------------------|-------|------------------------------|
| GET    | /                     | X-User-ID | Islemler (paginated)    |
| POST   | /                     | X-User-ID | Yeni transfer baslat    |
| GET    | /{id}                 | ⚠️ Yok | Islem detay                  |
| GET    | /saga/{sagaId}        | ⚠️ Yok | Saga durumu                  |
| GET    | /user/{userId}        | ⚠️ Yok | Kullanici islemleri          |
| GET    | /account/{accountId}  | ⚠️ Yok | Hesap islemleri              |

### 9.4 Notification Service (`/api/v1/notifications`)

| Method | Endpoint                        | Auth  | Aciklama                  |
|--------|---------------------------------|-------|---------------------------|
| POST   | /                               | Yok   | Bildirim olustur          |
| GET    | /{id}                           | Yok   | Bildirim detay            |
| GET    | /user/{userId}                  | Yok   | Kullanici bildirimleri    |
| POST   | /{id}/send                      | Yok   | Bildirim gonder           |
| GET    | /pending                        | Yok   | Bekleyen bildirimler      |
| POST   | /process                        | Yok   | Toplu islem               |
| GET    | /user/{userId}/unread/count     | Yok   | Okunmamis sayisi          |
| GET    | /user/{userId}/unread           | Yok   | Okunmamis bildirimler     |
| PUT    | /{id}/read                      | Yok   | Okundu isaretle           |
| PUT    | /user/{userId}/read-all         | Yok   | Tumunu okundu isaretle    |
| DELETE | /{id}                           | Yok   | Bildirim sil              |

---

## 10. MEVCUT DURUM VE BILINEN SORUNLAR

### 10.1 Tamamlanmis Olanlar (✅)

- [x] Microservices mimari tasarimi
- [x] Database-per-Service pattern
- [x] Saga Orchestrator (para transferi)
- [x] Outbox Pattern (event publishing guvenilirligi)
- [x] Distributed Idempotency (Redis + DB double-check)
- [x] Atomic balance operations (race-condition-free)
- [x] API Gateway with JWT authentication
- [x] Docker Compose (13 container)
- [x] Frontend scaffold (React 19 + Vite + Tailwind)
- [x] Custom exception hierarchy per service
- [x] Soft delete + GDPR anonimizasyon
- [x] Account lockout mekanizmasi
- [x] Distributed tracing (Micrometer + Zipkin)
- [x] Kafka UI + health checks

### 10.2 KRITIK Sorunlar (🔴 — Hemen Duzeltilmeli)

| #  | Sorun                                          | Dosya / Yer                                  | Etki                         |
|----|------------------------------------------------|----------------------------------------------|------------------------------|
| C1 | `anyRequest().permitAll()`                     | user-service SecurityConfig.java:56          | Tum endpointler acik         |
| C2 | Authorization yok — isAccountOwner cagrilmiyor | account-service AccountController.java       | Herkes her hesaba erisir     |
| C3 | Zayif account number uretimi                   | account-service AccountService.java:316      | Collision + predictability   |
| C4 | Hardcoded JWT secret                           | api-gateway application.yml:38               | Secret exposure              |
| C5 | ConcurrentHashMap idempotency                  | notification-service TransactionEventConsumer | Restart'ta kayip, multi-inst |

### 10.3 YUKSEK Oncelikli Sorunlar (🟡)

| #  | Sorun                                          | Dosya / Yer                                  | Aciklama                    |
|----|------------------------------------------------|----------------------------------------------|-----------------------------|
| H1 | Iki JWT filter (karisiklik)                    | AuthenticationFilter + JwtUtil vs JwtAuthFilter | Gereksiz duplicate         |
| H2 | ObjectMapper her publish'te yeni               | transaction-service OutboxPublisher.java:127 | Performance                 |
| H3 | kafkaTemplate.send().get() blocking            | transaction-service OutboxPublisher.java:134 | Thread block                |
| H4 | @Transactional eksik Kafka consumer'da         | account-service SagaCommandConsumer.java:60  | Data consistency risk       |
| H5 | DLQ (Dead Letter Queue) yok                    | Tum Kafka consumer'lari                      | Failed mesajlar kaybolur    |
| H6 | Rate limiting devre disi                       | api-gateway application.yml:11-16            | Brute-force korumasiz       |
| H7 | Inter-service authentication yok               | Tum servisler                                 | Gateway bypass risk         |
| H8 | parseAmount() Double->BigDecimal precision     | account-service SagaCommandConsumer.java:230 | Float precision kaybi       |

### 10.4 ORTA Oncelikli Sorunlar (🟠)

| #  | Sorun                                          | Aciklama                                    |
|----|------------------------------------------------|---------------------------------------------|
| M1 | Test coverage %15-20 (hedef %80+)              | 13 test / 84 main dosya                     |
| M2 | MapStruct dependency var, kullanilmiyor        | 4 modulde dependency, 0 @Mapper interface   |
| M3 | GlobalExceptionHandler her serviste kopya      | common-lib modulune tasinmali                |
| M4 | Notification-service POM tutarsizligi          | Farkli parent, JaCoCo/Testcontainers yok    |
| M5 | Service discovery yok                          | URL'ler hardcoded                           |
| M6 | Config server yok                              | Konfigurasyon her serviste duplicate        |
| M7 | Frontend .idea dosyalari commit'lenmis         | .gitignore eksik                            |
| M8 | Init scripts tum DB'lere mount ediliyor        | Her DB sadece kendi script'ini almalı       |

### 10.5 DUSUK Oncelikli Sorunlar (🔵)

| #  | Sorun                                          | Aciklama                                    |
|----|------------------------------------------------|---------------------------------------------|
| L1 | CI/CD pipeline yok                             | Is ilaninda Jenkins isteniyor               |
| L2 | API versioning stratejisi yok                  | /api/v1/ var ama migration plani yok        |
| L3 | QR Odeme implement edilmemis                   | Raporda planlanmis, kodda yok               |
| L4 | Swagger/OpenAPI sadece notification'da         | Diger servislerde yok                       |
| L5 | Structured logging yok                         | Duz text log, JSON format beklenir          |
| L6 | README.md yok                                  | Proje dokumantasyonu eksik                  |

---

## 11. SPRINT PLANI VE ILERLEME

| Sprint | Konu                              | Durum       | Cikti                                         |
|--------|-----------------------------------|-------------|-----------------------------------------------|
| 1      | Domain Analysis                   | ✅ Tamam    | Proje kapsami, ozellik listesi               |
| 2      | Tech Stack Decision               | ✅ Tamam    | Java 17, Spring Boot 3.x, PostgreSQL, vb.    |
| 3      | Architecture Design               | ✅ Tamam    | C4 Model, microservices yapisi               |
| 4      | Database Design                   | ✅ Tamam    | ER Diagram, DB per Service                    |
| 5      | Architecture Review               | ✅ Tamam    | Saga, Idempotency, Outbox cozumleri          |
| 6      | Core Implementation               | 🟡 %70     | Ana servisler kodlandi, eksikler var         |
| 7      | Security & Hardening              | ❌ Baslamadi| Auth, authorization, secret management       |
| 8      | Test & Quality (TDD)              | ❌ Baslamadi| Unit/Integration/E2E tests, %80+ coverage    |
| 9      | CI/CD & DevOps                    | ❌ Baslamadi| GitHub Actions, Docker, monitoring           |
| 10     | Documentation & Polish            | ❌ Baslamadi| README, Swagger, API docs                    |

---

## 12. POM YAPISI VE DEPENDENCY'LER

### 12.1 Parent POM

- GroupId: `com.minibank`, ArtifactId: `minibank`, Packaging: `pom`
- 5 modul: user-service, account-service, transaction-service, notification-service, api-gateway
- Spring Boot 3.2.0 BOM + Spring Cloud 2023.0.0 BOM

### 12.2 Modul Dependency Farkliliklari

| Ozellik            | user-svc | account-svc | tx-svc  | notif-svc | gateway |
|--------------------|----------|-------------|---------|-----------|---------|
| Parent             | s-b-s-p  | s-b-s-p     | s-b-s-p | ⚠️ minibank | s-b-s-p |
| Spring Security    | Evet     | Hayir       | Hayir   | Hayir     | Hayir (reactive) |
| Kafka              | Hayir    | Evet        | Evet    | Evet      | Hayir   |
| MapStruct          | Evet     | Evet        | Evet    | Hayir     | Hayir   |
| JaCoCo             | Evet     | Evet        | Evet    | Hayir     | Hayir   |
| Testcontainers     | Evet     | Evet        | Evet    | Hayir     | Hayir   |
| Redis              | Evet     | Hayir       | Evet    | Hayir     | Evet   |

---

## 13. FRONTEND TEKNOLOJI STACK'I

| Teknoloji           | Versiyon   | Amac                         |
|---------------------|------------|------------------------------|
| React               | 19.0.0     | UI Framework                 |
| Vite                | 6.0.5      | Build tool                   |
| TypeScript          | 5.6.2      | Type safety                  |
| Tailwind CSS        | 3.4.17     | Styling                      |
| Zustand             | 5.0.2      | State management             |
| TanStack React Query| 5.62.8     | Data fetching                |
| Axios               | 1.7.9      | HTTP client                  |
| React Router DOM    | 7.1.1      | Routing                      |
| React Hook Form     | 7.54.2     | Form management              |
| Zod                 | 3.24.1     | Validation                   |
| Lucide React        | 0.469.0    | Icons                        |
| Nginx               | -          | Production serve             |

---

## 14. TEST DURUMU

### 14.1 Mevcut Test Dosyalari

| Servis              | Test Dosyalari                                                        |
|---------------------|-----------------------------------------------------------------------|
| user-service        | UserServiceTest.java (unit), UserServiceIntegrationTest.java          |
| account-service     | AccountServiceTest.java (unit)                                        |
| transaction-service | SagaOrchestratorTest.java (unit), TransactionServiceTest.java (unit)  |
| notification-service| NotificationServiceTest.java (unit), NotificationServiceIntegrationTest.java, TestConfig.java |
| api-gateway         | GatewayIntegrationTest.java, CorsConfigTest.java, FallbackHandlerTest.java, JwtAuthenticationFilterTest.java, RequestLoggingFilterTest.java |

### 14.2 Eksik Testler

- Account Service: Integration test yok
- Saga end-to-end test yok
- Outbox publisher test yok
- Idempotency test yok
- API Gateway rate limit test yok
- Frontend test yok (hiç)
- Load/stress test yok
- Contract test yok

---

## 15. DESIGN PATTERN KULLANIM OZETI

| Pattern                    | Nerede                         | Durum             |
|----------------------------|--------------------------------|-------------------|
| Saga Orchestrator          | Transaction Service            | ✅ Uygulandi     |
| Outbox Pattern             | Transaction Service            | ✅ Uygulandi     |
| Database-per-Service       | Tum servisler                  | ✅ Uygulandi     |
| API Gateway                | API Gateway                    | ✅ Uygulandi     |
| CQRS-lite                  | Account Service (read/write)   | ✅ Uygulandi     |
| Idempotency Key            | Transaction + Notification     | ⚠️ Notif. eksik |
| Soft Delete                | Tum entity'ler                 | ✅ Uygulandi     |
| Audit Trail                | Tum entity'ler                 | ✅ Uygulandi     |
| Optimistic Locking         | Account, Transaction           | ✅ Uygulandi     |
| Pessimistic Locking        | Account Repository             | ✅ Uygulandi     |
| Atomic Balance Update      | Account Repository             | ✅ Uygulandi     |
| Circuit Breaker            | API Gateway                    | ✅ Uygulandi     |
| Strategy Pattern           | Notification (Email/Sms)       | ✅ Uygulandi     |
| Repository Pattern         | Tum servisler (Spring Data)    | ✅ Uygulandi     |
| DTO Pattern                | Tum servisler                  | ✅ Uygulandi     |
| MapStruct                  | Dependency var, kod yok        | ❌ Kullanilmadi  |
| Service Discovery (Eureka) | -                              | ❌ Yok           |
| Config Server              | -                              | ❌ Yok           |
| Event Sourcing             | -                              | ❌ Yok (raporda yanlis anlasildi, duzeltildi) |

---

## 16. ONEMLI KARARLAR VE GEREKCELER

| Karar                                       | Gerekce                                                      |
|---------------------------------------------|--------------------------------------------------------------|
| Orchestration over Choreography             | Merkezi gozetim, daha iyi hata yonetimi, bankacilik icin uygun |
| Outbox Pattern (Polling Publisher)          | Basit implementasyon, Debezium CDC'ye gecis kolay           |
| Micrometer over Sleuth                      | Sleuth Spring Boot 3.x ile deprecated                       |
| PostgreSQL over Oracle/SQL Server           | Ucretsiz, Docker uyumlu, yetirli                            |
| Redis for idempotency + cache               | Atomic operations (SET NX EX), sub-ms latency                |
| UUID over Long ID                           | Distributed sistemlerde ID cakismasi onleme                  |
| DECIMAL(19,4) over DOUBLE                   | Finansal precision kaybi onleme                              |
| Atomic SQL UPDATE over JPA save             | Race condition onleme, bakiye tutarliligi                    |

---

## 17. SKOR TABLOSU (MEVCUT DURUM)

| Kategori              | Puan (1-10) | Yorum                                    |
|-----------------------|-------------|------------------------------------------|
| Mimari Tasarim        | 9/10        | Pattern secimleri mukemmel               |
| Domain Bilgisi        | 8/10        | Bankacilik kavramlari dogru anlasilmis   |
| Bakiye Guvenligi      | 10/10       | Production-level thinking                |
| Kod Kalitesi          | 7/10        | Temiz ama duplicate kod var              |
| Guvenlik              | 4/10        | permitAll + authorization eksikligi      |
| Test Coverage         | 3/10        | En zayif halka                           |
| Infrastructure        | 7/10        | Docker Compose iyi, CI/CD eksik          |
| Documentation         | 6/10        | Javadoc iyi, README yok                  |
| Consistency           | 5/10        | POM tutarsizligi, duplicate handler      |
| Production Readiness  | 4/10        | Henuz production'a hazir degil           |

**GENEL ORTALAMA: 6.3/10**

---

## 18. ONCELIKLI GELISTIRME YOL HARITASI

```
Faz 1: SECURITY HARDENING (1-2 gun)
  ├── C1: permitAll() -> .authenticated() + JWT filter user-service
  ├── C2: Authorization check (isAccountOwner) tum account endpoint'lerde
  ├── C3: SecureRandom account number generation (Luhn algoritmasi)
  ├── C4: .env dosyasi + JWT secret environment variable
  └── C5: ConcurrentHashMap -> Redis idempotency (notification-service)

Faz 2: CODE QUALITY (1-2 gun)
  ├── H1: AuthenticationFilter + JwtUtil sil
  ├── H2-H3: OutboxPublisher refactor (ObjectMapper inject, async publish)
  ├── H4: @Transactional ekle SagaCommandConsumer'a
  ├── H8: parseAmount() String-based yap
  └── M2: MapStruct kullan veya kaldir

Faz 3: INFRASTRUCTURE (1-2 gun)
  ├── H5: DLQ konfigurasyonu ekle
  ├── H6: Rate limiting aktiflestir
  ├── H7: Inter-service authentication
  ├── M4: Notification-service POM duzelt
  └── M8: Init scripts ayir

Faz 4: TEST COVERAGE (3-4 gun)
  ├── Unit tests: Her service %80+ coverage
  ├── Integration tests: Testcontainers ile
  ├── Saga end-to-end test
  ├── JaCoCo raporu
  └── Frontend test (Vitest + React Testing Library)

Faz 5: DEVOPS (1-2 gun)
  ├── GitHub Actions CI/CD pipeline
  ├── M3: common-lib modulu (GlobalExceptionHandler)
  ├── Swagger/OpenAPI tum servislere
  ├── Structured logging (JSON format)
  └── README.md + API dokumantasyon

Faz 6: ADVANCED (bonus)
  ├── Service Discovery (Eureka/Consul)
  ├── Config Server (Spring Cloud Config)
  ├── QR Odeme implementasyonu
  ├── Kubernetes manifest'leri
  └── Load test (Gatling/k6)
```

---

## 19. AI ICIN CALISMA KURALLARI

Bu dokumani okuyan AI asagidaki kurallara uymalidir:

1. **Proje context'ini bildigini varsay** — Kullaniciya "dokumanda sunu okudum" deme, dogal akista uygula.
2. **Mevcut mimari kararlara saygi goster** — Saga, Outbox, Atomic Balance gibi kararlar tartisilmaz, uygulanir.
3. **Bankacilik domain kurallarini bil** — Bakiye asla cache'lenmez, double/float yasak, idempotency sart.
4. **Dil tutarliligi** — Kullanici Turkce konusuyorsa Turkce yanit ver. Kod ve teknik terimler Ingilizce.
5. **Gorev emri bekle** — Proje context'ini anladiktan sonra gorev bekle. Rol verilene kadar proaktif degil.
6. **Degisiklikleri dokumante et** — Yaptigin her degisikligi bu dokumanin ilgili bolumune not olarak ekle.
7. **Sorun onceligini bil** — Kritik (C) > Yuksek (H) > Orta (M) > Dusuk (L). Oncelik disina cikma.
8. **Test yazmadan kod yazma** — TDD yaklasimi: Red -> Green -> Refactor.

---

> **Dokuman Versiyon:** 1.0
> **Son Guncelleme:** 20 Nisan 2026
> **Proje Ilerlemesi:** Sprint 6-7 arasi, %60-65 tamamlandi
