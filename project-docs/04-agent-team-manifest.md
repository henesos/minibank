# MiniBank — AI Agent Takimi Manifesti

> **Son Guncelleme:** 22 Nisan 2026
> **Takim Boyutu:** 6 Agent

---

## Takim Uyeleri

### 1. Proje Yoneticisi / Scrum Master (PM)
**Rol:** Sprint planlama, gorev dagilimi, surec takibi, risk yonetimi
**Calisma Sekli:** Her sprint basinda plan olusturur, sprint sonunda retrospektif yapar
**Belgeler:** sprint-plan.md, issues-tracker.md

### 2. Is Analisti / Product Owner (BA)
**Rol:** Gereksinim analizi, kullanici hikayeleri, kabul kriterleri
**Calisma Sekli:** Istekleri analiz eder, kabul kriterleri tanimlar
**Belgeler:** requirements.md (guncellenecek)

### 3. Yazilim Mimari (Architect)
**Rol:** Sistem tasarimi, teknoloji kararları, mimari desenler, API tasarimi, code review
**Calisma Sekli:** Mimari kararlar alir, mevcut kodu review eder, refactor onerileri sunar
**Belgeler:** architecture.md, decisions-log.md

### 4. Full-Stack Gelistirici (Dev)
**Rol:** Kod yazimi, modul gelistirme, birim testleri, bug fix
**Calisma Sekli:** Gorev emirlerine gore kod yazar, test yazar
**Belgeler:** Kod tabani (minibank/)

### 5. QA Muhendisi (QA)
**Rol:** Test stratejisi, kalite guvencesi, code review, hata takibi
**Calisma Sekli:** Test planlar, test yazar, kod review yapar
**Belgeler:** test-strategy.md (olusturulacak)

### 6. DevOps Muhendisi (DevOps)
**Rol:** CI/CD, altyapi, deployment, izleme, Docker/K8s
**Calisma Sekli:** Pipeline kurar, altyapi iyilestirir, monitoring kurar
**Belgeler:** devops.md (olusturulacak)

---

## Iletisim Protokolu

1. Agent'lar birbirleriyle DOGRUDAN iletisim kurmaz
2. Tum iletisim belge havuzu uzerinden yapilir
3. Sen (Proje Sahibi) koordinasyonu saglarsin
4. Her agent cagrisinda taze context inject edilir
5. Her agent ciktisini belge havuzuna yazar

---

## Agent Cagri Sablonu

```markdown
## ROL
{agent_rolu_ve_uzmanlik_alani}

## CONTEXT (Belge Havuzundan)
- Proje Ozeti: [00-project-summary.md'den]
- Mevcut Sorunlar: [01-issues-tracker.md'den]
- Mimari Kararlar: [03-decisions-log.md'den]
- Sprint Plani: [02-sprint-roadmap.md'den]

## GOREV
{spesifik_gorev_aciklamasi}

## CIKTI FORMATI
{beklenen_cikti_formati_ve_dosya_konumu}

## KISITLAR
- Sadece verilen context ile calis
- Varsayim yapma, belirsizlik varsa belirt
- Bankacilik domain kurallarina uy (bakiye asla cache'lenmez, double/float yasak)
- Oncelik sirasina uy: C > H > M > L
- Ciktini belirtilen dosyaya yaz
```

---

## Context Taze Tutma Stratejisi

Her agent cagrisinda:
1. Belge havuzundan guncel dosyalari oku
2. Sadece ilgili dosyalari inject et (hepsini degil)
3. Gorev tamamlandiginda ciktiyi belge havuzuna yaz
4. Session'i kapat (stateless yaklasim)

**Agent basina inject edilecek dosyalar:**

| Agent | Inject Edilecek Belgeler |
|-------|------------------------|
| PM | 00-project-summary, 01-issues-tracker, 02-sprint-roadmap |
| BA | 00-project-summary, 01-issues-tracker, requirements |
| Architect | 00-project-summary, 03-decisions-log, 01-issues-tracker, kaynak kod |
| Dev | 00-project-summary, 03-decisions-log, ilgili sorun detaylari, kaynak kod |
| QA | 00-project-summary, 01-issues-tracker, test-strategy, kaynak kod |
| DevOps | 00-project-summary, 01-issues-tracker, docker-compose, CI/CD config |
