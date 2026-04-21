# MiniBank — Proje Ozet Dokumani

> **Son Guncelleme:** 22 Nisan 2026
> **Durum:** Faz 0 Tamamlandi
> **Proje Ilerlemesi:** %60-65 (Sprint 6-7 arasi)

---

## 1. Proje Bilgileri

| Alan | Deger |
|------|-------|
| Proje Adi | MiniBank — Digital Wallet System |
| Proje Tipi | Microservices-based banking application |
| Amac | Bankada Java Developer pozisyonu icin portfolio projesi |
| Hedef | Production-ready dijital cuzdan uygulamasi |
| Para Birimi | TRY (Turk Lira) |
| Versiyon | 1.0.0-SNAPSHOT |
| Java | 17 (LTS) |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.0 / 2023.0.1 |

---

## 2. Mevcut Durum Ozeti

### Tamamlanan Sprintler (1-5)
- Domain Analysis, Tech Stack, Architecture Design, Database Design, Architecture Review

### Devam Eden (Sprint 6)
- Core Implementation: %70 tamamlandi
- Ana servisler kodlandi ama kritik eksiklikler var

### Baslanmamis (Sprint 7-10)
- Security & Hardening
- Test & Quality (TDD)
- CI/CD & DevOps
- Documentation & Polish

---

## 3. Teknoloji Yigini

| Katman | Teknoloji | Versiyon |
|--------|-----------|----------|
| Backend | Java + Spring Boot | 17 / 3.2.5 |
| Database | PostgreSQL | 15 |
| Cache | Redis | 7 |
| Message Broker | Apache Kafka | 7.5.0 (Confluent) |
| Frontend | React + TypeScript + Vite | 19 / 5.6.2 / 6 |
| Containerization | Docker Compose | - |
| Tracing | Micrometer + Zipkin | - |
| State Management | Zustand | 5.0.2 |
| Data Fetching | TanStack React Query | 5.62.8 |

---

## 4. Servis Mimarisi

```
Frontend (:80) -> API Gateway (:8080) -> [User (:8081), Account (:8082), Transaction (:8083), Notification (:8084)]
```

Her servisin kendi PostgreSQL veritabani var (Database-per-Service).
Servisler arasi iletisim: REST (senkron) + Kafka (asenkron)

---

## 5. Kritik Mimari Patternler

1. **Saga Orchestrator** — Para transferi icin orchestration-based saga
2. **Outbox Pattern** — Event publishing guvenilirligi (Polling Publisher)
3. **Distributed Idempotency** — Redis + DB double-check
4. **Atomic Balance Update** — Tek SQL ile race-condition-free bakiye guncelleme
5. **Bakiye Asla Cache'lenmez** — Her zaman DB'den okunur

---

## 6. Skor Tablosu

| Kategori | Puan | Yorum |
|----------|------|-------|
| Mimari Tasarim | 9/10 | Pattern secimleri mukemmel |
| Domain Bilgisi | 8/10 | Bankacilik kavramlari dogru |
| Bakiye Guvenligi | 10/10 | Production-level |
| Kod Kalitesi | 7/10 | Temiz ama duplicate kod var |
| Guvenlik | 4/10 | permitAll + auth eksikligi |
| Test Coverage | 3/10 | En zayif halka |
| Infrastructure | 7/10 | Docker iyi, CI/CD eksik |
| Documentation | 6/10 | Javadoc iyi, README yok |
| Consistency | 5/10 | POM tutarsizligi |
| Production Readiness | 4/10 | Production'a hazir degil |

**GENEL ORTALAMA: 6.3/10**

---

## 7. Sorun Oncelik Ozeti

| Oncelik | Sayi | Aciklama |
|---------|------|----------|
| Kritik (C) | 5 | Hemen duzeltilmeli — guvenlik aciklari |
| Yuksek (H) | 8 | Kisa surede duzeltilmeli — tutarlilik/risk |
| Orta (M) | 8 | Planli duzeltilmeli — kalite iyilestirme |
| Dusuk (L) | 6 | Bonus — profesyonellik |

---

## 8. Proje Dosya Konumlari

| Kaynak | Yol |
|--------|-----|
| Kaynak Kod | /home/z/my-project/upload/minibank-extracted/minibank/ |
| Belge Havuzu | /home/z/my-project/project-docs/ |
| Context Dokumani | /home/z/my-project/upload/minibank-extracted/minibank/MINIBANK-PROJECT-CONTEXT.md |
| PDF Rapor | /home/z/my-project/upload/proje.pdf |
