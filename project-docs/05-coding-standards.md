# MiniBank — Kodlama Standartlari

> **Son Guncelleme:** 22 Nisan 2026

---

## 1. Genel Kurallar

- **Java 17** ozelliklerini kullan (records, sealed classes, pattern matching)
- **Clean Code** prensiplerine uy
- **SOLID** prensiplerini uygula
- Her metodun **tek sorumlulugu** olsun
- Magic number/string kullanma, constant tanimla
- Her public metod icin **Javadoc** yaz

---

## 2. Bankacilik Domain Kurallari (KRITIK)

### 2.1 Bakiye Kurallari
```
KURAL 1: BAKIYE ASLA CACHE'LENMEZ
  - Her zaman veritabanindan okunur
  - @Cacheable YOK bakiye metodlarinda

KURAL 2: ATOMIC SQL UPDATE
  - deductBalance(): UPDATE ... SET balance = balance - :amount WHERE balance >= :amount
  - addBalance(): UPDATE ... SET balance = balance + :amount
  - Tek SQL, race condition yok

KURAL 3: DECIMAL(19,4)
  - BigDecimal kullanilir, double/float YASAK
  - Veritabaninda DECIMAL(19,4) precision

KURAL 4: CHECK CONSTRAINTS
  - balance >= 0
  - available_balance >= 0
  - available_balance <= balance
```

### 2.2 Idempotency Kurallari
- Her finansal istek idempotency key ile gelmelidir
- Redis SET NX EX ile atomic lock
- DB'de double-check (Redis kaybolsa bile)
- TTL: 24 saat

### 2.3 Saga Kurallari
- Orchestrator state'i PostgreSQL'de saklanir
- Her adimin compensation islemi tanimli olmalidir
- Crash recovery mekanizmasi olmalidir

---

## 3. Kod Organizasyonu

### 3.1 Paket Yapisi (Her servis icin)
```
com.minibank.{servis}/
├── config/          # Konfigurasyon siniflari
├── controller/      # REST controller'lar
├── dto/             # Request/Response DTO'lar
├── entity/          # JPA Entity'ler
├── exception/       # Ozel exception siniflari
├── repository/      # Spring Data Repository'ler
├── service/         # Business logic
├── kafka/           # Kafka consumer/producer (varsa)
├── outbox/          # Outbox pattern (varsa)
└── saga/            # Saga orchestrator (varsa)
```

### 3.2 Naming Convention
- Class: PascalCase (`UserService`, `AccountController`)
- Method: camelCase (`getBalance`, `processTransfer`)
- Constant: UPPER_SNAKE_CASE (`MAX_DAILY_LIMIT`)
- Package: kucuk harf (`com.minibank.transaction`)

---

## 4. Exception Yonetimi

- Her servis kendi `GlobalExceptionHandler`'ina sahip
- Base exception: `{Servis}ServiceException` (HttpStatus + errorCode)
- Ozel exception'lar base'den turetilir
- Hicbir zaman ham exception firlatma, DTO ile sar

---

## 5. API Tasarim Standartlari

- URL: `/api/v1/{resource}` formati
- HTTP metodlari: GET (oku), POST (olustur), PUT (guncelle), DELETE (sil)
- Response wrapper: `{"data": ..., "message": ..., "timestamp": ...}`
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- HATEOAS: MVP'de yok, ileride eklenebilir

---

## 6. Test Standartlari

- **TDD Yaklasimi:** Red -> Green -> Refactor
- Unit test coverage: %80+ hedef
- Test naming: `methodName_scenario_expectedResult`
  - Ornek: `transfer_insufficientBalance_throwsException`
- Her bug fix icin mutlaka test yaz
- Integration test: Testcontainers kullan

---

## 7. Git Kurallari

- Branch: `feature/{sprint-no}-{kisa-aciklama}`
  - Ornek: `feature/sprint7-security-hardening`
- Commit: `type(scope): message`
  - Ornek: `fix(security): add JWT authentication to user-service`
- Types: feat, fix, refactor, test, docs, chore

---

## 8. Guvenlik Standartlari

- JWT secret asla hardcoded olmaz (environment variable)
- BCrypt strength: 12
- Rate limiting: Aktif olmali
- CORS: Sadece izin verilen origin'ler
- Input validation: Her endpoint'te Zod/Bean Validation
- SQL injection: JPA parameter binding, string concatenation YASAK
