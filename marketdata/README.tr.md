<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# marketdata

Piyasa verisi mikroservisi — BIST, VİOP, döviz, kripto, TEFAS fonları, makro (enflasyon/faiz), banka döviz kurları ve eurobondlar için veri toplama + zamanlayıcılar + REST API.

---

## Özet

| Öğe | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Veritabanı** | PostgreSQL `nrs_market` |
| **Migrasyonlar** | Liquibase |
| **Docker portu** | 8083 |
| **Yerel port** | 8086 |
| **Swagger** | http://localhost:8083/swagger-ui.html |

---

## Sorumluluklar

### Veri toplama

| Kaynak | Veri |
|--------|-----------|
| TCMB EVDS | Enflasyon, faiz oranları, mevduat, eurobondlar, borç |
| BIST / İş Yatırım | Hisse günlük mumları |
| VİOP CSV | Vadeli fiyatlar (geçmiş doldurma) |
| TEFAS | Fon fiyatları |
| dovizborsa.com | Banka döviz kurları |
| FinHub / Yahoo / Stooq | ABD hisse / ETF |
| CoinGecko | Kripto |

### REST API

- `/api/market/**` — kotasyonlar, geçmiş veri, VİOP, tahvil, fon, kripto
- `/api/news/**` — finans haberleri

### Erişim modeli

| Kapsam | Kimlik | Örnekler |
|--------|--------|----------|
| Public GET | JWT gerekmez | `/api/market/**`, `/api/news/**`, `/api/viop/**`, `/api/debt/**` |
| Admin POST | JWT ADMIN veya OPS | `/api/admin/**` |
| Internal | JWT veya token başlığı | `/internal/**`, `/internal/market/backfill/**` |

Bkz. [`docs/api/endpoints.tr.md`](../docs/api/endpoints.tr.md).

### `ops` profili — internal backfill

Docker profili **`docker,ops`**. `BackfillController` → `/internal/market/backfill/*`.

`NRS_INTERNAL_BACKFILL_TOKEN` / `app.internal.backfill-token` doluysa:

```http
X-Nrs-Internal-Token: <aynı değer>
```

`.env.example`'da yok — kök README ortam notuna bakın; Compose varsayılan token ile gelir.

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| PostgreSQL `nrs_market` | Fiyat geçmişi ve enstrüman meta verisi |
| Redis | Anlık görüntü önbelleği ve hız sınırlama |
| Kafka | Log appender hattı |
| Keycloak | JWT (admin endpoint'leri için isteğe bağlı) |

---

## Çalıştırma

### Docker Compose

```powershell
docker compose up -d market-data-service
```

İlk açılışta bootstrap zamanlayıcıları EVDS doldurmaları çalıştırabilir — makro paneller için depo kökü `.env` içinde `EVDS_API_KEY` ayarlayın.

Servis URL: http://localhost:8083  
Swagger UI: http://localhost:8083/swagger-ui.html

Sağlık:

```powershell
curl http://localhost:8083/actuator/health
```

Veriyi hızlı doğrulama (örnekler):

```powershell
# OpenAPI JSON (makine okunur)
curl http://localhost:8083/v3/api-docs > $null

# Birkaç temsili endpoint (yollar modüle göre değişebilir)
curl "http://localhost:8083/api/market/dashboard"
curl "http://localhost:8083/api/market/bank-rates"
```

### Yerel (isteğe bağlı)

```powershell
docker compose up -d market-data-service
```

Yerel port: **8086** (`application.yml`)

---

## Test

CI: `mvn test -pl marketdata`  
Yerel duman: `curl http://localhost:8083/actuator/health`

Entegrasyon testleri Testcontainers (PostgreSQL + Redis) kullanır.

---

## Sorun giderme

<details>
<summary><strong>Makro paneller boş</strong></summary>

- Depo kökü `.env` içinde `EVDS_API_KEY` ayarlı olduğundan emin olun
- Ortam değişkenlerini değiştirdikten sonra servisi yeniden başlatın:

```powershell
docker compose up -d --force-recreate market-data-service
```

</details>

<details>
<summary><strong>İlk açılış yavaş hissediliyor</strong></summary>

İlk başlangıçta doldurmalar ve zamanlayıcı ısınması çalışabilir. Logları kontrol edin:

```powershell
docker compose logs -f market-data-service
```

</details>

---

## Yapılandırma (ana ortam değişkenleri)

| Ortam | Açıklama |
|-----|----------|
| `EVDS_API_KEY` | TCMB EVDS makro verisi (makro paneller için önerilir/önemli) |
| `FINHUB_API_KEY` | ABD hisse verisi (isteğe bağlı) |
| `COINGECKO_API_KEY` | Kripto (isteğe bağlı) |
| `MARKET_BIST_ENABLED` | BIST toplamayı aç/kapat |
| `TEFAS_ENABLED` | TEFAS fon toplamayı aç/kapat |

Ayrıca: `application.yml`, `application-docker.yml` ve depo kökü `.env.example`.

---

## Dokümantasyon

- [Kök README](../README.tr.md)
- [Mimari](../docs/architecture.tr.md)
- [API](../docs/api/README.tr.md)
- [Hızlı uç nokta referansı](../docs/api/endpoints.tr.md)
