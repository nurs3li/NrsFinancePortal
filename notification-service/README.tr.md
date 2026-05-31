<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# notification-service

Bildirim ve e-posta mikroservisi — Kafka olaylarını tüketir, uygulama içi bildirimleri saklar ve isteğe bağlı olarak Gmail OAuth ile HTML e-posta gönderir.

---

## Özet

| Öğe | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_finance` (bildirim tabloları) |
| **Migrasyonlar** | Liquibase |
| **Docker portu** | 8089 |
| **Swagger** | http://localhost:8089/swagger-ui.html |

---

## Sorumluluklar

- Kafka'dan bildirim olaylarını tüketmek
- Uygulama içi gelen kutusu için bildirimleri kalıcılaştırmak ve REST ile sunmak
- İsteğe bağlı HTML e-posta göndermek (Portföy AI raporu, fiyat alarmları vb.) Gmail OAuth ile
- S2S (servisler arası) JWT ile `finance-service` dahili API üzerinden kullanıcı e-posta adreslerini çözmek

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL | Bildirim kalıcılığı |
| Kafka | Olay kaynağı |
| Keycloak | JWT doğrulama + S2S token'ları |
| finance-service | Kullanıcı/hesap verisi (`FINANCE_BASE_URL`) |
| Gmail API | E-posta iletimi (isteğe bağlı) |

---

## Çalıştırma

### Docker Compose (önerilen)

```powershell
docker compose up -d notification-service
```

Servis URL: http://localhost:8089  
Swagger UI: http://localhost:8089/swagger-ui.html

Sağlık:

```powershell
curl http://localhost:8089/actuator/health
```

---

## E-posta yapılandırması (isteğe bağlı)

Bunları depo kökü `.env` içinde tanımlayın ([`.env.example`](../.env.example)):

```env
GMAIL_CLIENT_ID=...
GMAIL_CLIENT_SECRET=...
GMAIL_REFRESH_TOKEN=...
GMAIL_FROM_ADDRESS=...
NOTIFICATION_TEST_EMAIL=...
FINANCE_BASE_URL=http://localhost:8085
KEYCLOAK_NOTIFICATION_S2S_SECRET=...
```

Gmail kimlik bilgileri verilmezse **uygulama içi bildirimler çalışmaya devam eder**, e-posta iletimi devre dışı kalır.

### Ana değişkenler

| Değişken | Zorunlu | Notlar |
|----------|----------|------|
| `FINANCE_BASE_URL` | Evet (e-posta özellikleri için) | Kullanıcı e-posta adreslerini çözmek için kullanılır |
| `KEYCLOAK_NOTIFICATION_S2S_SECRET` | Evet (S2S için) | Servisler arası erişim token'ı için istemci gizlisi |
| `GMAIL_*` | Hayır | E-posta gönderimi isteniyorsa gerekli |

---

## Test

- CI: `mvn test -pl notification-service`
- Yerel duman: `/actuator/health` kontrol edin, ardından UI'dan veya olay üreten üst servisten bildirim akışı tetikleyin.

---

## API

| Metot | Path | Not |
|-------|------|-----|
| GET | `/api/v1/notifications/me` | Sayfalı gelen kutusu |
| GET | `/api/v1/notifications/me/unread-count` | Okunmamış sayısı |
| PATCH | `/api/v1/notifications/{id}/read` | Okundu işaretle |
| POST | `/api/notifications/internal/email/send` | S2S e-posta `{ "to", "subject", "body" }` → 202 |

### Kafka

| Topic | Rol |
|-------|-----|
| `notification-events` | `NotificationEventConsumer` → uygulama içi bildirimler |

E-posta kurulumu: [`docs/email-setup.tr.md`](../docs/email-setup.tr.md)

---

## Dokümantasyon

- [Kök README](../README.tr.md)
- [API](../docs/api/README.tr.md)
- [E-posta kurulumu](../docs/email-setup.tr.md)
- [Güvenlik — S2S](../docs/security/README.tr.md)
