# MiniBank — Functional Gap Analysis Raporu

> **Tarih:** 22 Nisan 2026
> **Kapsam:** Tum servisler + Frontend (100+ dosya incelendi)
> **Sonuc:** Proje vaatlerinin %40'i tam calismiyor, %30'u kismen calisiyor

---

## EXECUTIVE SUMMARY

MiniBank, **mimari tasarim ve domain bilgisi acisindan cok guclu** bir proje. Saga, Outbox, Idempotency, Atomic Balance gibi pattern'ler dogru secilmis. Ancak **kod implementasyonunda kritik kopukluklar** var. Projenin vaat ettigi temel ozelliklerin buyuk kismi ya calismiyor ya da guvenlik acigi iceriyor.

**Genel Degerlendirme:** Proje "demo seviyesinde" calisiyor, "production-ready" degil.

---

## 1. SERVIS BAZLI OZET TABLO

### User Service (Port 8081)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Kayit (POST /register) | ⚠️ KISMİ | nationalId duplicate kontrol yok, kayit sonrasi token donmuyor |
| Giris (POST /login) | ⚠️ KISMİ | PENDING kullanici login yapabiliyor, lockout unlock yok |
| JWT Yenileme (POST /refresh) | ⚠️ KISMİ | Refresh token blacklist yok, replay attack riski |
| Profil Islemleri | 🔴 GUVENLIK | permitAll() + IDOR - herkes herkesin profilini okuyabilir |
| Email/Phone Dogrulama | ❌ CALISMIYOR | Token mekanizmasi yok, direkt flag toggle |
| Hesap Silme | ⚠️ KISMİ | GDPR: firstName/lastName/passwordHash temizlenmiyor |
| Health Check | ⚠️ KISMİ | Hardcoded "UP", dependency check yok |

### Account Service (Port 8082)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Hesap Olusturma | ⚠️ KISMİ | Account number collision riski, Luhn yok |
| Hesap Listeleme | 🔴 GUVENLIK | Authorization yok, herkes her hesabi gorebilir |
| Bakiye Islemleri | ✅ CALISIYOR | Atomic SQL + BigDecimal + DB constraint |
| Hesap Aktiflestirme/Askıya Alma | ⚠️ KISMİ | State machine yok, CLOSED->ACTIVE gecisi var |
| Saga Participant (Kafka) | ⚠️ KISMİ | @Transactional yok, Double->BigDecimal precision sorunu |
| Soft Delete | ✅ CALISIYOR | Bakiye kontrolu + status gecisi |

### Transaction Service (Port 8083)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Transfer Baslatma | ⚠️ KISMİ | fromUserId null→daily limit bypass, idempotency complete cagrilmiyor |
| Saga Orchestrator | ✅ CALISIYOR | Happy path + failure path tam |
| Outbox Pattern | ⚠️ KISMİ | Blocking .get(), ObjectMapper her seferinde yeni |
| Saga Event Consumer | ⚠️ KISMİ | @Transactional yok, DLQ yok |
| Idempotency | ⚠️ KISMİ | markIdempotencyComplete() hic cagrilmiyor |
| Gunluk Limit | ⚠️ KISMİ | Sadece COMPLETED sayiliyor, PENDING dahil degil |
| Transaction Sorgulama | 🔴 GUVENLIK | Authorization yok, herkes her islemi gorebilir |
| Saga State Machine | ⚠️ KISMİ | Timeout/retry implementasyonu yok |

### Notification Service (Port 8084)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Kafka Consumer | ⚠️ KISMİ | ConcurrentHashMap idempotency, toUserId bildirimi yok |
| Bildirim CRUD | ✅ CALISIYOR | Pagination, soft delete, idempotency key |
| Email/SMS Gonderim | ❌ MOCK | Tamamen mock, gercek provider yok |
| Retry Mekanizmasi | ❌ CALISMIYOR | markAsFailed() her durumda cagriliyor, retry calismaz |
| Okundu Isaretleme | ✅ CALISIYOR | Tekil ve toplu okundu |
| Authorization | 🔴 GUVENLIK | Herkes herkesin bildirimini gorebilir/silebilir |
| Scheduled Retry Job | ❌ YOK | @EnableScheduling var ama @Scheduled metod yok |

### API Gateway (Port 8080)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Routing | ✅ CALISIYOR | 4 servis + legacy routes |
| JWT Authentication | ✅ CALISIYOR | parseSignedClaims (dogru API) |
| Rate Limiting | ❌ KAPALI | Konfigürasyon yorum satirinda |
| Circuit Breaker | ❌ CALISMIYOR | Resilience4j dependency var ama kullanilmiyor |
| CORS | ⚠️ KISMİ | Nginx+Gateway cakismasi, wildcard sorunlu |
| JWT Secret | 🔴 GUVENLIK | Hardcoded, environment variable yok |
| Duplicate Filter | ⚠️ KARISIKLIK | AuthenticationFilter dead code |

### Frontend (Port 80)

| Akis | Durum | Aciklama |
|------|-------|----------|
| Login/Register | ✅ CALISIYOR | Form validation + API baglantisi + token store |
| Dashboard | ⚠️ KISMİ | "This Month +12%" hardcoded, API response format riski |
| Hesap Islemleri | ⚠️ KISMİ | Currency USD (TRY olmali), deposit/withdraw sentetik transaction |
| Transfer | ⚠️ KISMİ | Idempotency key var ama onay dialogu yok |
| Bildirimler | ⚠️ KISMİ | 30sn polling (WebSocket yok), isim karisikligi |
| Protected Routes | ✅ CALISIYOR | Token check + 401 redirect |
| Error Handling | ⚠️ KISMİ | API hatalari gosteriliyor ama global error boundary yok |
| Nginx | ⚠️ KISMİ | SPA routing + proxy OK ama CORS wildcard sorunu |

---

## 2. PROJE VAAT ETTIGI VS. GERCEKTE YAPABILDIGI

| Vaat Edilen | Gerecek Durum | Calisiyor mu? |
|-------------|--------------|---------------|
| Kullanici kaydi ve girisi | Kayit var, giris var ama PENDING login yapabilir | ⚠️ Kısmi |
| JWT tabanli kimlik dogrulama | Gateway'de var, servislerde yok | ⚠️ Kısmi |
| Email/Telefon dogrulama | Sadece flag toggle, token mekanizmasi yok | ❌ Hayır |
| Hesap olusturma ve yonetimi | CRUD var ama authorization yok | ⚠️ Kısmi |
| Bakiye islemleri (yatır/cek) | Atomic SQL ile calisiyor | ✅ Evet |
| Para transferi (Saga) | Orchestrator calisiyor ama idempotency complete yok | ⚠️ Kısmi |
| Gunluk transfer limiti | Var ama sadece COMPLETED sayiliyor, bypass edilebilir | ⚠️ Kısmi |
| Islem gecmisi | Pagination var ama authorization yok | ⚠️ Kısmi |
| Email/SMS/Push bildirimler | Tamamen mock, gercek gonderim yok | ❌ Hayır |
| QR ile odeme | Kodda hic implement edilmemis | ❌ Hayır |
| Rate limiting | Kod var ama devre disi | ❌ Hayır |
| Circuit breaker | Dependency var ama kullanilmiyor | ❌ Hayır |
| Distributed tracing | Micrometer + Zipkin konfigüre edilmis | ✅ Evet |
| API Gateway JWT auth | Calisiyor | ✅ Evet |
| Frontend UI | Temel sayfalar var, USD/TRY karisikligi | ⚠️ Kısmi |

**Sonuc:** 15 vaatten sadece 3'u tam calisiyor, 8'i kismen calisiyor, 4'u hic calismiyor.

---

## 3. EN KRITIK 10 SORUN (PRODUCTION BLOCKER)

| # | Sorun | Servis | Etki |
|---|-------|--------|------|
| 1 | **permitAll() — Tum endpointler acik** | User Service | Herkes her veriye erisir |
| 2 | **Authorization yok — IDOR** | Account+Transaction+Notification | Herkes her hesabi/islemi gorebilir |
| 3 | **Email/Phone dogrulama sahte** | User Service | Guvenlik dogrulamasi deger tasimaz |
| 4 | **Hardcoded JWT Secret** | Gateway+User Service | Token calinabilir |
| 5 | **fromUserId null→gunluk limit bypass** | Transaction Service | Sinirsiz transfer mumkun |
| 6 | **markIdempotencyComplete() cagrilmiyor** | Transaction Service | 24 saat blokaj |
| 7 | **ConcurrentHashMap idempotency** | Notification Service | Restart'ta duplicate |
| 8 | **Retry logic bug** | Notification Service | Bildirimler kaybolur |
| 9 | **Double→BigDecimal precision** | Account Saga Consumer | Finansal hesap hatasi |
| 10 | **Circuit Breaker+Rate Limiting kapali** | Gateway | DDoS ve servis cokusu riski |

---

## 4. IKI BUYUK FONKSIYONEL KOPUKLUK

### Kopukluk 1: Dogrulama Akisi Tamamen Kırık
```
BEKLENEN:
Kayit → verification token uret → email/SMS gonder → 
kullanici kodu gir → validate et → status=ACTIVE

GERCEKTE:
Kayit → status=PENDING → POST /verify-email (hicbir kod istemiyor) → 
status=ACTIVE → BITTI

Sonuc: Guvenlik dogrulamasi yok. Herkes herkesinin email/phone'ini dogrulayabilir.
```

### Kopukluk 2: Bildirim Akisi Yarim
```
BEKLENEN:
Transfer → Saga event → Kafka → Notification Consumer →
Email/SMS gonder → Basarisizsa retry → DLQ

GERCEKTE:
Transfer → Saga event → Kafka → Notification Consumer →
MOCK log bas → Basarisizsa markAsFailed (retry calismaz) → 
Bildirim kaybolur

Sonuc: Kullaniciya hicbir bildirim ulasmiyor.
```

---

## 5. ONERILEN FAZ SIRASI

### Faz 1: Guvenlik (EN ACIL)
- permitAll() → authenticated()
- Tum servislere authorization ekle
- JWT secret environment variable
- fromUserId @NotNull

### Faz 2: Finansal Dogruluk
- Double→BigDecimal duzelt
- markIdempotencyComplete() ekle
- Gunluk limite PENDING dahil et
- Account number Luhn algoritmasi

### Faz 3: Fonksiyonel Kopukluklar
- Verification token mekanizmasi
- Notification retry logic fix
- Email/SMS provider entegrasyonu
- Circuit Breaker aktiflestir

### Faz 4: Kalite
- Test coverage %80+
- Rate limiting aktiflestir
- CORS duzelt
- Frontend USD→TRY
