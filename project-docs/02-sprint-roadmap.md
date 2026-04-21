# MiniBank — Sprint Roadmap ve Gelisme Plani

> **Son Guncelleme:** 22 Nisan 2026
> **Mevcut Sprint:** 6 (Core Implementation — %70)

---

## Gecmis Sprintler (Tamamlanmis)

| Sprint | Konu | Durum | Cikti |
|--------|------|-------|-------|
| 1 | Domain Analysis | Tamam | Proje kapsami, ozellik listesi |
| 2 | Tech Stack Decision | Tamam | Java 17, Spring Boot 3.x, PostgreSQL, vb. |
| 3 | Architecture Design | Tamam | C4 Model, microservices yapisi |
| 4 | Database Design | Tamam | ER Diagram, DB per Service |
| 5 | Architecture Review | Tamam | Saga, Idempotency, Outbox cozumleri |

---

## Mevcut Sprint

### Sprint 6: Core Implementation (%70)
- [x] Microservices mimari yapisi
- [x] Database-per-Service pattern
- [x] Saga Orchestrator (para transferi)
- [x] Outbox Pattern
- [x] Distributed Idempotency
- [x] Atomic balance operations
- [x] API Gateway JWT authentication
- [x] Docker Compose (13 container)
- [x] Frontend scaffold
- [ ] Security hardening (kritik sorunlar)
- [ ] Test yazimi

---

## Planlanan Sprintler

### Sprint 7: Security & Hardening (Oncelik: EN YUKSEK)
**Sure:** 1-2 gun
**Hedef:** Tum Kritik (C) ve Yuksek (H) sorunlari coz

**Gorevler:**
- [ ] C1: permitAll() -> .authenticated() + JWT filter user-service
- [ ] C2: Authorization check (isAccountOwner) tum account endpoint'lerde
- [ ] C3: SecureRandom account number generation (Luhn algoritmasi)
- [ ] C4: .env dosyasi + JWT secret environment variable
- [ ] C5: ConcurrentHashMap -> Redis idempotency (notification-service)
- [ ] H1: AuthenticationFilter + JwtUtil sil (gereksiz filter)
- [ ] H4: @Transactional ekle SagaCommandConsumer'a
- [ ] H8: parseAmount() String-based yap
- [ ] H6: Rate limiting aktiflestir

### Sprint 8: Code Quality & Infrastructure (Oncelik: YUKSEK)
**Sure:** 1-2 gun
**Hedef:** Kod kalitesi ve altyapi iyilestirmeleri

**Gorevler:**
- [ ] H2-H3: OutboxPublisher refactor (ObjectMapper inject, async publish)
- [ ] H5: DLQ konfigurasyonu ekle
- [ ] H7: Inter-service authentication
- [ ] M2: MapStruct kullan veya kaldir
- [ ] M4: Notification-service POM duzelt
- [ ] M8: Init scripts ayir

### Sprint 9: Test & Quality (Oncelik: YUKSEK)
**Sure:** 3-4 gun
**Hedef:** %80+ test coverage, TDD uygulamasi

**Gorevler:**
- [ ] Unit tests: Her service %80+ coverage
- [ ] Integration tests: Testcontainers ile
- [ ] Saga end-to-end test
- [ ] JaCoCo raporu
- [ ] Frontend test (Vitest + React Testing Library)

### Sprint 10: DevOps & Documentation (Oncelik: ORTA)
**Sure:** 1-2 gun
**Hedef:** CI/CD pipeline, dokumantasyon

**Gorevler:**
- [ ] GitHub Actions CI/CD pipeline
- [ ] M3: common-lib modulu (GlobalExceptionHandler)
- [ ] Swagger/OpenAPI tum servislere
- [ ] Structured logging (JSON format)
- [ ] README.md + API dokumantasyon

### Sprint 11+: Advanced (Bonus)
- [ ] Service Discovery (Eureka/Consul)
- [ ] Config Server (Spring Cloud Config)
- [ ] QR Odeme implementasyonu
- [ ] Kubernetes manifest'leri
- [ ] Load test (Gatling/k6)

---

## AI Agent Takimi Gorev Dagilimi

| Sprint | Birincil Agent | Destek Agent |
|--------|---------------|-------------|
| 7 | Mimar + Dev | QA (security test) |
| 8 | Dev + DevOps | Mimar (refactor review) |
| 9 | QA | Dev (test-friendly kod) |
| 10 | DevOps | Analist (dokumantasyon) |
