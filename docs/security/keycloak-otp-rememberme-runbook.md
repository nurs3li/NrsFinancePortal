# Keycloak OTP ve Remember-Me Runbook

Bu runbook, Finans Portal icin OTP/2FA ve session politikasini hem otomatik (uygulama tarafindan) hem de manuel (Keycloak Console) olarak yonetmek icindir.

## 1) Mimari ve Kapsam
- Realm: `nrs-finance`
- Frontend client: `nrs-frontend` (public client)
- Admin API cagrisi yapan service-account client: `nrs-finance-backend` (confidential)
- Otomasyon modulu: `finance-service` icinde, feature flag ile ac/kapa.

## 2) Zorunlu Roller ve OTP Politikasi
- Varsayilan enforced roller: `ADMIN`, `FINANCE_MANAGER`
- OTP required action: `CONFIGURE_TOTP`
- Beklenen akis:
  1. Kullanici role sahip ve OTP configured degilse sonraki login'de TOTP setup zorlanir.
  2. TOTP setup sonrasi standart sifre + OTP ile giris devam eder.

## 3) Remember-Me ve Session Politikasi (Realm-level)
Onerilen varsayimlar:
- Remember Me: `true`
- SSO Session Idle: `PT30M`
- SSO Session Max: `PT8H`
- Access Token Lifespan: `PT5M`
- Client Session Idle: `PT30M`
- Client Session Max: `PT8H`

## 4) Otomatik Mod (Feature Flags)

`finance-service` config anahtarlari:
- `app.keycloak.security.otp-enforcement-enabled` (default: `false`)
- `app.keycloak.security.remember-me-enforcement-enabled` (default: `false`)
- `app.keycloak.security.enforced-roles` (default: `ADMIN,FINANCE_MANAGER`)
- `app.keycloak.security.required-action` (default: `CONFIGURE_TOTP`)
- `app.keycloak.security.remember-me` (default: `true`)
- `app.keycloak.security.sso-idle` (default: `PT30M`)
- `app.keycloak.security.sso-max` (default: `PT8H`)
- `app.keycloak.security.access-token-lifespan` (default: `PT5M`)
- `app.keycloak.security.client-session-idle` (default: `PT30M`)
- `app.keycloak.security.client-session-max` (default: `PT8H`)
- `app.keycloak.security.reconcile-cron` (default: `0 0 */6 * * *`)

Otomasyon tetikleme:
- Uygulama acilisinda bir kez reconcile
- Cron ile periyodik drift reconcile (flag aciksa)

Not:
- Tum enforcement flag'leri default OFF oldugu icin mevcut sistem davranisi degismez.
- Keycloak gecici ulasilamazsa uygulama startup fail etmez; sadece warning log yazar.

## 5) Manuel Fallback (Keycloak Console)

### 5.1 OTP
1. `Authentication` -> Browser flow'da OTP adimini dogrula.
2. `Authentication` -> Required Actions -> `Configure OTP` aktif olsun.
3. User bazinda gerekli ise `Users -> Required User Actions -> CONFIGURE_TOTP`.

### 5.2 Remember Me / Session
1. `Realm Settings -> Login` -> `Remember Me` ac/kapat.
2. `Realm Settings -> Sessions` ve `Tokens` ekranlarinda timeout/lifespan degerlerini ayarla.

## 6) Gerekli Yetkiler ve Secret'lar

Service-account client (`nrs-finance-backend`) roller:
- `realm-management -> manage-users`
- `realm-management -> view-users`
- `realm-management -> query-users`
- `realm-management -> view-realm`
- `realm-management -> manage-realm` (realm-level policy enforcement icin gerekli)

`finance-service` env degiskenleri:
- `KEYCLOAK_ADMIN_ENABLED=true`
- `KEYCLOAK_ADMIN_SERVER_URL` (docker icinde `http://nrs-keycloak:8080`)
- `KEYCLOAK_ADMIN_REALM=nrs-finance`
- `KEYCLOAK_ADMIN_CLIENT_ID=nrs-finance-backend`
- `KEYCLOAK_ADMIN_CLIENT_SECRET=<client secret>`

## 7) Rollout Stratejisi (Regressionsiz)
1. Once tum yeni security flag'leri `false` ile deploy et.
2. Test ortaminda sirayla ac:
   - `otp-enforcement-enabled=true`
   - sonra `remember-me-enforcement-enabled=true`
3. Loglardan reconcile sonucunu kontrol et.
4. Uretimde ayni sirayla ac.

## 8) Dogrulama Checklist
- [ ] USER login ve routing degismedi.
- [ ] ADMIN/FM kullanicilarinda OTP required action reconcile edildi.
- [ ] Realm remember-me/session degerleri reconcile edildi (flag acikken).
- [ ] Frontend token refresh ve 401 davranisi stabil.
- [ ] Enforcement flag kapatildiginda otomasyon yazma islemi yapmiyor.

## 9) Geri Donus / Incident
- Acil durumda:
  - `KEYCLOAK_SECURITY_OTP_ENFORCEMENT_ENABLED=false`
  - `KEYCLOAK_SECURITY_REMEMBER_ME_ENFORCEMENT_ENABLED=false`
- Servisi yeniden baslat, sistem eski manuel moda doner.
