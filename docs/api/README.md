<p align="center">
  <img src="../../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# REST API documentation

NRS Finance Portal APIs are documented using **OpenAPI 3** via **SpringDoc**. Interactive exploration and testing are available through **Swagger UI** for each service.

---

## Swagger UI (interactive)

| Service | Docker URL | Swagger UI |
|--------|------------|------------|
| finance-service | http://localhost:8085 | http://localhost:8085/swagger-ui.html |
| marketdata | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| notification-service | http://localhost:8089 | http://localhost:8089/swagger-ui.html |
| log-consumer-service | http://localhost:8087 | http://localhost:8087/swagger-ui.html |

### OpenAPI JSON (machine-readable)

| Service | Endpoint |
|--------|----------|
| finance-service | http://localhost:8085/v3/api-docs |
| marketdata | http://localhost:8083/v3/api-docs |
| notification-service | http://localhost:8089/v3/api-docs |
| log-consumer-service | http://localhost:8087/v3/api-docs |

---

## API versioning

Standard prefix: **`/api/v1/`**

Examples:

```
GET /api/v1/users/me
GET /api/v1/portfolio/unified
GET /api/v1/market/dashboard
```

For backward compatibility, some endpoints support **dual paths**:

```
/api/v1/users/me/totp
/api/users/me/totp          ← legacy
```

Frontend: `frontend/src/api/apiVersion.ts` — `VITE_API_VERSION=v1`

Backend: `ApiPaths.java` — `V1_PREFIX`, `v1WithLegacy()`

---

## Response envelope

All public `/api/**` endpoints return a consistent envelope (excluding `/internal/**`):

```json
{
  "success": true,
  "data": {
    "example": "..."
  },
  "errors": null,
  "meta": null
}
```

Error example:

```json
{
  "success": false,
  "data": null,
  "errors": {
    "code": "BAD_REQUEST",
    "message": "symbol: must not be blank",
    "timestamp": "2026-05-28T09:15:00.123456789Z",
    "error": "symbol: must not be blank",
    "path": "/api/v1/portfolio/manual/me",
    "correlationId": "abc-123"
  },
  "meta": null
}
```

Key classes: `ApiResponse` / `ApiEnvelope`, `ApiErrorBody`, `ApiErrorCode`, `GlobalExceptionHandler`, `ApiResponseEnvelopeAdvice`, `ApiSecurityErrorHandler`

---

## Authentication

In Swagger UI, click **Authorize** and paste:

```
Bearer <access_token>
```

How to obtain a token:

1. Sign in via the frontend (Keycloak), or
2. Use the Keycloak token endpoint (password grant — dev/test only)

Public endpoints (registration/login) do not require JWT — see the permit list in `SecurityConfig`.

### Quick cURL smoke checks

```powershell
# Health
curl http://localhost:8085/actuator/health

# OpenAPI docs
curl http://localhost:8085/v3/api-docs > $null
curl http://localhost:8083/v3/api-docs > $null
```

---

## API catalog

Source of truth:

- SpringDoc `/v3/api-docs` for each service
- Controller definitions in the relevant service module

Quick endpoint reference: **[endpoints.md](./endpoints.md)** · [TR](./endpoints.tr.md) — public auth, admin suspend, marketdata access model, notification internal email path.

---

## Recommended API groups (demo path)

If you want to demo the platform quickly, start with these groups:

| Group | Example path | Service |
|------|------------|--------|
| Auth / user | `/api/v1/users/me`, `/api/public/register`, `/api/public/password-reset/**` | finance |
| Portfolio | `/api/v1/portfolio/**` | finance |
| Market dashboard / terminal | `/api/market/dashboard`, `/api/market/terminal/**` | finance (JWT) |
| Market data (public GET) | `/api/market/bank-rates/**`, `/api/market/macro/**`, `/api/news/**` | marketdata |
| Notifications | `/api/v1/notifications/**` | notification |
| Admin | `/api/admin/**` | finance |
| Health | `/actuator/health` | all services |

---

## Local development ports (without Docker)

| Service | Port | OpenAPI |
|--------|------|---------|
| finance-service | 8080 | http://localhost:8080/v3/api-docs |
| marketdata | 8086 | http://localhost:8086/v3/api-docs |
| notification-service | 8089 | http://localhost:8089/v3/api-docs |
| log-consumer-service | 8090 | http://localhost:8090/v3/api-docs |

---

[← Documentation hub](../README.md)
