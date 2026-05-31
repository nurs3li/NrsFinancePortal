<p align="center">
  <img src="assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="architecture.md">English</a> · <a href="architecture.tr.md">Türkçe</a></p>

---

# System architecture

NRS Finance Portal uses an **event-driven**, **observable**, and **identity-centric** microservice architecture.

<p align="center">
  <img src="assets/gifs/architecture-overview.gif" alt="Architecture overview" width="720" />
</p>

---

## 1. Architecture principles

| Principle | How it is applied |
|---------|----------|
| **Layered architecture** | `api` → `application` → `domain` → `infrastructure` |
| **Separate databases** | `nrs_finance` (portal) and `nrs_market` (market data) |
| **Centralized identity** | Keycloak — services act as JWT OAuth2 Resource Servers |
| **Async logging** | Log4j2 → Kafka → OpenSearch (does not block request path) |
| **Caching** | Redis — rate limiting, market snapshots, notification dedup |
| **API versioning** | `/api/v1/...` (+ backward-compatible legacy paths where needed) |

---

## 2. Component diagram

![Component diagram](./assets/images/architecture/02-component-diagram.png)

---

## 3. Service responsibilities

### finance-service

- User profile, registration (public API), admin user management
- Manual portfolio, VIOP/bond positions
- Simulation, price alerts, Portfolio AI
- Market terminal proxy (delegates to `marketdata`)
- Admin audit log queries (OpenSearch)
- Keycloak admin integrations (roles, OTP policy)

### marketdata

- Data ingestion from external providers: TCMB EVDS, BIST, VIOP CSV, TEFAS, bank FX rates, FinHub, CoinGecko, etc.
- REST API: prices, historical data, inflation, rates, eurobonds
- Scheduled jobs (scheduler) and startup backfills
- Liquibase-managed `nrs_market` schema

### notification-service

- Kafka event consumption → in-app notifications
- Email delivery via Gmail OAuth
- Service-to-service JWT (finance-service → notification internal API)

### log-consumer-service

- Consumes Kafka topic `application-logs`
- Daily indices: `application-logs-yyyy-MM-dd` → OpenSearch
- Additional consumers for transactions/events (if enabled)

### frontend

- SPA — React Router, TanStack Query
- Keycloak JS adapter — token refresh, role guards
- Grafana panel embeds (admin audit)

---

## 4. Example request flow — portfolio list

![Request flow — portfolio list](./assets/images/architecture/04-request-flow-portfolio.png)

---

## 5. Security architecture

![Security architecture — Keycloak + JWT](./assets/images/architecture/05-security-architecture.png)

Details: [security/README.md](./security/README.md)

---

## 6. Data & migrations

| Database | Migration tool | Changelog location |
|------------|-------------------|------------------|
| nrs_finance | Liquibase | `finance-service/.../db/changelog/` |
| nrs_market | Liquibase | `marketdata/.../db/changelog/` |
| notification schema | Liquibase | `notification-service/.../db/changelog/` |

`spring.jpa.hibernate.ddl-auto: none` — schemas are managed via Liquibase only.

---

## Manual portfolio — materialized read path

`finance-service` optimizes manual portfolio dashboards using a **materialized read** layer (high level):

| Concern | Component |
|---------|-----------|
| Price tree / cache warmup | `ManualPortfolioWarmupService`, `ManualPortfolioPriceTreeLoader` |
| Read APIs | `ManualPortfolioReadService` (used by `PortfolioController`) |
| Value snapshots | `PortfolioValueSnapshot`, snapshot triggers on position changes |
| Gap-fill | Incremental warmup heals missing history when new symbols are added |

This is internal to `finance-service`; the frontend calls standard `/api/v1/portfolio/**` endpoints.

<p align="center">
  <img src="assets/gifs/features/portfolio-manual-position.gif" alt="Manual portfolio — add position" width="720" />
</p>

---

## Frontend → three backend bases

The SPA talks to three APIs (see `frontend/.env` / `VITE_*`):

| Backend | Default URL | Examples |
|---------|-------------|----------|
| finance-service | http://localhost:8085 | Portfolio, auth, admin, simulation |
| marketdata | http://localhost:8083 | Market terminal, macro, news (many public GETs) |
| notification-service | http://localhost:8089 | In-app notifications inbox |

Keycloak (http://localhost:8081) handles login tokens; Grafana embeds use `VITE_GRAFANA_*`.

---

## Events & notifications (Kafka)

`finance-service` publishes to Kafka topic **`notification-events`** via `NotificationEventKafkaPublisher` (direct publish — **no transactional outbox** in this codebase). `notification-service` consumes and persists in-app notifications; optional email uses Gmail.

---

## 7. Observability

| Signal | Tooling | Access |
|--------|------|--------|
| Metrics | Prometheus + Actuator | :9090, `/actuator/prometheus` |
| Dashboards | Grafana | :3001 |
| Traces | OTel → Tempo | Grafana Tempo datasource |
| Logs | OpenSearch | :9200, Dashboards :5601 |
| Correlation | `traceId`, `spanId`, `correlationId` in JSON logs | OpenSearch filters |

Details: [observability/README.md](./observability/README.md)

---

## 8. Docker Compose topology

<p align="center">
  <img src="assets/gifs/system-architecture.gif" alt="System architecture animation" width="900" />
</p>

![Docker Compose topology](./assets/images/architecture/08-compose-topology.png)




[← Documentation hub](./README.md) · [Getting started →](./getting-started.md)
