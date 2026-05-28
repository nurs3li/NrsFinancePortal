# REST API dokümantasyonu

Finans Portalı REST API'leri **OpenAPI 3** (SpringDoc) ile dokümante edilir. Canlı deneme arayüzü **Swagger UI** üzerinden sunulur.

---

## Canlı Swagger UI

| Servis | Docker URL | Swagger UI |
|--------|------------|------------|
| finance-service | http://localhost:8085 | http://localhost:8085/swagger-ui.html |
| marketdata | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| notification-service | http://localhost:8089 | http://localhost:8089/swagger-ui.html |
| log-consumer-service | http://localhost:8087 | http://localhost:8087/swagger-ui.html |

### OpenAPI JSON (machine-readable)

| Servis | Endpoint |
|--------|----------|
| finance-service | http://localhost:8085/v3/api-docs |
| marketdata | http://localhost:8083/v3/api-docs |
| notification-service | http://localhost:8089/v3/api-docs |
| log-consumer-service | http://localhost:8087/v3/api-docs |

---

## API versiyonlama (Madde 18)

Standart prefix: **`/api/v1/`**

Örnek:

```
GET /api/v1/users/me
GET /api/v1/portfolio/unified
GET /api/v1/market/dashboard
```

Geriye dönük uyumluluk için bazı endpoint'ler **dual path** destekler:

```
/api/v1/users/me/totp
/api/users/me/totp          ← legacy
```

Frontend: `frontend/src/api/apiVersion.ts` — `VITE_API_VERSION=v1`

Backend: `ApiPaths.java` — `V1_PREFIX`, `v1WithLegacy()`

---

## Yanıt zarfı (envelope)

Tüm public `/api/**` endpoint'leri tutarlı envelope kullanır (`/internal/**` hariç):

```json
{
  "success": true,
  "data": { ... },
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

Sınıflar: `ApiResponse` / `ApiEnvelope`, `ApiErrorBody`, `ApiErrorCode`, `GlobalExceptionHandler`, `ApiResponseEnvelopeAdvice`, `ApiSecurityErrorHandler`

---

## Kimlik doğrulama

Swagger UI'da **Authorize** butonuna tıklayın:

```
Bearer <access_token>
```

Token alma:

1. Frontend ile Keycloak login, veya
2. Keycloak token endpoint (password grant — dev ortamı)

Public endpoint'ler (kayıt, login) JWT gerektirmez — `SecurityConfig` permit list.

---

## API katalog

Endpoint listesi: SpringDoc (`/v3/api-docs`) ve modül controller'ları; `docs/api/endpoints.md` gerektiğinde elle güncellenir.

---

## Öncelikli API grupları

Değerlendirme / demo için önerilen endpoint grupları:

| Grup | Örnek path | Servis |
|------|------------|--------|
| Auth / kullanıcı | `/api/v1/users/me`, `/api/public/register` | finance |
| Portföy | `/api/v1/portfolio/**` | finance |
| Piyasa | `/api/market/**`, `/api/news/**` | marketdata |
| Banka kurları | `/api/market/bank-rates/**` | marketdata |
| Bildirim | `/api/v1/notifications/**` | notification |
| Admin | `/api/admin/**` | finance |
| Health | `/actuator/health` | tüm servisler |

---

## Yerel geliştirme portları (Docker dışı)

| Servis | Port | OpenAPI |
|--------|------|---------|
| finance-service | 8080 | http://localhost:8080/v3/api-docs |
| marketdata | 8086 | http://localhost:8086/v3/api-docs |
| notification-service | 8089 | http://localhost:8089/v3/api-docs |
| log-consumer-service | 8090 | http://localhost:8090/v3/api-docs |

---

[← Dokümantasyon hub](../README.md)
