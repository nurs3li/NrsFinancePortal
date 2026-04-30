# Keycloak OTP ve Session Runbook

Bu dokuman, mobil kapsam disi Finans Portal icin web SSO guvenlik ayarlarini standardize eder.

## 1) Realm ve Client
- Realm: `nrs-finance`
- Web client: `nrs-frontend` (public client)
- Service client'lar: backend servisleri icin confidential client

## 2) OTP/2FA Politikasi
- En az roller: `ADMIN`, `FM`, `OPS` icin OTP zorunlu.
- Realm Authentication Flow:
  - Browser flow altina `OTP Form` adimi eklenir.
  - Bu roller icin `Required Action = CONFIGURE_TOTP`.
- Onboarding:
  1. Kullanici ilk giriste QR ile TOTP kurar.
  2. Sonraki girislerde sifre + OTP zorunlu olur.

## 3) Remember-me / Session Politikasi
- Remember-me kullaniliyorsa sadece Keycloak session seviyesinde aktif edilir.
- Onerilen varsayim:
  - SSO Session Idle: 30 dakika
  - SSO Session Max: 8 saat
  - Access Token Lifespan: 5 dakika
  - Refresh Token Lifespan: 30 dakika
- Frontend tarafinda token yenileme `updateToken(...)` ile devam eder, refresh gecersizse login'e donulur.

## 4) Frontend SSO Beklenen Davranis
- 401 durumunda tum API client'lari Keycloak login'e yonlendirir.
- Uygulama baslangicinda `check-sso` calisir.
- JWT `Authorization: Bearer <token>` header'i ile gonderilir.

## 5) Dogrulama Checklist
- [ ] Admin kullanicisi OTP setup'a zorlandi.
- [ ] OTP dogru olmadan giris yapilamiyor.
- [ ] Token suresi dolunca arka planda yenileme calisiyor.
- [ ] Refresh suresi doldugunda login ekranina yonleniyor.
- [ ] 401 davranisi finance/market/metrics/notification client'larinda tutarli.

## 6) Incident / Geri Donus
- Acil durumda OTP enforcement gecici olarak sadece `ADMIN` rolune daraltilir.
- Session suresi kisitlari degistirilecekse once test realm'inde smoke testi yapilir.
