<p align="center">
  <img src="./docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="420" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

<h1 align="center">NRS Finance Portal</h1>

<p align="center">
  <strong>Multi-asset market intelligence, portfolio analytics, and investment simulation in one platform.</strong>
</p>

<p align="center">
  <a href="https://github.com/nurs3li/NrsFinancePortal/actions/workflows/ci.yml">
    <img alt="CI" src="https://github.com/nurs3li/NrsFinancePortal/actions/workflows/ci.yml/badge.svg" />
  </a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 3.5.5" src="https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=000000" />
  <img alt="Docker Compose" src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" />
  <img alt="Microservices" src="https://img.shields.io/badge/Architecture-Microservices-111827" />
</p>

<p align="center">
  NRS Finance Portal is a modular finance platform that unifies portfolio management, live market monitoring,
  inflation-adjusted (real) return analytics, investment simulations, price alerts, and production-grade observability
  into a single, coherent experience.
</p>

<p align="center">
  Instead of scattered market screens, manual spreadsheets, and disconnected tools, it consolidates decision support,
  monitoring, and analytics workflows to enable faster and more reliable financial decisions.
</p>

---

## Contents

1. [Quick start summary](#quick-start-summary)
2. [Product overview](#product-overview)
3. [Tech stack](#tech-stack)
4. [System architecture](#system-architecture)
5. [Getting started (Docker)](#getting-started-docker)
6. [Environment variables](#environment-variables)
7. [Services, ports, and URLs](#services-ports-and-urls)
8. [Repository layout](#repository-layout)
9. [Testing](#testing)
10. [Requirements compliance](#requirements-compliance)
11. [Documentation map](#documentation-map)
12. [Troubleshooting](#troubleshooting)

---

## Quick start summary

| | |
|---|---|
| **Requirements** | Git + Docker Desktop (**8 GB RAM** recommended) |
| **1. Clone & env** | `git clone` → `copy .env.example .env` (Windows) or `cp .env.example .env` (macOS/Linux) → set at least `POSTGRES_PASSWORD` |
| **2. Start** | `docker compose up -d --build` (first run **5–15 min**) |
| **3. Open** | http://localhost:3000 |
| **4. Demo login** | `testuser` / `123456789` (user) or `nrsadmin` / `123456789` (admin) — see [Step 5](#step-5--open-the-app) |

> Demo passwords are for **local development only**; change them in production.

---

## Product overview

### User-facing modules

| Module | What it does |
|-------|-----------|
| **Dashboard** | Portfolio overview, KPIs, quick access |
| **Market terminal** | FX, crypto, equities, funds, VIOP, bonds — live and historical data |
| **Portfolio** | Manual positions, real return, concentration analytics |
| **Simulation** | “What if I invested on date X — where would I be today?” scenarios |
| **VIOP / Bond analysis** | Position entry/close flows, P&L calculations |
| **Portfolio AI** | OpenAI-assisted portfolio analysis report (optional API key) |
| **Notifications** | Price alerts and system notifications |
| **Admin** | User management, audit logs, Grafana embed |

### Technical view

The system runs as **4 Spring Boot microservices** + **1 React SPA**, orchestrated via **Docker Compose**. Diagrams and animated overview: [`docs/architecture.md`](docs/architecture.md).

**Authentication:** Keycloak realm `nrs-finance`. The frontend authenticates with `keycloak-js`; backend services validate JWTs as OAuth2 Resource Servers.

**2FA:** TOTP is supported. Users enable it from **Settings**; it is optional by default.

---

## Tech stack

| Layer | Technologies |
|--------|-----------|
| Frontend | React 19, TypeScript, Vite 7, TanStack Query, React Router, Keycloak JS |
| Backend | Java 21, Spring Boot 3.5.5, Spring Data JPA, Spring Security (OAuth2 Resource Server) |
| Database | PostgreSQL 16, Liquibase migrations |
| Cache | Redis 7 |
| Messaging | Apache Kafka (logs + notification events) |
| Identity | Keycloak 24, JWT |
| Logging | Log4j2 → Kafka → OpenSearch |
| Observability | OpenTelemetry, Prometheus, Grafana, Tempo |
| API documentation | SpringDoc OpenAPI 3, Swagger UI |
| Containers | Docker, Docker Compose |
| CI | GitHub Actions |

---

## System architecture

Component diagrams, request flows, and Compose topology: [`docs/architecture.md`](docs/architecture.md).

---

## Getting started (Docker)

Step-by-step GIFs, smoke tests, and the full verification checklist: [`docs/getting-started.md`](docs/getting-started.md).

### Prerequisites

| Tool | Minimum version | Verify |
|---------|---------------|----------------|
| Git | 2.x | `git --version` |
| Docker Desktop | 4.x (Compose v2) | `docker compose version` |

> This repository is **Docker-first** for build, test (CI parity), and runtime. You typically do **not** need JDK/Maven/Node installed on the host unless you choose to run services outside Docker.

### Step 1 — Clone

```powershell
git clone https://github.com/nurs3li/NrsFinancePortal.git
cd NrsFinancePortal
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-01-clone.gif" alt="Getting started — step 1: clone" width="820" />
</p>

### Step 2 — Create your environment file

```powershell
copy .env.example .env
```

```bash
cp .env.example .env
```

`.env.example` already defines `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` — in most cases you only need to change the password. Do not alter the default DB/user values unless you know what you are doing.

| Priority | Variables | Notes |
|----------|-----------|-------|
| **Required** | `POSTGRES_PASSWORD` | Minimum to start the stack |
| **Recommended** | `EVDS_API_KEY` | Macro/inflation/rates panels — [TCMB EVDS](https://evds3.tcmb.gov.tr/) |
| **Optional** | `FINHUB_API_KEY`, `OPENAI_API_KEY`, `GMAIL_*` | US equities, Portfolio AI, email notifications |

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-02-env.gif" alt="Getting started — step 2: environment file" width="820" />
</p>

### Step 3 — Start the full stack

```powershell
docker compose up -d --build
```

The first build can take **5–15 minutes** (Maven builds, Liquibase migrations, Keycloak realm import, and initial warm-up).

What gets started (high level):

- **Frontend** (React SPA)
- **4 Spring Boot services**: `finance-service`, `market-data-service`, `notification-service`, `log-consumer-service`
- **Infrastructure**: Postgres, Redis, Kafka, Keycloak, OpenSearch (+ Dashboards), Prometheus, Grafana, Tempo

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-03-compose-up.gif" alt="Getting started — step 3: docker compose up" width="820" />
</p>

### Step 4 — Verify

```powershell
docker compose ps
```

Expected container names in the `NAME` column include `nrs-finance`, `nrs-market-data`, `nrs-postgres`, `nrs-keycloak`, `nrs-frontend-dev`, and others — all **running** or **healthy**.

> On first startup, `market-data-service` may show **starting** for 5–10 minutes — this is normal. `finance-service` waits for market-data to become healthy; if finance keeps restarting, check `docker compose logs market-data-service` first.

Quick health checks:

```powershell
curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8083/actuator/health
curl.exe http://localhost:8089/actuator/health
curl.exe http://localhost:8087/actuator/health
```

On Windows PowerShell, `curl` is often an alias for `Invoke-WebRequest`. Use `curl.exe` or:

```powershell
Invoke-RestMethod http://localhost:8085/actuator/health
```

Follow logs:

```powershell
docker compose logs -f finance-service
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-04-verify.gif" alt="Getting started — step 4: verify services" width="820" />
</p>

### Step 5 — Open the app

| # | URL | Expected |
|---|-----|----------|
| 1 | http://localhost:3000 | Landing / login |
| 2 | http://localhost:8081 | Keycloak (realm: `nrs-finance`) |
| 3 | http://localhost:8085/swagger-ui.html | finance-service Swagger UI |
| 4 | http://localhost:8083/swagger-ui.html | marketdata Swagger UI |
| 5 | http://localhost:3001 | Grafana (`admin` / `admin`) |
| 6 | http://localhost:5601 | OpenSearch Dashboards |
| 7 | http://localhost:8085/actuator/health | `{"status":"UP"}` |

#### Demo accounts (Keycloak realm import)

| Account | Password | Role | What to test |
|---------|----------|------|--------------|
| `nrsadmin` | `123456789` | ADMIN | Admin menu, audit logs, user management |
| `testuser` | `123456789` | USER | Dashboard, market, portfolio, simulation |

**New user registration:** Keycloak self-registration is **disabled** (`registrationAllowed: false`). Register from the landing page at http://localhost:3000 → **Register** — portal flow with email verification code via `finance-service` `/api/public/register`.

**Requires Gmail OAuth** (`GMAIL_*` in `.env`) for verification emails. Without it, use demo accounts above. Setup: [`docs/email-setup.md`](docs/email-setup.md).

**Forgot password:** On the landing **Sign in** tab → **Forgot password** (not a separate URL). Flow: email → verification code → new password via `/api/public/password-reset/*`. Also requires Gmail OAuth — same [`docs/email-setup.md`](docs/email-setup.md).

**Swagger:** Sign in at http://localhost:3000, then in Swagger UI click **Authorize** → `Bearer <access_token>` (token from browser session or Keycloak).

> Demo passwords are for **local development only**; change them before any non-local deployment.

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-05-open-app.gif" alt="Getting started — step 5: open the app" width="820" />
</p>

### Step 6 — Stop

```powershell
docker compose down
```

Remove volumes as well (warning: deletes data):

```powershell
docker compose down -v
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-06-stop.gif" alt="Getting started — step 6: stop the stack" width="820" />
</p>

---

## Environment variables

Full reference: [`.env.example`](.env.example)

### Required

| Variable | Required | Description |
|----------|---------|----------|
| `POSTGRES_DB` | Yes | Default: `nrs_finance` |
| `POSTGRES_USER` | Yes | DB username |
| `POSTGRES_PASSWORD` | Yes | DB password |

### Recommended (data & richer UI)

| Variable | Required | Description |
|----------|---------|----------|
| `EVDS_API_KEY` | No | TCMB EVDS macro data (recommended for macro panels) |
| `FINHUB_API_KEY` | No | US equities / ETF data (optional) |

### Optional (feature flags & integrations)

| Variable | Required | Description |
|----------|---------|----------|
| `OPENAI_API_KEY` | No | Enables Portfolio AI analysis (optional) |
| `GMAIL_*` | No | Enables email delivery from `notification-service` (optional) |
| `VITE_*` | No | Frontend runtime configuration (Docker defaults usually work) |

> **Note:** Some variables are set in `docker-compose.yml` or service `application-docker.yml` but not listed in `.env.example`. To override them locally, add them to `.env` (Compose passes them through). Examples: `KEYCLOAK_SECURITY_*`, `PORTFOLIO_AI_DAILY_LIMIT`, `NRS_INTERNAL_BACKFILL_TOKEN`, `MARKET_DATA_INTERNAL_BACKFILL_TOKEN`. See [`docker-compose.yml`](docker-compose.yml) and module READMEs.

Docker Compose automatically loads the `.env` file from the repo root; no additional volume mapping is required.

---

## Services, ports, and URLs

### Docker Compose (default demo stack)

| Component | Host port | Swagger / UI |
|---------|------------|--------------|
| **Frontend** | 3000 | http://localhost:3000 |
| **finance-service** | 8085 | http://localhost:8085/swagger-ui.html |
| **marketdata** | 8083 | http://localhost:8083/swagger-ui.html |
| **notification-service** | 8089 | http://localhost:8089/swagger-ui.html |
| **log-consumer-service** | 8087 | http://localhost:8087/swagger-ui.html |
| **Keycloak** | 8081 | http://localhost:8081 |
| **PostgreSQL** | 5432 | — |
| **Redis** | 6379 | — |
| **Kafka** | 9092 | — |
| **OpenSearch** | 9200 | — |
| **OpenSearch Dashboards** | 5601 | http://localhost:5601 |
| **Prometheus** | 9090 | http://localhost:9090 |
| **Grafana** | 3001 | http://localhost:3001 |
| **Tempo** | 3200 | — |

For service-specific ports, env vars, and Swagger URLs, see each **module README** (linked below). This root README covers the full-stack quick path only.

| Module | README |
|--------|--------|
| finance-service | [finance-service/README.md](finance-service/README.md) |
| marketdata | [marketdata/README.md](marketdata/README.md) |
| notification-service | [notification-service/README.md](notification-service/README.md) |
| log-consumer-service | [log-consumer-service/README.md](log-consumer-service/README.md) |
| frontend | [frontend/README.md](frontend/README.md) |

### Local development ports (without Docker)

| Service | Default port |
|--------|-----------------|
| finance-service | 8080 |
| marketdata | 8086 |
| notification-service | 8089 |
| log-consumer-service | 8090 |
| frontend (Vite) | 5173 |

---

## Repository layout

```
NrsFinancePortal/
├── README.md                    ← GitHub landing page (this file)
├── .env.example                 ← Environment template
├── docker-compose.yml           ← Full stack definition
├── pom.xml                      ← Maven parent (Java 21)
│
├── frontend/                    ← React SPA
├── finance-service/             ← Core portal API
├── marketdata/                  ← Market data service
├── notification-service/        ← Notifications & email
├── log-consumer-service/        ← Kafka → OpenSearch log indexing
│
├── infra/                       ← Keycloak, Grafana, Prometheus, OTel, Postgres init
├── docs/                        ← Technical documentation hub
├── scripts/                     ← Dev scripts (e.g., maintenance SQL)
└── .github/workflows/ci.yml     ← CI pipeline
```

Backend layering (per service):

```
src/main/java/.../
├── api/              # REST controller, DTO, GlobalExceptionHandler
├── application/      # Business logic, use-case services
├── domain/           # Entities, domain models
├── infrastructure/   # JPA, Kafka, Keycloak, external API clients
└── config/           # Spring configuration
```

---

## Testing

### Layer 1 — Automated (CI)

GitHub Actions on every push/PR: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

- 4 backend modules: `mvn test` with Testcontainers
- Frontend: ESLint, Vitest, production build
- Docker smoke build

### Layer 2 — Manual smoke (reviewer)

After the stack is up, verify core flows (full checklist in [`docs/getting-started.md` §5](docs/getting-started.md#5-functional-smoke-test)):

- [ ] Sign in at http://localhost:3000 → **Dashboard** loads
- [ ] **Market** → instrument list loads
- [ ] **Portfolio** → page opens
- [ ] (Admin) **Admin → Audit** → Grafana panel embeds

### Layer 3 — Optional local tests (developer)

Requires Docker Desktop running (Testcontainers) and JDK/Maven/Node on the host. Commands: [`docs/getting-started.md` §7](docs/getting-started.md#7-test-suite).

Optional Javadoc generation:

```powershell
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q javadoc:javadoc -DskipTests
```

### Success criteria (setup complete)

- [ ] `docker compose ps` — critical services **running** / **healthy**
- [ ] All 4 backend `/actuator/health` endpoints return **UP**
- [ ] http://localhost:3000 — login works and **Dashboard** appears
- [ ] At least one **Swagger UI** page loads (8085 or 8083)
- [ ] http://localhost:3001 — **Grafana** opens
- [ ] (Optional) OpenSearch Dashboards → `application-logs-*` index visible

---

## Requirements compliance

| Requirement | Where to verify |
|-------------|-----------------|
| **Item 21** — README & setup guide | This file + [`docs/getting-started.md`](docs/getting-started.md) |
| **Item 22** — Unit / integration tests | [Testing](#testing) + CI badge + getting-started §7 |
| **Item 14** — Docker Compose stack | [`docker-compose.yml`](docker-compose.yml) |
| **Item 15** — Microservices architecture | [System architecture](#system-architecture) + [`docs/architecture.md`](docs/architecture.md) |
| **Item 16** — REST API & Swagger | Service Swagger URLs above + [`docs/api/README.md`](docs/api/README.md) |
| **Item 17** — Authentication (Keycloak/JWT) | [Demo accounts](#step-5--open-the-app) + [`docs/security/README.md`](docs/security/README.md) |
| **Item 18** — Observability | Grafana :3001 + [`docs/observability/README.md`](docs/observability/README.md) |
| **Item 19** — Centralized logging | Kafka → OpenSearch — getting-started §6 |
| **Item 20** — CI pipeline | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) |

---

## Documentation map

| Topic | File |
|------|-------|
| **Documentation hub** | [docs/README.md](docs/README.md) |
| **Architecture deep-dive** | [docs/architecture.md](docs/architecture.md) · [TR](docs/architecture.tr.md) |
| **Step-by-step setup & verification** | [docs/getting-started.md](docs/getting-started.md) · [TR](docs/getting-started.tr.md) |
| **REST API & OpenAPI** | [docs/api/README.md](docs/api/README.md) |
| **Observability (Grafana, OTel, OpenSearch)** | [docs/observability/README.md](docs/observability/README.md) |
| **Security (Keycloak, JWT, 2FA)** | [docs/security/README.md](docs/security/README.md) |
| **Email setup (Gmail OAuth)** | [docs/email-setup.md](docs/email-setup.md) · [TR](docs/email-setup.tr.md) |
| **Keycloak bootstrap (Compose)** | [docs/ops/keycloak-bootstrap.md](docs/ops/keycloak-bootstrap.md) · [TR](docs/ops/keycloak-bootstrap.tr.md) |
| **API quick reference** | [docs/api/endpoints.md](docs/api/endpoints.md) · [TR](docs/api/endpoints.tr.md) |

---

## Troubleshooting

<details>
<summary><strong>Port 3000 or 8085 is already in use</strong></summary>

```powershell
docker compose down
netstat -ano | findstr :3000
netstat -ano | findstr :8085
```

Stop the conflicting process or change the port mapping in `docker-compose.yml`.

</details>

<details>
<summary><strong>finance-service keeps restarting</strong></summary>

It may be waiting for market-data to become healthy:

```powershell
docker compose logs market-data-service
docker compose logs finance-service
```

</details>

<details>
<summary><strong>Market / macro panels are empty</strong></summary>

Check whether `EVDS_API_KEY` is set in `.env`. Without a key, macro panels may remain empty.

</details>

<details>
<summary><strong>Keycloak redirect_uri mismatch</strong></summary>

Use the frontend at `http://localhost:3000`. If you run the UI on `:5173`, update the Keycloak client redirect URIs to include `http://localhost:5173/*`.

</details>

<details>
<summary><strong>Swagger 401 Unauthorized</strong></summary>

In Swagger UI, click **Authorize** and paste `Bearer <access_token>` (after logging in via Keycloak).

</details>

---

**Maintainer:** NRS Finance Portal team · educational/demo delivery scope
