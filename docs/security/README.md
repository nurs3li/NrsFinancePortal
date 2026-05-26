# Güvenlik — Keycloak, JWT, 2FA

Finans Portalı **Madde 8–9** kapsamında Keycloak tabanlı kimlik doğrulama ve opsiyonel TOTP (2FA) desteği sunar.

---

## Mimari özet

```
┌──────────────┐         ┌─────────────────┐
│ React SPA    │ login   │ Keycloak 24     │
│ keycloak-js  │◄───────►│ realm:          │
└──────┬───────┘  JWT    │ nrs-finance     │
       │ Bearer          └────────┬────────┘
       ▼                          │ issuer-uri
┌──────────────┐                  │
│ Spring Boot  │◄─────────────────┘
│ Resource     │  JWT doğrulama
│ Server       │  role claims
└──────────────┘
```

---

## Keycloak yapılandırması

| Öğe | Değer |
|-----|-------|
| Docker URL | http://localhost:8081 |
| Realm | `nrs-finance` |
| Realm import | `infra/keycloak/import/nrs-finance-realm.json` |
| Frontend client | `nrs-frontend` |
| Backend client | `nrs-finance-backend` (admin / S2S) |

Compose başlangıcında realm otomatik import edilir (`start-dev --import-realm`).

### Admin console

- URL: http://localhost:8081
- Kullanıcı: `.env` → `KEYCLOAK_ADMIN` (varsayılan `admin`)
- Şifre: `.env` → `KEYCLOAK_ADMIN_PASSWORD`

---

## JWT akışı (Madde 8)

1. Kullanıcı frontend'de giriş yapar.
2. Keycloak `access_token` (JWT) döner.
3. Axios interceptor her isteğe `Authorization: Bearer <token>` ekler.
4. Backend `spring.security.oauth2.resourceserver.jwt.issuer-uri` ile token imzasını doğrular.
5. `@PreAuthorize`, `RoleProtectedRoute` ile rol kontrolü.

Roller (örnek): `USER`, `ADMIN`

Backend config: `SecurityConfig.java`  
Frontend: `auth/AuthContext.tsx`, `auth/RoleProtectedRoute.tsx`

---

## 2FA / TOTP (Madde 9)

**Davranış:** Kullanıcı **Ayarlar → İki faktörlü doğrulama** kartından TOTP'yi etkinleştirir. Zorunlu değildir; açan kullanıcı girişte OTP girer.

### Bileşenler

| Katman | Dosya / servis |
|--------|----------------|
| API | `UserTotpController` — `/api/v1/users/me/totp` |
| Servis | `UserTotpService`, `UserTotpCredentialStore` (Redis) |
| Login | `PublicLoginService` — portal TOTP veya Keycloak OTP |
| Keycloak | `KeycloakTotpLoginPolicyService`, `CONFIGURE_TOTP` action |
| Frontend | `SettingsTwoFactorCard.tsx`, `totpApi.ts` |

### Kütüphane

`dev.samstevens.totp` — TOTP generate/verify

### Ortam değişkenleri

```env
KEYCLOAK_SECURITY_OTP_ENFORCEMENT_ENABLED=false   # varsayılan: opsiyonel 2FA
KEYCLOAK_SECURITY_REQUIRED_ACTION=CONFIGURE_TOTP
```

Admin rolü için OTP enforcement ayrı yapılandırılabilir (`KEYCLOAK_SECURITY_ENFORCED_ROLES`).

---

## Remember Me & oturum süreleri

Keycloak realm SSO ayarları finance-service tarafından senkronize edilebilir:

```env
KEYCLOAK_SECURITY_REMEMBER_ME_ENFORCEMENT_ENABLED=true
KEYCLOAK_SECURITY_SSO_IDLE=PT30M
KEYCLOAK_SECURITY_SSO_MAX=PT8H
KEYCLOAK_SECURITY_ACCESS_TOKEN_LIFESPAN=PT5M
```

---

## Servisler arası (S2S) güvenlik

notification-service → finance-service internal API:

- Client credentials / S2S token
- `S2SAccessTokenService`, `KEYCLOAK_NOTIFICATION_S2S_SECRET`

---

## Public endpoint'ler

Kayıt ve login JWT gerektirmez:

- `PublicRegistrationController`
- `PublicLoginController`

Rate limiting: `RateLimitFilter` (Redis tabanlı)

---

## Güvenlik checklist (değerlendirme)

- [ ] Keycloak :8081 erişilebilir
- [ ] Login → JWT ile korumalı endpoint 200
- [ ] JWT olmadan `/api/v1/users/me` → 401
- [ ] Admin sayfası USER rolü ile → 403
- [ ] 2FA: Ayarlardan etkinleştir → sonraki login'de OTP
- [ ] Swagger Authorize ile JWT test

---

[← Dokümantasyon hub](../README.md)
