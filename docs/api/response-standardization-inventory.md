# API Response Standardization Inventory

Bu envanter `/api/**` endpointleri icin tek tip response envelope gecisini izlemek icin tutulur.

## Hedef Sozlesme
- Success: `{ success: true, data, errors: null, meta?: object }`
- Error: `{ success: false, data: null, errors: { code, message, path, timestamp, correlationId, details? } }`

## Servis Bazli Siniflandirma

### finance-service
- **A) Zaten envelope kullananlar**
  - Cogu `/api/**` controller'lari `ApiResponse` doner.
  - Error responses: `GlobalExceptionHandler` (`ApiResponse.error`)
- **B) Ham DTO/Map donenler (migrasyon adayi)**
  - `MarketOverviewController` gibi bazi uclar ham DTO donabilir; `ApiResponseEnvelopeAdvice` ile otomatik envelope'a alinmistir.
- **C) Istisnalar (binary/stream)**
  - (su an kayitli istisna yok)

### marketdata
- **A) Zaten envelope kullananlar**
  - Yok (baslangicta ham DTO/Map)
- **B) Ham DTO/Map donenler (migrasyon adayi)**
  - `/api/market/**`, `/api/news/**`
  - Bu endpointler `ApiEnvelopeAdvice` ile otomatik envelope'a alinmistir.
  - Error responses: `GlobalExceptionHandler` -> `ApiEnvelope.error(...)`
- **C) Istisnalar**
  - `/health/**` (API disi)
  - `/internal/**` (API disi, envelope kapsaminda degil)

## Uygulanan Mekanizma
- finance-service: `ApiResponseEnvelopeAdvice`
- marketdata: `ApiEnvelopeAdvice`
- Frontend (legacy uyum): `marketClient` / `notificationClient` response interceptor envelope payload'ini otomatik `data` alanina unwrap eder.

## Not
- Amaç endpoint davranisini bozmadan kademeli gecis yapmak.
- Binary/stream endpointler bilincli olarak envelope disinda birakilmistir.
