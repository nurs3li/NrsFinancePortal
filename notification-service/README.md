# notification-service

Bildirim ve e-posta mikroservisi — Kafka event tüketimi, in-app bildirim, Gmail OAuth e-posta.

---

## Özet

| Özellik | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_finance` (notification tabloları) |
| **Migration** | Liquibase |
| **Docker port** | 8089 |
| **Swagger** | http://localhost:8089/swagger-ui.html |

---

## Sorumluluklar

- Kafka'dan bildirim event'lerini tüketir
- Kullanıcıya in-app bildirim kaydeder ve listeler
- Gmail OAuth ile HTML e-posta gönderir (Portföy AI raporu, price alert vb.)
- finance-service internal API ile kullanıcı e-posta adresi çözümleme (S2S JWT)

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL | Bildirim kayıtları |
| Kafka | Event consumer |
| Keycloak | JWT + S2S token |
| finance-service | Kullanıcı bilgisi (`FINANCE_BASE_URL`) |
| Gmail API | E-posta gönderimi (opsiyonel) |

---

## Çalıştırma

### Docker Compose

```powershell
docker compose up -d notification-service
```

Port: **8089**

---

## E-posta yapılandırması (opsiyonel)

`.env`:

```env
GMAIL_CLIENT_ID=...
GMAIL_CLIENT_SECRET=...
GMAIL_REFRESH_TOKEN=...
GMAIL_FROM_ADDRESS=...
NOTIFICATION_TEST_EMAIL=...
FINANCE_BASE_URL=http://localhost:8085
KEYCLOAK_NOTIFICATION_S2S_SECRET=...
```

Gmail yapılandırması yoksa in-app bildirimler çalışır; e-posta gönderimi devre dışı kalır.

---

## Test

CI’da `mvn test -pl notification-service`. Yerel: servis health + bildirim akışı manuel doğrulama.

---

## API

- `GET /api/v1/notifications` — kullanıcı bildirimleri
- `PATCH /api/v1/notifications/{id}/read` — okundu işaretle
- Internal: `/internal/email/**` — finance-service çağrıları (S2S)

---

## Dokümantasyon

- [Kök README](../README.md)
- [API](../docs/api/README.md)
- [Güvenlik — S2S](../docs/security/README.md)
