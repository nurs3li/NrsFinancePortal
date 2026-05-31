<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# finance-service

Çekirdek portal API — kullanıcı/hesaplar, portföy, simülasyonlar, admin özellikleri, fiyat alarmları, Portföy AI ve piyasa vekili (proxy).

---

## Özet

| Öğe | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_finance` |
| **Migrasyonlar** | Liquibase (`src/main/resources/db/changelog/`) |
| **Docker portu** | 8085 → konteyner 8080 |
| **Yerel port** | 8080 (varsayılan) |
| **Swagger** | http://localhost:8085/swagger-ui.html |

---

## Sorumluluklar

- Kayıt ve kullanıcı profili yönetimi (genel + kimlik doğrulamalı API'ler)
- Portföy yönetimi (manuel pozisyonlar; etkinse vadeli/tahvil analizi)
- Yatırım simülasyonları
- Fiyat alarmı değerlendirme ve bildirim tetikleme
- Portföy AI analizi (OpenAI — isteğe bağlı)
- Admin: kullanıcı yönetimi, denetim log sorguları, gözlemlenebilirlik admin endpoint'leri
- Keycloak admin entegrasyonları (roller, OTP politikası, kullanıcı yaşam döngüsü)
- `marketdata`'ya delege eden piyasa terminali vekili

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL | Kalıcı depolama |
| Redis | Hız sınırlama, TOTP gizli depolama, önbellek |
| Keycloak | JWT issuer + admin API |
| marketdata | Piyasa fiyatları ve geçmiş veri |
| notification-service | Bildirimler ve e-posta |
| Kafka | Loglama hattı taşıması |
| OpenSearch | Denetim log araması |
| Tempo / Grafana | Gözlemlenebilirlik admin entegrasyonları |

---

## Çalıştırma

### Docker Compose (önerilen)

Depo kökünden:

```powershell
docker compose up -d finance-service
```

Bağımlılıklar (Postgres, Keycloak, market-data, Redis, Kafka, OpenSearch vb.) tam yığın çalıştırıldığında otomatik başlar.

Servis URL: http://localhost:8085  
Swagger UI: http://localhost:8085/swagger-ui.html

Sağlık:

```powershell
curl http://localhost:8085/actuator/health
```

---

## Test

- CI, backend modülleri için birim + entegrasyon testleri (Testcontainers) çalıştırır.
- Yerel duman: `curl http://localhost:8085/actuator/health`

---

## Ana paketler

```
com.nurseli.nrsfinanceportal/
├── api/                    # REST controller, DTO, GlobalExceptionHandler
├── application/            # Use-case servisleri / iş mantığı
├── domain/                 # Varlıklar ve domain modelleri
├── infrastructure/         # JPA, Keycloak, Kafka, OpenSearch istemcileri
└── config/                 # SecurityConfig, OpenApiConfig, RedisConfig
```

---

## Public API özeti

| Controller | Temel path | Amaç |
|------------|------------|------|
| `PublicRegistrationController` | `/api/public/register` | `request-code`, `complete` |
| `PublicLoginController` | `/api/public` | `login`, `token/refresh` |
| `PublicPasswordResetController` | `/api/public/password-reset` | `request-code`, `verify-code`, `complete` |

Kayıt ve şifre sıfırlama e-postaları Gmail gerektirir — [`docs/email-setup.tr.md`](../docs/email-setup.tr.md).

### Admin askıya alma

| Metot | Path | Controller |
|-------|------|------------|
| POST | `/api/admin/users/{userId}/suspend-login` | `AdminUserSuspensionController` |
| POST | `/api/admin/users/{userId}/unsuspend-login` | `AdminUserSuspensionController` |

Bkz. [`docs/security/README.tr.md`](../docs/security/README.tr.md).

---

## Ortam değişkenleri (Docker)

| Değişken | Açıklama |
|----------|----------|
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `MARKET_DATA_SERVICE_URL` | marketdata temel URL |
| `KEYCLOAK_ADMIN_*` | Keycloak admin istemcisi |
| `OPENAI_API_KEY` | Portföy AI (isteğe bağlı) |
| `APP_OBSERVABILITY_*` | OpenSearch, Tempo, Grafana URL |

Tam liste: depo kökü `.env.example` ve `application-docker.yml`

---

## API sürümleme

Önek: `/api/v1/` — `config/ApiPaths.java`

---

## Sorun giderme

<details>
<summary><strong>Swagger 401 Unauthorized döndürüyor</strong></summary>

Swagger UI'da **Authorize** kullanın ve yapıştırın:

```
Bearer &lt;access_token&gt;
```

Token'ı frontend üzerinden giriş yaparak (Keycloak) alabilirsiniz.

</details>

<details>
<summary><strong>Keycloak yönlendirme / giriş sorunları</strong></summary>

- Keycloak'ın http://localhost:8081 adresinde erişilebilir olduğundan emin olun
- Realm/istemci için yapılandırılmış frontend giriş noktasını kullanın

</details>

<details>
<summary><strong>Denetim logları boş</strong></summary>

- Log hattının ayakta olduğunu doğrulayın (Kafka + log-consumer + OpenSearch)
- OpenSearch indekslerini kontrol edin:

```powershell
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

</details>

---

## Dokümantasyon

- [Kök README](../README.tr.md)
- [API dokümantasyonu](../docs/api/README.tr.md)
- [Hızlı uç nokta referansı](../docs/api/endpoints.tr.md)
- [E-posta kurulumu](../docs/email-setup.tr.md)
- [Güvenlik](../docs/security/README.tr.md)
