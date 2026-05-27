# Kurulum ve doğrulama rehberi

Bu rehber, projeyi **sıfırdan** ayağa kaldırıp **her katmanı tek tek doğrulamanız** için yazılmıştır. Hızlı özet için [`README.md`](../README.md) yeterlidir.

---

## 1. Ön koşul kontrol listesi

- [ ] Windows 10/11 veya macOS / Linux
- [ ] Docker Desktop kurulu ve çalışıyor (`docker info` hata vermiyor)
- [ ] En az **8 GB RAM** ayrılmış (tüm stack için)
- [ ] Diskte **~5 GB** boş alan (image'lar + volume'lar)
- [ ] Git kurulu

Kaynak derleme ve çalıştırma **yalnızca Docker** ile yapılır; host’ta JDK/Maven/Node gerekmez.

---

## 2. Klonlama ve ortam

```powershell
git clone https://github.com/nurs3li/NrsFinancePortal.git
cd NrsFinancePortal
copy .env.example .env
```

`.env` düzenleme:

```env
POSTGRES_PASSWORD=GucluBirSifre123!
EVDS_API_KEY=...   # TCMB'den alın — makro veri için önerilir
```

---

## 3. Stack'i başlatma

```powershell
docker compose up -d --build
```

**Beklenen süre:** İlk sefer 5–15 dk (Maven build + migration).

İlerlemeyi izleme:

```powershell
docker compose logs -f --tail=100
```

---

## 4. Katman katman doğrulama

### 4.1 Altyapı

```powershell
docker compose ps
```

| Servis | Beklenen durum |
|--------|----------------|
| nrs-postgres | healthy |
| nrs-keycloak | running |
| nrs-kafka | running |
| nrs-redis | running |
| nrs-opensearch | healthy |

OpenSearch:

```powershell
curl http://localhost:9200/_cluster/health
```

Prometheus:

```powershell
curl http://localhost:9090/-/ready
```

Grafana:

```powershell
curl http://localhost:3001/api/health
```

### 4.2 Backend servisleri

```powershell
curl http://localhost:8083/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8089/actuator/health
curl http://localhost:8087/actuator/health
```

Hepsi `{"status":"UP"}` veya benzeri dönmeli.

### 4.3 Swagger UI

Tarayıcıda açın ve sayfa yüklensin:

- http://localhost:8085/swagger-ui.html
- http://localhost:8083/swagger-ui.html

### 4.4 Frontend

http://localhost:3000 — landing veya login ekranı görünmeli.

### 4.5 Keycloak

http://localhost:8081 — Keycloak welcome veya admin console.

Admin: `.env` → `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (varsayılan `admin` / `admin`).

Realm: **nrs-finance** (otomatik import).

---

## 5. Fonksiyonel smoke test

1. http://localhost:3000 → Kayıt ol veya test kullanıcı ile giriş.
2. Dashboard'a yönlendirildiğinizi doğrulayın.
3. **Piyasa** sekmesine gidin — sembol listesi yüklenmeli (EVDS key yoksa makro paneller kısıtlı olabilir).
4. **Portföy** sayfasını açın.
5. Admin rolü varsa: **Admin → Audit** — Grafana embed panelleri.

---

## 6. Log pipeline doğrulama (Madde 13)

1. Herhangi bir API'ye istek atın (Swagger veya frontend).
2. OpenSearch Dashboards: http://localhost:5601
3. Index pattern: `application-logs-*` (ilk kullanımda oluşması birkaç dakika sürebilir).

Alternatif:

```powershell
curl "http://localhost:9200/application-logs-*/_search?size=1&pretty"
```

---

## 7. Test suite

Backend testleri CI’da (`mvn test`, Testcontainers) çalışır. Yerel doğrulama için stack health kontrolleri (bölüm 4) yeterlidir.

---

## 8. Temiz kapatma

```powershell
docker compose down
```

Tam sıfırlama (veritabanı silinir):

```powershell
docker compose down -v
```

---

## Sık karşılaşılan hatalar

| Hata | Çözüm |
|------|--------|
| `port is already allocated` | `docker compose down`, çakışan portu kullanan uygulamayı kapat |
| `finance-service` unhealthy | `docker compose logs market-data-service` — market önce ayağa kalkmalı |
| Blank market macro | `EVDS_API_KEY` ekle, `docker compose up -d --build market-data-service` |
| Frontend Keycloak error | `:3000` kullanın, `:5173` değil |

---

[← Dokümantasyon hub](./README.md) · [Mimari →](./architecture.md)
