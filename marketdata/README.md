<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# marketdata

Market data microservice — ingestion + schedulers + REST API for BIST, VIOP, FX, crypto, TEFAS funds, macro (inflation/rates), bank FX rates, and eurobonds.

---

## Summary

| Item | Value |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Database** | PostgreSQL `nrs_market` |
| **Migrations** | Liquibase |
| **Docker port** | 8083 |
| **Local port** | 8086 |
| **Swagger** | http://localhost:8083/swagger-ui.html |

---

## Responsibilities

### Data ingestion

| Source | Data |
|--------|-----------|
| TCMB EVDS | Inflation, interest rates, deposits, eurobonds, debt |
| BIST / İş Yatırım | Equity daily candles |
| VIOP CSV | Futures prices (historical backfill) |
| TEFAS | Fund prices |
| dovizborsa.com | Bank FX rates |
| FinHub / Yahoo / Stooq | US equities / ETFs |
| CoinGecko | Crypto |

### REST API

- `/api/market/**` — quotes, historical data, VIOP, bonds, funds, crypto
- `/api/news/**` — financial news

### Access model

| Scope | Auth | Examples |
|-------|------|----------|
| Public GET | None (JWT optional) | `/api/market/**`, `/api/news/**`, `/api/viop/**`, `/api/debt/**`, `/api/funds/**` |
| Admin POST | JWT role ADMIN or OPS | `/api/admin/**` |
| Internal | JWT ADMIN/OPS or token header | `/internal/**`, `/internal/market/backfill/**` |

Swagger documents the full surface. See [`docs/api/endpoints.md`](../docs/api/endpoints.md).

### `ops` profile — internal backfill

Docker enables Spring profile **`docker,ops`**. `BackfillController` exposes `/internal/market/backfill/*`.

When `NRS_INTERNAL_BACKFILL_TOKEN` (Compose) / `app.internal.backfill-token` is set, require header:

```http
X-Nrs-Internal-Token: <same value>
```

Documented in Compose as `NRS_INTERNAL_BACKFILL_TOKEN` (default local-docker token). **Not in `.env.example`** — add to `.env` to override; see root README env note.

---

## Dependencies

| Component | Purpose |
|---------|------|
| PostgreSQL `nrs_market` | Price history and instrument metadata |
| Redis | Snapshot caching and rate limiting |
| Kafka | Log appender pipeline |
| Keycloak | JWT (optional for admin endpoints) |

---

## Run

### Docker Compose

```powershell
docker compose up -d market-data-service
```

On first boot, bootstrap schedulers may run EVDS backfills — make sure `EVDS_API_KEY` is set in the repo root `.env` for macro panels.

Service URL: http://localhost:8083  
Swagger UI: http://localhost:8083/swagger-ui.html

Health:

```powershell
curl http://localhost:8083/actuator/health
```

Verify data quickly (examples):

```powershell
# OpenAPI JSON (machine-readable)
curl http://localhost:8083/v3/api-docs > $null

# Public GET examples on this service (dashboard is on finance-service :8085, JWT required)
curl "http://localhost:8083/api/market/bank-rates"
curl "http://localhost:8083/api/market/macro/inflation/latest"
```

### Local (optional)

From the repository root (Postgres, Redis, Kafka must be reachable):

```powershell
mvn spring-boot:run -pl marketdata
```

Local port: **8086** (`application.yml`)

---

## Test

CI: `mvn test -pl marketdata`  
Local smoke: `curl http://localhost:8083/actuator/health`

Integration tests use Testcontainers (PostgreSQL + Redis).

---

## Troubleshooting

<details>
<summary><strong>Macro panels are empty</strong></summary>

- Ensure `EVDS_API_KEY` is set in the repo root `.env`
- Restart the service after changing env vars:

```powershell
docker compose up -d --force-recreate market-data-service
```

</details>

<details>
<summary><strong>First boot feels slow</strong></summary>

Backfills and scheduler warm-up may run on first start. Check logs:

```powershell
docker compose logs -f market-data-service
```

</details>

---

## Configuration (key env vars)

| Env | Description |
|-----|----------|
| `EVDS_API_KEY` | TCMB EVDS macro data (recommended/important for macro panels) |
| `FINHUB_API_KEY` | US equities data (optional) |
| `COINGECKO_API_KEY` | Crypto (optional) |
| `MARKET_BIST_ENABLED` | Toggle BIST ingestion |
| `TEFAS_ENABLED` | Toggle TEFAS fund ingestion |

See also: `application.yml`, `application-docker.yml`, and the repo root `.env.example`.

---

## Documentation

- [Root README](../README.md)
- [Architecture](../docs/architecture.md)
- [API](../docs/api/README.md)
- [Quick endpoint reference](../docs/api/endpoints.md)
