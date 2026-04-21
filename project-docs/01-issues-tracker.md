# MiniBank — Sorun Takip Dokumani

> **Son Guncelleme:** 22 Nisan 2026
> **Durum:** Faz 0 Degerlendirmesi

---

## KRITIK SORUNLAR (C) — Hemen Duzeltilmeli

| # | Sorun | Dosya / Yer | Etki | Durum |
|---|-------|-------------|------|-------|
| C1 | `anyRequest().permitAll()` | user-service SecurityConfig.java:56 | Tum endpointler acik | Bekliyor |
| C2 | Authorization yok — isAccountOwner cagrilmiyor | account-service AccountController.java | Herkes her hesaba erisir | Bekliyor |
| C3 | Zayif account number uretimi | account-service AccountService.java:316 | Collision + predictability | Bekliyor |
| C4 | Hardcoded JWT secret | api-gateway application.yml:38 | Secret exposure | Bekliyor |
| C5 | ConcurrentHashMap idempotency | notification-service TransactionEventConsumer | Restart'ta kayip, multi-inst | Bekliyor |

---

## YUKSEK ONCELIKLI SORUNLAR (H)

| # | Sorun | Dosya / Yer | Aciklama | Durum |
|---|-------|-------------|----------|-------|
| H1 | Iki JWT filter (karisiklik) | AuthenticationFilter + JwtUtil vs JwtAuthFilter | Gereksiz duplicate | Bekliyor |
| H2 | ObjectMapper her publish'te yeni | transaction-service OutboxPublisher.java:127 | Performance | Bekliyor |
| H3 | kafkaTemplate.send().get() blocking | transaction-service OutboxPublisher.java:134 | Thread block | Bekliyor |
| H4 | @Transactional eksik Kafka consumer'da | account-service SagaCommandConsumer.java:60 | Data consistency risk | Bekliyor |
| H5 | DLQ (Dead Letter Queue) yok | Tum Kafka consumer'lari | Failed mesajlar kaybolur | Bekliyor |
| H6 | Rate limiting devre disi | api-gateway application.yml:11-16 | Brute-force korumasiz | Bekliyor |
| H7 | Inter-service authentication yok | Tum servisler | Gateway bypass risk | Bekliyor |
| H8 | parseAmount() Double->BigDecimal precision | account-service SagaCommandConsumer.java:230 | Float precision kaybi | Bekliyor |

---

## ORTA ONCELIKLI SORUNLAR (M)

| # | Sorun | Aciklama | Durum |
|---|-------|----------|-------|
| M1 | Test coverage %15-20 (hedef %80+) | 13 test / 84 main dosya | Bekliyor |
| M2 | MapStruct dependency var, kullanilmiyor | 4 modulde dependency, 0 @Mapper | Bekliyor |
| M3 | GlobalExceptionHandler her serviste kopya | common-lib modulune tasinmali | Bekliyor |
| M4 | Notification-service POM tutarsizligi | Farkli parent, JaCoCo/Testcontainers yok | Bekliyor |
| M5 | Service discovery yok | URL'ler hardcoded | Bekliyor |
| M6 | Config server yok | Konfigurasyon her serviste duplicate | Bekliyor |
| M7 | Frontend .idea dosyalari commit'lenmis | .gitignore eksik | Bekliyor |
| M8 | Init scripts tum DB'lere mount ediliyor | Her DB sadece kendi script'ini almalı | Bekliyor |

---

## DUSUK ONCELIKLI SORUNLAR (L)

| # | Sorun | Aciklama | Durum |
|---|-------|----------|-------|
| L1 | CI/CD pipeline yok | Is ilaninda Jenkins isteniyor | Bekliyor |
| L2 | API versioning stratejisi yok | /api/v1/ var ama migration plani yok | Bekliyor |
| L3 | QR Odeme implement edilmemis | Raporda planlanmis, kodda yok | Bekliyor |
| L4 | Swagger/OpenAPI sadece notification'da | Diger servislerde yok | Bekliyor |
| L5 | Structured logging yok | Duz text log, JSON format beklenir | Bekliyor |
| L6 | README.md yok | Proje dokumantasyonu eksik | Bekliyor |

---

## Bagimlilik Haritasi (Hangi Sorunlar Birlikte Cozulmeli)

```
C1 (permitAll) ──┬── H1 (iki JWT filter) ── H7 (inter-service auth)
                 └── H6 (rate limiting)

C4 (JWT secret) ── H7 (inter-service auth)

C2 (auth yok) ── H4 (@Transactional eksik) ── C5 (ConcurrentHashMap)

H2 (ObjectMapper) ── H3 (blocking send) ── birlikte refactor

H5 (DLQ) ── bagimsiz

H8 (parseAmount) ── C2 ile ilgili (saga consumer)
```
