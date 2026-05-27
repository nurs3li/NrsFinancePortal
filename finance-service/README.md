# finance-service

Ana portal API — kullanıcı, portföy, simülasyon, admin, price alert, Portföy AI ve market proxy.

---

## Özet

| Özellik | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_finance` |
| **Migration** | Liquibase (`src/main/resources/db/changelog/`) |
| **Docker port** | 8085 → container 8080 |
| **Yerel port** | 8080 (varsayılan) |
| **Swagger** | http://localhost:8085/swagger-ui.html |

---

## Sorumluluklar

- Kullanıcı kaydı, profil, starred assets
- Manuel portföy, VIOP/tahvil pozisyonları
- Yatırım simülasyonu
- Price alert değerlendirme ve bildirim tetikleme
- Portföy AI analizi (OpenAI — opsiyonel)
- Admin: kullanıcı yönetimi, audit log, observability admin API
- Keycloak admin entegrasyonu (rol, OTP policy, kullanıcı silme)
- Market terminal proxy → `marketdata` servisi

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL | Kalıcı veri |
| Redis | Rate limit, TOTP secret, cache |
| Keycloak | JWT issuer, admin API |
| marketdata | Piyasa fiyatları |
| notification-service | E-posta / bildirim |
| Kafka | Log appender |
| OpenSearch | Audit log sorguları |
| Tempo / Grafana | Observability admin |

---

## Çalıştırma

### Docker Compose (önerilen)

Repo kökünden:

```powershell
docker compose up -d finance-service
```

Bağımlılıklar (postgres, keycloak, market-data, redis, kafka, opensearch) otomatik başlar.

Tek servis (bağımlılıklarla):

```powershell
docker compose up -d finance-service
```

---

## Test

Backend testleri GitHub Actions CI’da çalışır. Yerel doğrulama: `curl http://localhost:8085/actuator/health`

---

## Önemli paketler

```
com.nurseli.nrsfinanceportal/
├── api/                    # REST controller, DTO, GlobalExceptionHandler
├── application/            # İş servisleri
├── domain/                 # Entity
├── infrastructure/         # JPA, Keycloak, Kafka, OpenSearch client
└── config/                 # SecurityConfig, OpenApiConfig, RedisConfig
```

---

## Ortam değişkenleri (Docker)

| Değişken | Açıklama |
|----------|----------|
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `MARKET_DATA_SERVICE_URL` | marketdata base URL |
| `KEYCLOAK_ADMIN_*` | Keycloak admin client |
| `OPENAI_API_KEY` | Portföy AI (opsiyonel) |
| `APP_OBSERVABILITY_*` | OpenSearch, Tempo, Grafana URL |

Tam liste: repo kökü `.env.example` ve `application-docker.yml`

---

## API versiyonlama

Prefix: `/api/v1/` — `config/ApiPaths.java`

---

## Dokümantasyon

- [Kök README](../README.md)
- [API dokümantasyonu](../docs/api/README.md)
- [Güvenlik](../docs/security/README.md)
- [Javadoc](../docs/javadoc.md)
