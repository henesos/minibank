# SonarLint Rules — MiniBank Project

> Sprint 9 — SonarLint Refactoring
> Target: Bankacılık standartlarına uygun temiz kod

---

## 1. Security (Güvenlik) — 7 Kural

| # | Kural ID | Kural Adı | Açıklama |
|---|----------|-----------|----------|
| 1 | S5804 | Hardcoded credentials | Kritik bilgiler (JWT secret, DB password) environment variable üzerinden alınmalı, kaynak kodda asla hardcode edilmemeli. |
| 2 | S5122 | URI injection vulnerability | URL'ler kullanıcı girdisi ile doğrudan birleştirilmemeli. Parametreli sorgular veya encode kullanılmalı. |
| 3 | S2077 | SQL injection risk | JPA `@Query`'lerde string concatenation yerine `@Param` ile named parameters kullanılmalı. |
| 4 | S2095 | Resources should be closed | InputStream, Connection gibi kaynaklar try-with-resources ile kullanılmalı. |
| 5 | S2755 | Deserialization trust | Kafka `JsonDeserializer.TRUSTED_PACKAGES = "*"` yerine spesifik paketler belirtilmeli (arbitrary class deserialization riski). |
| 6 | S2278 | Weak cryptography | `java.util.Random` veya `Math.random()` yerine `SecureRandom` kullanılmalı (kriptografik işlem gerektiren yerlerde). |
| 7 | S6437 | Sensitive log | Hassas veriler (password, token, card number) loglarda asla yazılmamalı. |

## 2. Bug — Hata Desenleri — 6 Kural

| # | Kural ID | Kural Adı | Açıklama |
|---|----------|-----------|----------|
| 1 | S2225 | Boxed primitive return | Metotlar `Boolean`, `Integer` gibi boxed type yerine `boolean`, `int` primitive dönmeli. NPE riskini önler. |
| 2 | S112 | Generic exception catch | `catch(Exception e)` yerine spesifik exception türleri yakalanmalı. Hata gizlenmesini önler. |
| 3 | S2259 | Null pointer risk | NullPointerException riski olan yerlerde `@NonNull`, `Optional` veya null check kullanılmalı. |
| 4 | S1181 | Catch Throwable | `catch(Throwable t)` asla kullanılmamalı. `catch(Exception e)` veya daha spesifik türler kullanılmalı. |
| 5 | S2696 | Instance writing to static field | Instance metod static field yazmamalı. Thread-safe olmayan kalıp hatalara yol açar. |
| 6 | S1068 | Unused private fields | Kullanılmayan private field'lar kaldırılmalı. Dead code artığı oluşturur. |

## 3. Code Smell — Kod Koku — 7 Kural

| # | Kural ID | Kural Adı | Açıklama |
|---|----------|-----------|----------|
| 1 | S1192 | String literal duplicates | Aynı string literal 3+ kez tekrar ediyorsa `static final` constant'a çıkarılmalı. |
| 2 | S1128 | Unnecessary imports / FQN | Fully qualified names (`java.util.List`) yerine `import` kullanılmalı. Kod okunabilirliğini artırır. |
| 3 | S1144 | Unused private methods | Kullanılmayan private metodlar kaldırılmalı. |
| 4 | S106 | System.out.println | `System.out.println()` yerine SLF4J `log` kullanılmalı. Log yönetimi ve seviye kontrolü için gerekli. |
| 5 | S108 | Empty catch blocks | Boş catch blokları en azından log mesajı veya `// justified` comment içermeli. |
| 6 | S3776 | Cognitive complexity | Metodların bilişsel karmaşıklığı 15'i geçmemeli. Karmaşık metodlar alt metodlara bölünmeli. |
| 7 | S2139 | Exceptions logged at INFO level | Exception'lar `log.warn()` veya `log.error()` ile loglanmalı. `log.info()` ile exception loglamak ciddi sorunları maskeleyebilir. |

## 4. Vulnerability — Açık — 3 Kural

| # | Kural ID | Kural Adı | Açıklama |
|---|----------|-----------|----------|
| 1 | S3329 | Uncontrolled format string | `String.format()` ile dışarıdan gelen veriler doğrudan format string olarak kullanılmamalı. JSON injection riski var. |
| 2 | S2612 | Regular expression denial of service | Regex pattern'ları kullanıcı girdisi ile dinamik oluşturulmamalı. ReDoS saldırısı riski. |
| 3 | S5131 | Weak SSL/TLS configuration | TLS versiyonları güncel tutulmalı (TLS 1.2+). Güvenli cipher suite'ler kullanılmalı. |

---

## Spring Boot Özel Kuralları

| Kural | Açıklama |
|-------|----------|
| @Autowired field injection | Constructor injection tercih edilmeli. `@RequiredArgsConstructor` ile Lombok kullanılabilir. |
| @Transactional scope | `@Transactional` annotasyonu sadece `public` metodlarda etkili olur. Private metodlara eklenmemeli. |
| Lombok generated code | SonarLint bazen Lombok ile üretilen kodlar için false positive üretir. Bu durumlar `// NOSONAR` ile işaretlenebilir. |
| JPA entity equals/hashCode | Lombok `@Data` entity'lerde equals/hashCode sorununa yol açabilir. `@EqualsAndHashCode(of = {"id"})` kullanılmalı. |

---

## False Positive Politikası

Aşağıdaki durumlar SonarLint false positive olarak kabul edilir ve `// NOSONAR` ile işaretlenir:
- Lombok `@Builder`, `@Data` ürettiği metodlar için S1144 (unused private methods)
- `GlobalExceptionHandler`'daki `catch(Exception.class)` — bu kasıtlı olarak tüm exception'ları yakalamak için tasarlanmıştır
- Kafka consumer'larında `catch(Exception e)` — message processing safety net olarak kabul edilir
- Spring Data Repository'dekiJPQL `@Query` — parametreli sorgu kullanımı doğrulanmıştır

---

*Toplam: 23 SonarLint kuralı (7 Security + 6 Bug + 7 Code Smell + 3 Vulnerability)*
