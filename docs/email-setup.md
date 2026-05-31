<p align="center">
  <img src="assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="email-setup.md">English</a> · <a href="email-setup.tr.md">Türkçe</a></p>

---

# Email setup (Gmail OAuth)

NRS Finance Portal sends transactional emails through **Gmail API + OAuth 2.0**. **Keycloak SMTP is not used** for registration codes, password reset, profile email change, or notification-service delivery.

---

## Architecture

```
finance-service (PublicRegistration / PasswordReset / UserProfile)
    → POST notification-service /api/notifications/internal/email/send
        → Gmail API (OAuth refresh token)
            → User inbox
```

| Flow | Trigger | finance-service | notification-service |
|------|---------|-----------------|----------------------|
| Registration code | Landing → Register | `POST /api/public/register/request-code` | Internal email send |
| Password reset code | Landing → Forgot password | `POST /api/public/password-reset/request-code` | Internal email send |
| Email change code | Settings → change email | `POST /api/users/me/email/request-code` | Internal email send |
| Portfolio AI / alerts / admin notices | Various | Kafka `notification-events` or direct internal email | Gmail send |

---

## Prerequisites

- Google account with access to [Google Cloud Console](https://console.cloud.google.com/)
- `notification-service` and `finance-service` running (full Docker stack recommended)
- `KEYCLOAK_ADMIN_*` configured (finance-service uses Keycloak admin token for S2S call to notification internal API)

---

## Step 1 — Google Cloud project

1. Create or select a GCP project.
2. Enable **Gmail API**: APIs & Services → Library → Gmail API → Enable.

---

## Step 2 — OAuth consent screen

1. APIs & Services → OAuth consent screen.
2. Choose **External** (or Internal for Workspace-only testing).
3. Fill app name, support email, developer contact.
4. Add scopes: `https://www.googleapis.com/auth/gmail.send` (and `gmail.compose` if prompted).
5. Add test users (your Gmail address) while app is in **Testing** mode.

---

## Step 3 — OAuth client credentials

1. APIs & Services → Credentials → **Create credentials** → **OAuth client ID**.
2. Application type: **Desktop app** (or Web application with redirect you control).
3. Save **Client ID** and **Client secret** → map to `.env`:

```env
GMAIL_CLIENT_ID=your-client-id.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=your-client-secret
```

---

## Step 4 — Refresh token (OAuth Playground)

1. Open [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground/).
2. Gear icon → check **Use your own OAuth credentials** → enter Client ID / Secret.
3. Step 1: select scope `https://www.googleapis.com/auth/gmail.send` → Authorize.
4. Step 2: **Exchange authorization code for tokens** → copy **Refresh token**.

```env
GMAIL_REFRESH_TOKEN=your-refresh-token
GMAIL_FROM_ADDRESS=your-sender@gmail.com
NOTIFICATION_TEST_EMAIL=optional-test-recipient@gmail.com
FINANCE_BASE_URL=http://localhost:8085
```

5. Restart affected services:

```powershell
docker compose up -d --build notification-service finance-service
```

---

## Environment variables

Defined in [`.env.example`](../../.env.example) (copy to `.env`):

| Variable | Service | Purpose |
|----------|---------|---------|
| `GMAIL_CLIENT_ID` | notification-service | OAuth client ID |
| `GMAIL_CLIENT_SECRET` | notification-service | OAuth client secret |
| `GMAIL_REFRESH_TOKEN` | notification-service | Long-lived send token |
| `GMAIL_FROM_ADDRESS` | notification-service | From address (must match authorized Gmail) |
| `NOTIFICATION_TEST_EMAIL` | notification-service | Optional redirect for test sends |
| `FINANCE_BASE_URL` | notification-service | User lookup for S2S features |
| `KEYCLOAK_NOTIFICATION_S2S_SECRET` | notification-service | Matches backend client secret for JWT |

Also required on finance-service side for internal email calls:

| Variable | Purpose |
|----------|---------|
| `KEYCLOAK_ADMIN_ENABLED` | Enable admin client (default true in Compose) |
| `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` | S2S bearer to notification internal API |

---

## Test — registration (UI)

1. Ensure Gmail vars are set and services healthy.
2. Open http://localhost:3000 → **Register**.
3. Enter email → **Send verification code**.
4. Check inbox → enter code + username/password → complete registration.
5. Sign in with new credentials.

<p align="center">
  <img src="assets/gifs/features/registration-email-flow.gif" alt="Portal registration with email verification" width="720" />
</p>

---

## Test — password reset (UI)

Forgot password is on the **landing login panel** (not a separate route):

1. http://localhost:3000 → Sign in tab → **Forgot password**.
2. Steps: email → verification code → new password.
3. Backend: `POST /api/public/password-reset/request-code` → `verify-code` → `complete`.

<p align="center">
  <img src="assets/gifs/features/password-reset-flow.gif" alt="Forgot password flow on landing page" width="720" />
</p>

---

## Test — API (curl)

Replace `http://localhost:8085` if needed. Registration:

```powershell
curl.exe -X POST http://localhost:8085/api/public/register/request-code `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"you@example.com\"}"
```

Password reset:

```powershell
curl.exe -X POST http://localhost:8085/api/public/password-reset/request-code `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"testuser@example.com\"}"
```

Expected: `{"success":true,...}` and email received (if Gmail configured).

---

## Troubleshooting

| Symptom | Check |
|---------|--------|
| "Verification mail could not be sent" | `docker compose logs notification-service`, Gmail token expiry |
| Registration works but no mail | `GMAIL_*` empty; use demo accounts `testuser` / `nrsadmin` instead |
| 401 on internal email | `KEYCLOAK_ADMIN_*` and `KEYCLOAK_NOTIFICATION_S2S_SECRET` alignment |
| OAuth "access blocked" | Add user as OAuth consent **Test user** |

See also: [`docs/getting-started.md`](./getting-started.md) Common issues.

---

## Screenshots (Gmail OAuth setup)

<p align="center">
  <img src="assets/images/email/gmail-oauth-playground.png" alt="Google OAuth Playground — refresh token" width="820" />
</p>

<p align="center">
  <img src="assets/images/email/google-cloud-gmail-api.png" alt="Google Cloud Console — Gmail API enabled" width="820" />
</p>

---

[← Documentation hub](./README.md) · [API endpoints →](./api/endpoints.md) · [Keycloak bootstrap →](./ops/keycloak-bootstrap.md)
