# MiniBank — Karar Kayitlari (Decisions Log)

> **Son Guncelleme:** 22 Nisan 2026
> **Amac:** Her mimari/teknik karar ve gerekcesini kaydetmek

---

## Mevcut Kararlar (Proje Basindan Beri)

| # | Karar | Gerekce | Tarih | Degistirilebilir mi? |
|---|-------|---------|-------|---------------------|
| D1 | Orchestration over Choreography (Saga) | Merkezi gozetim, daha iyi hata yonetimi, bankacilik icin uygun | Sprint 5 | Hayir |
| D2 | Outbox Pattern (Polling Publisher) | Basit implementasyon, Debezium CDC'ye gecis kolay | Sprint 5 | Evet (Debezium'e gecis) |
| D3 | Micrometer over Sleuth | Sleuth Spring Boot 3.x ile deprecated | Sprint 5 | Hayir |
| D4 | PostgreSQL over Oracle/SQL Server | Ucretsiz, Docker uyumlu, yetirli | Sprint 2 | Hayir |
| D5 | Redis for idempotency + cache | Atomic operations (SET NX EX), sub-ms latency | Sprint 5 | Hayir |
| D6 | UUID over Long ID | Distributed sistemlerde ID cakismasi onleme | Sprint 4 | Hayir |
| D7 | DECIMAL(19,4) over DOUBLE | Finansal precision kaybi onleme | Sprint 4 | Hayir |
| D8 | Atomic SQL UPDATE over JPA save | Race condition onleme, bakiye tutarliligi | Sprint 5 | Hayir |
| D9 | React 19 + Vite 6 frontend | Modern, hizli build, iyi DX | Sprint 2 | Hayir |
| D10 | Docker Compose local dev | Sifir butce, lokal gelistirme | Sprint 2 | Evet (K8s'e gecis) |

---

## Yeni Kararlar (AI Agent Takimi Tarafindan)

| # | Karar | Gerekce | Tarih | Sprint |
|---|-------|---------|-------|--------|
| - | - | - | - | - |

---

## Karar Degisiklik Gecmisi

| Tarih | Eski Karar | Yeni Karar | Gerekce |
|-------|-----------|-----------|---------|
| - | - | - | - |
