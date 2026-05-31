<p align="center">
  <img src="../../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Security — Keycloak, JWT, 2FA (TOTP)

NRS Finance Portal uses **Keycloak-based authentication** with **JWT** for service authorization and optional **TOTP-based 2FA**.

---

## Architecture overview

<p align="center">
  <img src="../assets/images/architecture/05-security-architecture.png" alt="Security architecture — React SPA, Keycloak 24, Spring Boot Resource Server" width="820" />
</p>

**Flow:** Frontend (`keycloak-js`) ↔ Keycloak login → JWT → API calls with `Bearer` token → Spring Boot validates via `issuer-uri` and role claims.

---

## Keycloak configuration

| Item | Value |
|-----|-------|
| Docker URL | http://localhost:8081 |
| Realm | `nrs-finance` |
| Realm import | `infra/keycloak/import/nrs-finance-realm.json` |
| Frontend client | `nrs-frontend` |
| Backend client | `nrs-finance-backend` (admin / S2S) |

The realm is automatically imported on startup (`start-dev --import-realm`).

### Admin console

- URL: http://localhost:8081
- Username: `.env` → `KEYCLOAK_ADMIN` (default `admin`)
- Password: `.env` → `KEYCLOAK_ADMIN_PASSWORD`

---

## JWT flow

1. The user signs in from the frontend.
2. Keycloak returns an `access_token` (JWT).
3. The frontend attaches `Authorization: Bearer <token>` to API calls (Axios interceptor).
4. Backend services validate the token signature using `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
5. Role-based access is enforced via `@PreAuthorize` and frontend route guards.

Example roles: `USER`, `ADMIN`

Backend: `SecurityConfig.java`  
Frontend: `auth/AuthContext.tsx`, `auth/RoleProtectedRoute.tsx`

---

## 2FA / TOTP

**Behavior:** Users enable TOTP from **Settings → Two-factor authentication**. It is optional by default; once enabled, login requires OTP verification.

### Components

| Layer | File / service |
|--------|----------------|
| API | `UserTotpController` — `/api/v1/users/me/totp` |
| Service | `UserTotpService`, `UserTotpCredentialStore` (Redis) |
| Login | `PublicLoginService` — portal TOTP or Keycloak OTP |
| Keycloak | `KeycloakTotpLoginPolicyService`, `CONFIGURE_TOTP` action |
| Frontend | `SettingsTwoFactorCard.tsx`, `totpApi.ts` |

### Library

`dev.samstevens.totp` — TOTP generation/verification

### Environment variables

```env
KEYCLOAK_SECURITY_OTP_ENFORCEMENT_ENABLED=false   # default: optional 2FA
KEYCLOAK_SECURITY_REQUIRED_ACTION=CONFIGURE_TOTP
```

OTP enforcement can be configured per role (see `KEYCLOAK_SECURITY_ENFORCED_ROLES`).

---

## Remember-me & session lifetimes

Keycloak realm SSO settings can be synchronized by `finance-service`:

```env
KEYCLOAK_SECURITY_REMEMBER_ME_ENFORCEMENT_ENABLED=true
KEYCLOAK_SECURITY_SSO_IDLE=PT30M
KEYCLOAK_SECURITY_SSO_MAX=PT8H
KEYCLOAK_SECURITY_ACCESS_TOKEN_LIFESPAN=PT5M
```

---

## Service-to-service (S2S) security

`notification-service` → `finance-service` internal API:

- Client credentials / S2S token
- `S2SAccessTokenService`, `KEYCLOAK_NOTIFICATION_S2S_SECRET`

---

## Public endpoints

Registration, login, and password reset do not require JWT:

- `PublicRegistrationController` — `/api/public/register/**`
- `PublicLoginController` — `/api/public/login`, `/api/public/token/**`
- `PublicPasswordResetController` — `/api/public/password-reset/**` (email OTP → Keycloak password update)

Password reset uses the same Gmail notification pipeline as registration (not Keycloak SMTP). Codes are stored in Redis with a 60s resend cooldown.

Rate limiting: `RateLimitFilter` (Redis-backed)

Details: [`email-setup.md`](../email-setup.md) (Gmail pipeline, not Keycloak SMTP)

---

## Admin login suspension

Admins can suspend portal login for a user (Keycloak + local DB):

| Method | Path | Effect |
|--------|------|--------|
| POST | `/api/admin/users/{userId}/suspend-login` | Sets `loginSuspended`, Keycloak `enabled=false`, optional `{ "reason" }` |
| POST | `/api/admin/users/{userId}/unsuspend-login` | Re-enables login |

Implementation:

- `AdminUserSuspensionController` — admin API
- `UserLoginSuspensionService` — business rules (**ADMIN role users cannot be suspended**; admin cannot suspend self)
- `FrozenUserAccessFilter` — returns **403** `USER_LOGIN_SUSPENDED` on authenticated requests for suspended users
- Kafka `notification-events` — user notified of suspend/unsuspend

Frontend: Admin → Users (`AdminUsersAndAccounts.tsx`); suspended login shows banner on landing (`?suspended=1`).

<p align="center">
  <img src="../assets/gifs/features/admin-suspend-user.gif" alt="Admin suspend login flow" width="720" />
</p>

---

## marketdata public read (no JWT)

For frontend market terminal, many **GET** routes under `/api/market/**`, `/api/news/**`, `/api/viop/**`, etc. are **permitAll** (see `marketdata/.../SecurityConfig.java`). Admin POST routes and `/internal/**` require JWT roles **ADMIN** or **OPS**.

---

## Production warnings (local Compose)

| Topic | Local demo behaviour |
|-------|---------------------|
| Keycloak mode | `start-dev --import-realm` — **not production** — see [`ops/keycloak-bootstrap.md`](../ops/keycloak-bootstrap.md) |
| Demo passwords | `nrsadmin` / `testuser` in realm JSON |
| Brute force | `bruteForceProtected: false` in imported realm |
| Email | Keycloak SMTP **empty** — portal uses Gmail via `notification-service` |
| Grafana | Anonymous viewer may be enabled for audit embeds (local only) |

---

## Security smoke checklist

- [ ] Keycloak is reachable at :8081
- [ ] Login → a JWT-protected endpoint returns 200
- [ ] Without JWT, `/api/v1/users/me` returns 401
- [ ] Admin page with `USER` role returns 403
- [ ] Enable 2FA in Settings → next login requires OTP
- [ ] Swagger UI “Authorize” works with `Bearer <token>`
- [ ] (If Gmail configured) Password reset: landing → Forgot password → complete flow
- [ ] (Admin) Suspend `testuser` → login blocked → unsuspend restores access

---

[← Documentation hub](../README.md)
