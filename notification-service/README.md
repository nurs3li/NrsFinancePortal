<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# notification-service

Notification & email microservice — consumes Kafka events, stores in-app notifications, and optionally delivers HTML emails via Gmail OAuth.

---

## Summary

| Item | Value |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Database** | PostgreSQL `nrs_finance` (notification tables) |
| **Migrations** | Liquibase |
| **Docker port** | 8089 |
| **Swagger** | http://localhost:8089/swagger-ui.html |

---

## Responsibilities

- Consume notification events from Kafka
- Persist notifications for the in-app inbox and expose them via REST
- Optionally send HTML emails (Portfolio AI report, price alerts, etc.) via Gmail OAuth
- Resolve user email addresses through `finance-service` internal API using S2S (service-to-service) JWT

---

## Dependencies

| Component | Purpose |
|---------|------|
| PostgreSQL | Notification persistence |
| Kafka | Event source |
| Keycloak | JWT validation + S2S tokens |
| finance-service | User/account data (`FINANCE_BASE_URL`) |
| Gmail API | Email delivery (optional) |

---

## Run

### Docker Compose (recommended)

```powershell
docker compose up -d notification-service
```

Service URL: http://localhost:8089  
Swagger UI: http://localhost:8089/swagger-ui.html

Health:

```powershell
curl http://localhost:8089/actuator/health
```

---

## Email configuration (optional)

Define these in the repo root `.env` (see [`.env.example`](../.env.example)):

```env
GMAIL_CLIENT_ID=...
GMAIL_CLIENT_SECRET=...
GMAIL_REFRESH_TOKEN=...
GMAIL_FROM_ADDRESS=...
NOTIFICATION_TEST_EMAIL=...
FINANCE_BASE_URL=http://localhost:8085
KEYCLOAK_NOTIFICATION_S2S_SECRET=...
```

If Gmail credentials are not provided, **in-app notifications continue to work**, while email delivery is disabled.

### Key variables

| Variable | Required | Notes |
|----------|----------|------|
| `FINANCE_BASE_URL` | Yes (for email features) | Used to resolve user email addresses |
| `KEYCLOAK_NOTIFICATION_S2S_SECRET` | Yes (for S2S) | Client secret for service-to-service access token |
| `GMAIL_*` | No | Required only if you want emails to be sent |

---

## Test

- CI: `mvn test -pl notification-service`
- Local smoke: check `/actuator/health`, then trigger a notification flow from the UI or via the upstream service that emits events.

---

## API

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/notifications/me` | Paginated inbox (`?unreadOnly=true`) |
| GET | `/api/v1/notifications/me/unread-count` | Unread count |
| PATCH | `/api/v1/notifications/{id}/read` | Mark read |
| POST | `/api/notifications/internal/email/send` | S2S direct email `{ "to", "subject", "body" }` → 202 |

### Kafka

| Topic | Role |
|-------|------|
| `notification-events` | Consumed by `NotificationEventConsumer` → in-app notifications |

Email setup (Gmail OAuth): [`docs/email-setup.md`](../docs/email-setup.md)

---

## Documentation

- [Root README](../README.md)
- [API](../docs/api/README.md)
- [Email setup (Gmail OAuth)](../docs/email-setup.md)
- [Security — S2S](../docs/security/README.md)
