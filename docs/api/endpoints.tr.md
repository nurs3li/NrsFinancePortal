<p align="center">
  <img src="../assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="endpoints.md">English</a> · <a href="endpoints.tr.md">Türkçe</a></p>

---

# API uç nokta hızlı referansı

**Kaynak doğruluk:** SpringDoc `/v3/api-docs` ve controller kaynak kodu. Bu dosya inceleyiciler için **hızlı referans**tır.

Swagger UI: [`docs/api/README.tr.md`](./README.tr.md)

---

## finance-service — public (JWT yok)

Taban: `http://localhost:8085` · Önek: `/api/v1/public/...` ve legacy `/api/public/...`

### Kayıt — `PublicRegistrationController`

| Metot | Path | Gövde (JSON) | Yanıt `data` |
|-------|------|--------------|--------------|
| POST | `/api/public/register/request-code` | `{ "email": "user@example.com" }` | Başarı mesajı |
| POST | `/api/public/register/complete` | `{ "email", "username", "firstName", "lastName", "password", "code" }` | Başarı mesajı |

E-posta için Gmail OAuth gerekir — [`docs/email-setup.tr.md`](../email-setup.tr.md).

### Giriş — `PublicLoginController`

| Metot | Path | Gövde | Not |
|-------|------|-------|-----|
| POST | `/api/public/login` | `{ "usernameOrEmail", "password", "otp", "rememberMe" }` | Token veya `otpRequired: true` |
| POST | `/api/public/token/refresh` | `{ "refreshToken" }` | Yeni token çifti |

### Şifre sıfırlama — `PublicPasswordResetController`

| Metot | Path | Gövde | Adım |
|-------|------|-------|------|
| POST | `/api/public/password-reset/request-code` | `{ "email" }` | 1 — kod gönder |
| POST | `/api/public/password-reset/verify-code` | `{ "email", "code" }` | 2 — kod doğrula |
| POST | `/api/public/password-reset/complete` | `{ "email", "newPassword", "confirmPassword" }` | 3 — Keycloak şifresi |

Landing UI: Giriş sekmesi → **Şifremi unuttum** (ayrı route yok).

---

## finance-service — kimlik doğrulamalı kullanıcı

Taban: `/api/v1/users/me/...` (legacy `/api/users/me/...`) · JWT gerekli

| Metot | Path | Controller | Amaç |
|-------|------|------------|------|
| PATCH | `/api/users/me/username` | `UserProfileController` | Kullanıcı adı |
| PATCH | `/api/users/me/profile` | `UserProfileController` | Ad/soyad |
| POST | `/api/users/me/email/request-code` | `UserProfileController` | E-posta değişikliği kodu |
| POST | `/api/users/me/email/confirm` | `UserProfileController` | Yeni e-posta onayı |
| POST | `/api/users/me/password` | `UserProfileController` | Şifre değişikliği |
| GET/POST | `/api/users/me/totp/**` | `UserTotpController` | 2FA TOTP |

---

## finance-service — admin

Taban: `/api/v1/admin/...` · Rol **ADMIN**

| Metot | Path | Controller | Amaç |
|-------|------|------------|------|
| POST | `/api/admin/users/{userId}/suspend-login` | `AdminUserSuspensionController` | Giriş askıya al (isteğe bağlı `{ "reason" }`) |
| POST | `/api/admin/users/{userId}/unsuspend-login` | `AdminUserSuspensionController` | Askıyı kaldır |
| DELETE | `/api/admin/users/{userId}` | `AdminUserSuspensionController` | Kullanıcı sil |

Askı: Keycloak `enabled=false` + `FrozenUserAccessFilter`. **ADMIN ve kendi hesabı askıya alınamaz.**

---

## marketdata — erişim modeli

Taban: `http://localhost:8083`

### Public GET (JWT yok)

`marketdata/.../SecurityConfig.java`:

| Desen | Örnekler |
|-------|----------|
| `/api/market/**` | Banka kurları, makro, kripto, döviz, geçmiş, hisse |
| `/api/news/**` | Haberler |
| `/api/funds/**`, `/api/viop/**`, `/api/debt/**` | Fon, VİOP, tahvil |

### Admin (JWT)

| Desen | Rol |
|-------|-----|
| POST `/api/admin/**` | ADMIN veya OPS |
| `/internal/**` | ADMIN veya OPS (token'lı backfill hariç ayrı kural) |

### Internal backfill (`ops` profili)

`BackfillController` · `/internal/market/backfill`

`NRS_INTERNAL_BACKFILL_TOKEN` / `app.internal.backfill-token` doluysa:

```http
X-Nrs-Internal-Token: <NRS_INTERNAL_BACKFILL_TOKEN>
```

| Metot | Path |
|-------|------|
| POST | `/internal/market/backfill/bist-daily` |
| POST | `/internal/market/backfill/crypto-history` |
| POST | `/internal/market/backfill/debt-history` |
| POST | `/internal/market/backfill/etf-history` |
| POST | `/internal/market/backfill/isyatirim-metals-usd` |
| POST | `/internal/market/backfill/tcmb` |
| POST | `/internal/market/backfill/viop-csv` |

---

## notification-service

Taban: `http://localhost:8089`

### Kullanıcı (JWT)

| Metot | Path |
|-------|------|
| GET | `/api/v1/notifications/me` |
| GET | `/api/v1/notifications/me/unread-count` |
| PATCH | `/api/v1/notifications/{id}/read` |

### Internal (S2S JWT)

| Metot | Path |
|-------|------|
| POST | `/api/notifications/internal/email/send` |

Gövde: `{ "to", "subject", "body" }` → 202 Accepted

### Kafka

| Topic | Tüketici |
|-------|----------|
| `notification-events` | `NotificationEventConsumer` |

---

## log-consumer-service

| Topic | İndeks |
|-------|--------|
| `application-logs` | `application-logs-yyyy-MM-dd` |

---

## Swagger UI

Örnek (finance-service): http://localhost:8085/swagger-ui.html · Tüm servisler: [`docs/api/README.tr.md`](./README.tr.md)

<p align="center">
  <img src="../assets/images/api/swagger-ui-finance.png" alt="finance-service Swagger UI" width="820" />
</p>

---

[← API genel bakış](./README.tr.md) · [E-posta kurulumu →](../email-setup.tr.md) · [Güvenlik →](../security/README.tr.md)
