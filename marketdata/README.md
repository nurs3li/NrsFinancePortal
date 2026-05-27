# marketdata

Piyasa verisi mikroservisi — ingest, scheduler, REST API (BIST, VIOP, FX, kripto, TEFAS, enflasyon, banka kurları, eurobond).

---

## Özet

| Özellik | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_market` |
| **Migration** | Liquibase |
| **Docker port** | 8083 |
| **Yerel port** | 8086 |
| **Swagger** | http://localhost:8083/swagger-ui.html |

---

## Sorumluluklar

### Veri ingest

| Kaynak | Veri tipi |
|--------|-----------|
| TCMB EVDS | Enflasyon, faiz, mevduat, eurobond, borç |
| BIST / İş Yatırım | Hisse günlük mum |
| VIOP CSV | Vadeli işlem fiyatları |
| TEFAS | Fon fiyatları |
| dovizborsa.com | Banka FX kurları |
| FinHub / Yahoo / Stooq | ABD hisse, ETF |
| CoinGecko | Kripto |

### REST API

- `/api/market/**` — fiyat, tarihsel, VIOP, tahvil, fon, kripto
- `/api/news/**` — finans haberleri
- Admin/backfill endpoint'leri (internal token)

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL `nrs_market` | Fiyat geçmişi, enstrüman metadata |
| Redis | Market snapshot cache, rate limit |
| Kafka | Log appender |
| Keycloak | JWT (opsiyonel admin endpoint'ler) |

---

## Çalıştırma

### Docker Compose

```powershell
docker compose up -d market-data-service
```

İlk açılışta bootstrap scheduler'lar EVDS backfill çalıştırabilir — `EVDS_API_KEY` `.env` içinde olmalı.

### Yerel

```powershell
docker compose up -d market-data-service
```

Port: **8086** (`application.yml`)

---

## Test

CI’da `mvn test -pl marketdata`. Yerel: `curl http://localhost:8083/actuator/health`

Testcontainers: PostgreSQL + Redis integration test paketi.

---

## Önemli yapılandırma

| Env | Açıklama |
|-----|----------|
| `EVDS_API_KEY` | TCMB makro veri (kritik) |
| `FINHUB_API_KEY` | ABD hisse |
| `COINGECKO_API_KEY` | Kripto (opsiyonel) |
| `MARKET_BIST_ENABLED` | BIST ingest |
| `TEFAS_ENABLED` | TEFAS fon |
| `VIOP_BACKFILL_ENABLED` | CSV backfill |

Detay: `application.yml`, `application-docker.yml`, repo `.env.example`

---

## VIOP CSV artifact

VIOP geçmiş verisi: `artifacts/viop/` — compose volume ile mount edilir.

---

## Dokümantasyon

- [Kök README](../README.md)
- [Mimari](../docs/architecture.md)
- [API](../docs/api/README.md)
