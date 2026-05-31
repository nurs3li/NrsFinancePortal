<p align="center">
  <img src="../../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# REST API dokümantasyonu

NRS Finance Portal API'leri **SpringDoc** ile **OpenAPI 3** kullanılarak belgelenir. Her servis için **Swagger UI** üzerinden etkileşimli keşif ve test yapılabilir.

---

## Swagger UI (etkileşimli)

| Servis | Docker URL | Swagger UI |
|--------|------------|------------|
| finance-service | http://localhost:8085 | http://localhost:8085/swagger-ui.html |
| marketdata | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| notification-service | http://localhost:8089 | http://localhost:8089/swagger-ui.html |
| log-consumer-service | http://localhost:8087 | http://localhost:8087/swagger-ui.html |

### OpenAPI JSON (makine okunur)

| Servis | Endpoint |
|--------|----------|
| finance-service | http://localhost:8085/v3/api-docs |
| marketdata | http://localhost:8083/v3/api-docs |
| notification-service | http://localhost:8089/v3/api-docs |
| log-consumer-service | http://localhost:8087/v3/api-docs |

---

## API sürümleme

Standart önek: **`/api/v1/`**

Örnekler:

```
GET /api/v1/users/me
GET /api/v1/portfolio/unified
GET /api/v1/market/dashboard
```

Geriye dönük uyumluluk için bazı endpoint'ler **çift yol** destekler:

```
/api/v1/users/me/totp
/api/users/me/totp          ← eski
```

Frontend: `frontend/src/api/apiVersion.ts` — `VITE_API_VERSION=v1`

Backend: `ApiPaths.java` — `V1_PREFIX`, `v1WithLegacy()`

---

## Yanıt zarfı (envelope)

Tüm genel `/api/**` endpoint'leri tutarlı bir zarf döndürür (`/internal/**` hariç):

```json
{
  "success": true,
  "data": {
    "example": "..."},
  "errors": null,
  "meta": null
}
```

Hata örneği:

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

Ana sınıflar: `ApiResponse` / `ApiEnvelope`, `ApiErrorBody`, `ApiErrorCode`, `GlobalExceptionHandler`, `ApiResponseEnvelopeAdvice`, `ApiSecurityErrorHandler`

---

## Kimlik doğrulama

Swagger UI'da **Authorize**'a tıklayın ve yapıştırın:

```
Bearer <access_token>
```

Token alma:

1. Frontend üzerinden giriş yapın (Keycloak), veya
2. Keycloak token endpoint'ini kullanın (password grant — yalnızca dev/test)

Kayıt/giriş gibi genel endpoint'ler JWT gerektirmez — `SecurityConfig` içindeki izin listesine bakın.

### Hızlı cURL duman testleri

```powershell
# Sağlık
curl http://localhost:8085/actuator/health

# OpenAPI dokümanları
curl http://localhost:8085/v3/api-docs > $null
curl http://localhost:8083/v3/api-docs > $null
```

---

## API kataloğu

Kaynak doğruluk: SpringDoc `/v3/api-docs` ve controller kaynak kodu.

Hızlı uç nokta referansı: **[endpoints.tr.md](./endpoints.tr.md)** — public auth, admin suspend, marketdata erişim modeli, notification internal e-posta yolu.

---

## Önerilen API grupları (demo yolu)

Platformu hızlıca göstermek istiyorsanız bu gruplarla başlayın:

| Grup | Örnek yol | Servis |
|------|------------|--------|
| Auth / kullanıcı | `/api/v1/users/me`, `/api/public/register`, `/api/public/password-reset/**` | finance |
| Portföy | `/api/v1/portfolio/**` | finance |
| Piyasa | `/api/market/**`, `/api/news/**` | marketdata |
| Banka döviz kurları | `/api/market/bank-rates/**` | marketdata |
| Bildirimler | `/api/v1/notifications/**` | notification |
| Admin | `/api/admin/**` | finance |
| Sağlık | `/actuator/health` | tüm servisler |

---

## Yerel geliştirme portları (Docker olmadan)

| Servis | Port | OpenAPI |
|--------|------|---------|
| finance-service | 8080 | http://localhost:8080/v3/api-docs |
| marketdata | 8086 | http://localhost:8086/v3/api-docs |
| notification-service | 8089 | http://localhost:8089/v3/api-docs |
| log-consumer-service | 8090 | http://localhost:8090/v3/api-docs |

---

[← Dokümantasyon merkezi](../README.tr.md)
