<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# finance-service

Core portal API — user/accounts, portfolio, simulations, admin features, price alerts, Portfolio AI, and a market proxy.

---

## Summary

| Item | Value |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Database** | PostgreSQL `nrs_finance` |
| **Migrations** | Liquibase (`src/main/resources/db/changelog/`) |
| **Docker port** | 8085 → container 8080 |
| **Local port** | 8080 (default) |
| **Swagger** | http://localhost:8085/swagger-ui.html |

---

## Responsibilities

- Registration and user profile management (public + authenticated APIs)
- Portfolio management (manual positions; futures/bond analysis where enabled)
- Investment simulations
- Price alert evaluation and notification triggering
- Portfolio AI analysis (OpenAI — optional)
- Admin features: user management, audit log queries, observability admin endpoints
- Keycloak admin integrations (roles, OTP policy, user lifecycle)
- Market terminal proxy delegating to `marketdata`

---

## Dependencies

| Component | Purpose |
|---------|------|
| PostgreSQL | Persistent storage |
| Redis | Rate limiting, TOTP secret storage, caching |
| Keycloak | JWT issuer + admin API |
| marketdata | Market prices and historical data |
| notification-service | Notifications and email |
| Kafka | Logging pipeline transport |
| OpenSearch | Audit log search |
| Tempo / Grafana | Observability admin integrations |

---

## Run

### Docker Compose (recommended)

From the repository root:

```powershell
docker compose up -d finance-service
```

Dependencies (Postgres, Keycloak, market-data, Redis, Kafka, OpenSearch, etc.) start automatically when running the full stack.

Service URL: http://localhost:8085  
Swagger UI: http://localhost:8085/swagger-ui.html

Health:

```powershell
curl http://localhost:8085/actuator/health
```

---

## Test

- CI runs unit + integration tests (Testcontainers) for the backend modules.
- Local smoke: `curl http://localhost:8085/actuator/health`

---

## Key packages

```
com.nurseli.nrsfinanceportal/
├── api/                    # REST controller, DTO, GlobalExceptionHandler
├── application/            # Use-case services / business logic
├── domain/                 # Entities and domain models
├── infrastructure/         # JPA, Keycloak, Kafka, OpenSearch clients
└── config/                 # SecurityConfig, OpenApiConfig, RedisConfig
```

---

## Public API summary

| Controller | Base path | Purpose |
|------------|-----------|---------|
| `PublicRegistrationController` | `/api/public/register` | `request-code`, `complete` |
| `PublicLoginController` | `/api/public` | `login`, `token/refresh` |
| `PublicPasswordResetController` | `/api/public/password-reset` | `request-code`, `verify-code`, `complete` |

Registration and password-reset emails require Gmail — [`docs/email-setup.md`](../docs/email-setup.md).

### Admin suspension

| Method | Path | Controller |
|--------|------|------------|
| POST | `/api/admin/users/{userId}/suspend-login` | `AdminUserSuspensionController` |
| POST | `/api/admin/users/{userId}/unsuspend-login` | `AdminUserSuspensionController` |

See [`docs/security/README.md`](../docs/security/README.md).

---

## Environment variables (Docker)

| Variable | Description |
|----------|----------|
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `MARKET_DATA_SERVICE_URL` | marketdata base URL |
| `KEYCLOAK_ADMIN_*` | Keycloak admin client |
| `OPENAI_API_KEY` | Portfolio AI (optional) |
| `APP_OBSERVABILITY_*` | OpenSearch, Tempo, Grafana URL |

Full list: repo root `.env.example` and `application-docker.yml`

---

## API versioning

Prefix: `/api/v1/` — see `config/ApiPaths.java`

---

## Troubleshooting

<details>
<summary><strong>Swagger returns 401 Unauthorized</strong></summary>

Use **Authorize** in Swagger UI and paste:

```
Bearer &lt;access_token&gt;
```

You can obtain the token by logging in via the frontend (Keycloak).

</details>

<details>
<summary><strong>Keycloak redirect / login issues</strong></summary>

- Ensure Keycloak is reachable at http://localhost:8081
- Ensure you're using the frontend entrypoint configured for the realm/client

</details>

<details>
<summary><strong>Audit logs are empty</strong></summary>

- Confirm the log pipeline is up (Kafka + log-consumer + OpenSearch)
- Check OpenSearch indices:

```powershell
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

</details>

---

## Documentation

- [Root README](../README.md)
- [API docs](../docs/api/README.md)
- [Quick endpoint reference](../docs/api/endpoints.md)
- [Email setup (registration / reset mail)](../docs/email-setup.md)
- [Security](../docs/security/README.md)
