<p align="center">
  <img src="../assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="keycloak-bootstrap.md">English</a> · <a href="keycloak-bootstrap.tr.md">Türkçe</a></p>

---

# Keycloak bootstrap (local Docker)

This guide explains how Keycloak is provisioned in the **Docker Compose** stack. It complements [`docs/security/README.md`](../security/README.md).

---

## What happens on `docker compose up`

The `keycloak` service in [`docker-compose.yml`](../../docker-compose.yml) runs:

```text
command: start-dev --import-realm
```

On first startup (or when the Keycloak data volume is empty), Keycloak imports:

| File | Purpose |
|------|---------|
| [`infra/keycloak/import/nrs-finance-realm.json`](../../infra/keycloak/import/nrs-finance-realm.json) | Realm `nrs-finance`, clients, roles, demo users |

Compose comment reference: `docs/ops/keycloak-bootstrap.md`

---

## Realm summary

| Item | Value |
|------|-------|
| Realm name | `nrs-finance` |
| Display name | NRS Finance Portal |
| Frontend client | `nrs-frontend` |
| Backend admin / S2S client | `nrs-finance-backend` |
| Self-registration in Keycloak UI | **Disabled** (`registrationAllowed: false`) |
| Portal registration | Landing page → `finance-service` `/api/public/register` |

User registration and password reset **emails are not sent by Keycloak SMTP**. They use Gmail OAuth via `notification-service` — see [`docs/email-setup.md`](../email-setup.md).

---

## Demo accounts (realm import)

Imported users for local evaluation:

| Username | Password | Role | Notes |
|----------|----------|------|-------|
| `nrsadmin` | `123456789` | ADMIN | Admin menu, audit, user management |
| `testuser` | `123456789` | USER | Dashboard, market, portfolio, simulation |

> **Local development only.** Change or remove demo credentials before any non-local deployment.

---

## Keycloak admin console

| Item | Value |
|------|-------|
| URL | http://localhost:8081 |
| Admin user | `.env` → `KEYCLOAK_ADMIN` (default `admin`) |
| Admin password | `.env` → `KEYCLOAK_ADMIN_PASSWORD` (default `admin`) |

Use the admin console to inspect realm settings, clients, and users. Prefer portal admin APIs for day-to-day user operations in production-like demos.

---

## Volume behaviour

| Volume | Name | Effect |
|--------|------|--------|
| `keycloak_data` | `nrs_keycloak_data` | Persists Keycloak internal state between restarts |

- **First run:** realm JSON is imported into the volume.
- **Later runs:** existing volume data is reused; realm import does **not** fully re-apply on every restart (Keycloak import-on-start behaviour with existing data).
- **Full reset:** `docker compose down -v` deletes volumes including Keycloak data; next `up` re-imports the realm JSON.

---

## Production warning

The Compose stack uses **`start-dev`**, which is intended for **local development only**:

- Not hardened for production (HTTP, dev mode, simplified config)
- Realm JSON contains **demo passwords**
- `bruteForceProtected` is **off** in the imported realm (see [`docs/security/README.md`](../security/README.md))
- Keycloak **SMTP is not configured** — email flows use Gmail via `notification-service`

For production, use a supported Keycloak deployment mode, external secrets, TLS, and remove demo users.

---

## Verify Keycloak after stack start

```powershell
docker compose ps keycloak
curl.exe http://localhost:8081/realms/nrs-finance/.well-known/openid-configuration
```

Open http://localhost:8081 and confirm realm **nrs-finance** exists.

<p align="center">
  <img src="../assets/images/ops/keycloak-realm-import.png" alt="Docker Compose — Keycloak realm import log" width="820" />
</p>

---

[← Documentation hub](../README.md) · [Security →](../security/README.md) · [Email setup →](../email-setup.md)
