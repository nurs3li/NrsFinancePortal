<p align="center">
  <img src="assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center">
  <a href="https://app.nrs-financeportal.com/">
    <img alt="NRS Finans Portalı — canlı ortam" src="https://img.shields.io/badge/🚀_NRS_Finans_Portalı-CANLIDA_INCELE-22c55e?style=for-the-badge&labelColor=0f172a" />
  </a>
</p>

<p align="center">
  <a href="https://app.nrs-financeportal.com/"><strong>▶ NRS FİNANS PORTALI — CANLIDA İNCELE</strong></a>
  <br />
  <sub><a href="https://app.nrs-financeportal.com/">app.nrs-financeportal.com</a></sub>
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="getting-started.md">English</a> · <a href="getting-started.tr.md">Türkçe</a></p>

---

# Başlarken (kurulum ve doğrulama)

Bu rehber, platformu **sıfırdan** ayağa kaldırmanız ve yığını **katman katman** doğrulamanız içindir. Hızlı özet ve kısa yol için kök [`README.tr.md`](../README.tr.md) dosyasına bakın.

---

## 1. Ön koşul kontrol listesi

- [ ] Windows 10/11, macOS veya Linux
- [ ] Docker Desktop kurulu ve çalışıyor (`docker info` başarılı)
- [ ] Tam yığın için en az **8 GB RAM**
- [ ] ~**5 GB** boş disk alanı (image'lar + volume'lar)
- [ ] Git kurulu

Derleme ve çalıştırma **Docker-first**'tir; standart akış için host'ta JDK/Maven/Node gerekmez.

---

## 2. Klonlama ve ortam

```powershell
git clone https://github.com/nurs3li/NrsFinancePortal.git
cd NrsFinancePortal
copy .env.example .env
```

```bash
cp .env.example .env
```

<p align="center">
  <img src="assets/gifs/getting-started/step-01-clone.gif" alt="Depoyu klonlama" width="820" />
</p>

`.env` dosyasını düzenleyin. Referans: [`.env.example`](../.env.example)

| Öncelik | Değişkenler | Not |
|---------|-------------|-----|
| **Zorunlu** | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Örnek dosyada varsayılanlar var — genelde yalnızca **şifreyi** değiştirin; DB/kullanıcı değerlerini bozmayın |
| **Keycloak admin** | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Varsayılan: `admin` / `admin` (Keycloak console :8081) |
| **Önerilen** | `EVDS_API_KEY` | Makro/enflasyon/faiz panelleri — anahtar: [TCMB EVDS](https://evds3.tcmb.gov.tr/) |
| **Opsiyonel** | `FINHUB_API_KEY` | ABD hisse / ETF verisi |
| **Opsiyonel** | `OPENAI_API_KEY`, `OPENAI_MODEL` | Portföy AI analizi |
| **Opsiyonel** | `GMAIL_*`, `NOTIFICATION_TEST_EMAIL` | E-posta bildirimleri — kurulum: [`email-setup.tr.md`](./email-setup.tr.md) |
| **Opsiyonel** | `COMPOSE_PROFILES` | `production` (nginx, varsayılan) veya `dev` (Vite HMR) |
| **Opsiyonel** | `VITE_*` | Frontend URL'leri — Docker varsayılanları genelde yeterli |
| **Opsiyonel** | `APP_LOG_KAFKA_MIN_LEVEL` | Varsayılan `INFO`; OpenSearch hacmini azaltmak için `WARN` |

`EVDS_API_KEY` ekledikten veya değiştirdikten sonra market-data'yı yeniden başlatın:

```powershell
docker compose up -d --build market-data-service
```

<p align="center">
  <img src="assets/gifs/getting-started/step-02-env.gif" alt=".env oluşturma ve düzenleme" width="820" />
</p>

---

## 3. Yığını başlatma

```powershell
docker compose up -d --build
```

`.env` dosyasında `COMPOSE_PROFILES=production` olmalı ([`.env.example`](../.env.example)) — nginx frontend 3000 portunda açılır. Vite HMR için `COMPOSE_PROFILES=dev` veya `docker compose --profile dev up -d`; aynı anda yalnızca bir profil.

**Beklenen süre:** ilk çalıştırma genelde 5–15 dakika (Maven build + migration + Keycloak realm import). İlk nginx frontend imaj derlemesi ek 1–3 dakika sürebilir.

İlerlemeyi izleyin:

```powershell
docker compose logs -f --tail=100
```

### Başlangıç sırası (finance neden geç ayağa kalkıyor?)

```
postgres, redis, kafka, keycloak, opensearch          (temel)
  → market-data-service                               (healthcheck)
    → finance-service
  → notification-service, log-consumer-service, frontend (production) veya frontend-dev (dev profili)   (paralel)
  → otel-collector, prometheus, grafana, tempo
```

İlk açılışta `market-data-service` 5–10 dakika **starting** kalabilir. `finance-service` market-data sağlığına bağlıdır — finance sürekli yeniden başlıyorsa önce market-data loglarına bakın.

<p align="center">
  <img src="assets/gifs/getting-started/step-03-compose-up.gif" alt="docker compose up --build" width="820" />
</p>

---

## 4. Katman katman doğrulama

> **Windows curl:** PowerShell'de `curl`, `Invoke-WebRequest` alias'ıdır. Aşağıda `curl.exe` veya `Invoke-RestMethod` kullanın.

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
curl.exe http://localhost:9200/_cluster/health
```

Prometheus:

```powershell
curl.exe http://localhost:9090/-/ready
```

Grafana:

```powershell
curl.exe http://localhost:3001/api/health
```

### 4.2 Backend servisleri

```powershell
curl.exe http://localhost:8083/actuator/health
curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8089/actuator/health
curl.exe http://localhost:8087/actuator/health
```

Hepsi `{"status":"UP"}` (veya eşdeğeri) dönmeli.

<p align="center">
  <img src="assets/gifs/getting-started/step-04-verify.gif" alt="docker compose ps ve health check ile doğrulama" width="820" />
</p>

### 4.3 Swagger UI

Tarayıcıda açın ve sayfaların yüklendiğini doğrulayın:

- http://localhost:8085/swagger-ui.html
- http://localhost:8083/swagger-ui.html

Kimlik gerektiren çağrılar için: http://localhost:3000 girişinden sonra **Authorize** → `Bearer <access_token>`.

### 4.4 Frontend

http://localhost:3000 — landing ve/veya giriş ekranı görünmeli. Her zaman **3000 portu** kullanın (Keycloak redirect URI'leri buna göre ayarlı).

<p align="center">
  <img src="assets/gifs/getting-started/step-05-open-app.gif" alt="Uygulamayı açma ve giriş" width="820" />
</p>

### 4.5 Keycloak

http://localhost:8081 — Keycloak karşılama sayfası veya admin console.

Admin: `.env` → `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (varsayılan: `admin` / `admin`).

Realm: **nrs-finance** (otomatik import).

### 4.6 Kimlik doğrulama ve demo hesapları

| Hesap | Şifre | Rol | Test edilecekler |
|-------|-------|-----|------------------|
| `nrsadmin` | `123456789` | ADMIN | Admin menüsü, kullanıcı yönetimi, audit / Grafana gömü |
| `testuser` | `123456789` | USER | Dashboard, piyasa, portföy, simülasyon |

**Yeni kullanıcı kaydı:** Keycloak realm'de `registrationAllowed: false`. http://localhost:3000 → **Kayıt** — portal akışı e-posta doğrulama kodu gönderir; backend: `finance-service` `/api/public/register`.

**Swagger token:** Frontend'den giriş yapın, ardından Swagger UI'da **Authorize** → `Bearer <access_token>`.

> Demo şifreler yalnızca **yerel geliştirme** içindir.

---

## 5. Fonksiyonel smoke test

| # | Adım | Beklenen |
|---|------|----------|
| 1 | http://localhost:3000 — `testuser` / `123456789` ile giriş | Dashboard açılır |
| 2 | **Piyasa** → Döviz veya Hisse sekmesi | Fiyat / liste gelir |
| 3 | **Piyasa** → Makro sekmesi | Enflasyon/faiz verisi *(`EVDS_API_KEY` yoksa boş olabilir — normal)* |
| 4 | **Portföy** → manuel pozisyon ekle | Kayıt listede görünür |
| 5 | **Simülasyon** → tarih seç, çalıştır | Sonuç ekranı |
| 6 | **Bildirimler** | Sayfa açılır |
| 7 | `nrsadmin` ile giriş → **Admin → Users** | Kullanıcı listesi |
| 8 | **Admin → Audit** | Grafana paneli gömülür |
| 9 | (`GMAIL_*` doluysa) Landing **Kayıt** | request-code → complete → giriş — [`email-setup.tr.md`](./email-setup.tr.md) |
| 10 | (`GMAIL_*` doluysa) Giriş sekmesi **Şifremi unuttum** | request-code → verify-code → complete |
| 11 | (Opsiyonel) **Ayarlar → 2FA** | TOTP etkin → sonraki girişte OTP |
| 12 | (Opsiyonel, `OPENAI_API_KEY`) **Portföy AI** | Analiz raporu üret |

<p align="center">
  <img src="assets/gifs/features/market-terminal-browse.gif" alt="Piyasa terminali" width="720" />
</p>

<p align="center">
  <img src="assets/gifs/features/simulation-run.gif" alt="Yatırım simülasyonu" width="720" />
</p>

<p align="center">
  <img src="assets/gifs/features/registration-email-flow.gif" alt="Kayıt akışı" width="720" />
</p>

<p align="center">
  <img src="assets/gifs/features/password-reset-flow.gif" alt="Şifre sıfırlama akışı" width="720" />
</p>

<p align="center">
  <img src="assets/gifs/features/settings-totp-enable.gif" alt="Ayarlar — 2FA etkinleştirme" width="720" />
</p>

<p align="center">
  <img src="assets/gifs/features/portfolio-ai-report.gif" alt="Portföy AI raporu" width="720" />
</p>

---

## 6. Log pipeline doğrulama (Kafka → OpenSearch)

1. Trafik üretin (portalda gezinin veya Swagger'dan birkaç endpoint çağırın).
2. OpenSearch Dashboards: http://localhost:5601
3. **Stack Management → Index Patterns** → `application-logs-*` (time field: `timestamp`)
4. **Discover** → zaman aralığı **Last 24 hours**
5. Filtre: `message:"[REQUEST]"` — finance/market/notification servislerinden access log görmelisiniz.

Env/compose: `APP_LOG_KAFKA_MIN_LEVEL=INFO` (varsayılan). Yalnızca uyarı/hata için `WARN` yapın.

Alternatif:

```powershell
curl.exe "http://localhost:9200/application-logs-*/_search?size=5&pretty"
```

Örnek Discover sorguları: `level:INFO AND message:"[REQUEST]"`, `serviceName:"finance-service" AND level:ERROR`

---

## 7. Test suite

CI bunları her push/PR'da çalıştırır. Yerel çalıştırma için **Docker Desktop açık** olmalıdır (Testcontainers). İlk çalıştırma image pull nedeniyle uzun sürebilir.

### Backend

```powershell
mvn test -pl finance-service -am
mvn test -pl marketdata -am
mvn test -pl notification-service -am
mvn test -pl log-consumer-service -am
```

Beklenen: **BUILD SUCCESS**, testler yeşil.

### Frontend

```powershell
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Beklenen: lint geçer, testler yeşil, production build başarılı.

İsteğe bağlı Javadoc (yerel JDK gerekmez):

```powershell
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q javadoc:javadoc -DskipTests
```

---

## 8. Temiz kapatma

```powershell
docker compose down
```

Tam sıfırlama (DB volume/verisi silinir):

```powershell
docker compose down -v
```

<p align="center">
  <img src="assets/gifs/getting-started/step-06-stop.gif" alt="docker compose down ile yığını durdurma" width="820" />
</p>

---

## Başarı kriterleri (değerlendirme kontrol listesi)

- [ ] `docker compose ps` — kritik servisler **running** / **healthy**
- [ ] 4 backend `/actuator/health` **UP**
- [ ] http://localhost:3000 — giriş + **Dashboard**
- [ ] En az bir **Swagger UI** açılır (8085 veya 8083)
- [ ] http://localhost:3001 — **Grafana** açılır
- [ ] (Opsiyonel) OpenSearch Dashboards → `application-logs-*` indeksi ve son loglar

---

## Sık karşılaşılan sorunlar

| Sorun | Çözüm |
|------|--------|
| `port is already allocated` | `docker compose down`, ardından çakışan süreci durdurun veya port mapping değiştirin |
| Port **5432** meşgul | Yerel PostgreSQL'i kapatın veya compose port eşlemesini değiştirin |
| `finance-service` unhealthy / restarting | `docker compose logs market-data-service` — market servisi önce healthy olmalı |
| Giriş yapamıyorum | Demo: `testuser` / `123456789` veya landing'den **Kayıt** akışı |
| **Giriş başarısız** (`production` profiline geçince) | `docker compose ps`: `finance-service` **Up (healthy)** olmalı; `nrs-frontend-dev` hâlâ 3000’deyse `docker stop nrs-frontend-dev` → `docker compose up -d frontend`; ardından `curl http://localhost:8085/actuator/health` |
| Makro paneller boş | `EVDS_API_KEY` tanımlayın, sonra `docker compose up -d --build market-data-service` |
| Swagger **401** | Frontend girişinden sonra **Authorize** → `Bearer <access_token>` |
| Frontend/Keycloak redirect hatası | http://localhost:3000 kullanın (`:5173` değil) |
| 3000’de UI yok | `.env` içinde `COMPOSE_PROFILES=production` veya `dev` (ikisi birden değil); `docker compose ps` → `nrs-frontend` veya `nrs-frontend-dev` |
| `frontend` (nginx) ilk build yavaş | İmaj içinde `npm run build` — bitince http://localhost:3000 |
| `frontend-dev` ilk açılışta yavaş (dev profili) | Konteyner içinde ilk `npm ci` çalışır — Vite sunucusunun bitmesini bekleyin |
| Kayıt / şifre sıfırlama maili gitmiyor | Gmail OAuth — [`email-setup.tr.md`](./email-setup.tr.md); `notification-service` health |
| "Şifremi unuttum" sayfası yok | Akış landing **Giriş** sekmesinde, ayrı route değil |
| "Hesap askıya alındı" mesajı | Admin suspend — `unsuspend-login` veya başka hesap |

İlgili: [`email-setup.tr.md`](./email-setup.tr.md) · [`ops/keycloak-bootstrap.tr.md`](./ops/keycloak-bootstrap.tr.md)

---

[← Dokümantasyon merkezi](./README.tr.md) · [Mimari →](./architecture.tr.md)
