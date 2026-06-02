<p align="center">
  <img src="./docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="420" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

<h1 align="center">NRS Finance Portal</h1>

<p align="center">
  <strong>Çok varlıklı piyasa istihbaratı, portföy analitiği ve yatırım simülasyonu tek platformda.</strong>
</p>

<p align="center">
  <a href="https://github.com/nurs3li/NrsFinancePortal/actions/workflows/ci.yml">
    <img alt="CI" src="https://github.com/nurs3li/NrsFinancePortal/actions/workflows/ci.yml/badge.svg" />
  </a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 3.5.5" src="https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=000000" />
  <img alt="Docker Compose" src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" />
  <img alt="Microservices" src="https://img.shields.io/badge/Architecture-Microservices-111827" />
</p>

<p align="center">
  NRS Finance Portal; portföy yönetimi, canlı piyasa izleme, enflasyona göre düzeltilmiş (reel) getiri analitiği,
  yatırım simülasyonları, fiyat alarmları ve üretim kalitesinde gözlemlenebilirliği tek, tutarlı bir deneyimde birleştiren modüler bir finans platformudur.
</p>

<p align="center">
  Dağınık piyasa ekranları, manuel elektronik tablolar ve birbirinden kopuk araçlar yerine; karar desteği,
  izleme ve analitik iş akışlarını bir araya getirerek daha hızlı ve güvenilir finansal kararlar almanızı sağlar.
</p>

---

## İçindekiler

1. [Hızlı başlangıç özeti](#hızlı-başlangıç-özeti)
2. [Ürün özeti](#ürün-özeti)
3. [Teknoloji yığını](#teknoloji-yığını)
4. [Sistem mimarisi](#sistem-mimarisi)
5. [Başlarken (Docker)](#başlarken-docker)
6. [Ortam değişkenleri](#ortam-değişkenleri)
7. [Servisler, portlar ve URL'ler](#servisler-portlar-ve-urller)
8. [Depo yapısı](#depo-yapısı)
9. [Test](#test)
10. [Proje isterleri uyumu](#proje-isterleri-uyumu)
11. [Dokümantasyon haritası](#dokümantasyon-haritası)
12. [Sorun giderme](#sorun-giderme)

---

## Hızlı başlangıç özeti

| | |
|---|---|
| **Gerekenler** | Git + Docker Desktop (**8 GB RAM** önerilir) |
| **1. Klon & ortam** | `git clone` → `copy .env.example .env` (Windows) veya `cp .env.example .env` (macOS/Linux) → en az `POSTGRES_PASSWORD` ayarlayın |
| **2. Başlat** | `docker compose up -d --build` (ilk sefer **5–15 dk**) |
| **3. Aç** | http://localhost:3000 |
| **4. Demo giriş** | `testuser` / `123456789` (kullanıcı) veya `nrsadmin` / `123456789` (admin) — bkz. [Adım 5](#adım-5--uygulamayı-açma) |

> Demo şifreler yalnızca **yerel geliştirme** içindir; production'da değiştirin.

---

## Ürün özeti

### Kullanıcıya dönük modüller

| Modül | Ne yapar |
|-------|-----------|
| **Gösterge paneli** | Portföy özeti, KPI'lar, hızlı erişim |
| **Piyasa terminali** | Döviz, kripto, hisse, fon, VİOP, tahvil — canlı ve geçmiş veri |
| **Portföy** | Manuel pozisyonlar, reel getiri, yoğunlaşma analitiği |
| **Simülasyon** | "X tarihinde yatırsaydım — bugün nerede olurdum?" senaryoları |
| **VİOP / Tahvil analizi** | Pozisyon aç/kapat akışları, K/Z hesapları |
| **Portföy AI** | OpenAI destekli portföy analiz raporu (isteğe bağlı API anahtarı) |
| **Bildirimler** | Fiyat alarmları ve sistem bildirimleri |
| **Admin** | Kullanıcı yönetimi, denetim logları, Grafana gömü |

### Teknik görünüm

Sistem **4 Spring Boot mikroservisi** + **1 React SPA** olarak **Docker Compose** ile çalışır. Diyagramlar ve animasyonlu özet: [`docs/architecture.tr.md`](docs/architecture.tr.md).

**Kimlik doğrulama:** Keycloak realm `nrs-finance`. Frontend `keycloak-js` ile kimlik doğrular; backend servisleri JWT'leri OAuth2 Resource Server olarak doğrular.

**2FA:** TOTP desteklenir. Kullanıcılar **Ayarlar**'dan etkinleştirir; varsayılan olarak isteğe bağlıdır.

---

## Teknoloji yığını

| Katman | Teknolojiler |
|--------|-----------|
| Frontend | React 19, TypeScript, Vite 7, TanStack Query, React Router, Keycloak JS |
| Backend | Java 21, Spring Boot 3.5.5, Spring Data JPA, Spring Security (OAuth2 Resource Server) |
| Veritabanı | PostgreSQL 16, Liquibase migrasyonları |
| Önbellek | Redis 7 |
| Mesajlaşma | Apache Kafka (loglar + bildirim olayları) |
| Kimlik | Keycloak 24, JWT |
| Loglama | Log4j2 → Kafka → OpenSearch |
| Gözlemlenebilirlik | OpenTelemetry, Prometheus, Grafana, Tempo |
| API dokümantasyonu | SpringDoc OpenAPI 3, Swagger UI |
| Konteynerler | Docker, Docker Compose |
| CI | GitHub Actions |

---

## Sistem mimarisi

Bileşen diyagramları, istek akışları ve Compose topolojisi: [`docs/architecture.tr.md`](docs/architecture.tr.md).

---

## Başlarken (Docker)

Adım adım GIF'ler, smoke test ve tam doğrulama listesi: [`docs/getting-started.tr.md`](docs/getting-started.tr.md).

### Ön koşullar

| Araç | Minimum sürüm | Doğrulama |
|---------|---------------|----------------|
| Git | 2.x | `git --version` |
| Docker Desktop | 4.x (Compose v2) | `docker compose version` |

> Bu depo derleme, test (CI uyumu) ve çalışma zamanı için **öncelikle Docker** kullanır. Servisleri Docker dışında çalıştırmayı seçmediğiniz sürece host'ta JDK/Maven/Node kurmanız genelde gerekmez.

### Adım 1 — Klonlama

```powershell
git clone https://github.com/nurs3li/NrsFinancePortal.git
cd NrsFinancePortal
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-01-clone.gif" alt="Başlarken — adım 1: klonlama" width="820" />
</p>

### Adım 2 — Ortam dosyası oluşturma

```powershell
copy .env.example .env
```

```bash
cp .env.example .env
```

`.env.example` zaten `POSTGRES_DB`, `POSTGRES_USER` ve `POSTGRES_PASSWORD` içerir — çoğu zaman yalnızca şifreyi değiştirmeniz yeterlidir. Varsayılan DB/kullanıcı değerlerini bilmeden değiştirmeyin.

| Öncelik | Değişkenler | Not |
|---------|-------------|-----|
| **Zorunlu** | `POSTGRES_PASSWORD` | Yığını başlatmak için minimum |
| **Önerilen** | `EVDS_API_KEY` | Makro/enflasyon/faiz panelleri — [TCMB EVDS](https://evds3.tcmb.gov.tr/) |
| **Opsiyonel** | `FINHUB_API_KEY`, `OPENAI_API_KEY`, `GMAIL_*` | ABD hisse, Portföy AI, e-posta bildirimleri |

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-02-env.gif" alt="Başlarken — adım 2: ortam dosyası" width="820" />
</p>

### Adım 3 — Tam yığını başlatma

```powershell
docker compose up -d --build
```

İlk derleme **5–15 dakika** sürebilir (Maven derlemeleri, Liquibase migrasyonları, Keycloak realm içe aktarma ve ilk ısınma).

Başlatılanlar (yüksek seviye):

- **Frontend** (React SPA)
- **4 Spring Boot servisi**: `finance-service`, `market-data-service`, `notification-service`, `log-consumer-service`
- **Altyapı**: Postgres, Redis, Kafka, Keycloak, OpenSearch (+ Dashboards), Prometheus, Grafana, Tempo

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-03-compose-up.gif" alt="Başlarken — adım 3: docker compose up" width="820" />
</p>

### Adım 4 — Doğrulama

```powershell
docker compose ps
```

`NAME` sütununda beklenen konteyner adları: `nrs-finance`, `nrs-market-data`, `nrs-postgres`, `nrs-keycloak`, `nrs-frontend-dev` vb. — hepsi **running** veya **healthy**.

> İlk açılışta `market-data-service` 5–10 dakika **starting** görünebilir — normal. `finance-service`, market-data sağlıklı olana kadar bekler; finance sürekli yeniden başlıyorsa önce `docker compose logs market-data-service` kontrol edin.

Hızlı sağlık kontrolleri:

```powershell
curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8083/actuator/health
curl.exe http://localhost:8089/actuator/health
curl.exe http://localhost:8087/actuator/health
```

Windows PowerShell'de `curl` genelde `Invoke-WebRequest` alias'ıdır. `curl.exe` veya:

```powershell
Invoke-RestMethod http://localhost:8085/actuator/health
```

Logları izleme:

```powershell
docker compose logs -f finance-service
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-04-verify.gif" alt="Başlarken — adım 4: servisleri doğrulama" width="820" />
</p>

### Adım 5 — Uygulamayı açma

| # | URL | Beklenen |
|---|-----|----------|
| 1 | http://localhost:3000 | Landing / giriş |
| 2 | http://localhost:8081 | Keycloak (realm: `nrs-finance`) |
| 3 | http://localhost:8085/swagger-ui.html | finance-service Swagger UI |
| 4 | http://localhost:8083/swagger-ui.html | marketdata Swagger UI |
| 5 | http://localhost:3001 | Grafana (`admin` / `admin`) |
| 6 | http://localhost:5601 | OpenSearch Dashboards |
| 7 | http://localhost:8085/actuator/health | `{"status":"UP"}` |

#### Demo hesapları (Keycloak realm import)

| Hesap | Şifre | Rol | Test edilecekler |
|-------|-------|-----|------------------|
| `nrsadmin` | `123456789` | ADMIN | Admin menüsü, audit logları, kullanıcı yönetimi |
| `testuser` | `123456789` | USER | Dashboard, piyasa, portföy, simülasyon |

**Yeni kullanıcı kaydı:** Keycloak self-registration **kapalı** (`registrationAllowed: false`). http://localhost:3000 → **Kayıt** — portal akışı, e-posta doğrulama kodu, backend: `finance-service` `/api/public/register`.

**Gmail OAuth gerekir** (`GMAIL_*` in `.env`) doğrulama e-postaları için. Yoksa yukarıdaki demo hesapları kullanın. Kurulum: [`docs/email-setup.tr.md`](docs/email-setup.tr.md).

**Şifremi unuttum:** Landing **Giriş** sekmesinde → **Şifremi unuttum** (ayrı URL yok). Akış: e-posta → doğrulama kodu → yeni şifre (`/api/public/password-reset/*`). Gmail OAuth gerekir — [`docs/email-setup.tr.md`](docs/email-setup.tr.md).

**Swagger:** http://localhost:3000 üzerinden giriş yapın, ardından Swagger UI'da **Authorize** → `Bearer <access_token>`.

> Demo şifreler yalnızca **yerel geliştirme** içindir; yerel olmayan ortamlarda değiştirin.

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-05-open-app.gif" alt="Başlarken — adım 5: uygulamayı açma" width="820" />
</p>

### Adım 6 — Durdurma

```powershell
docker compose down
```

Volume'ları da kaldırma (uyarı: verileri siler):

```powershell
docker compose down -v
```

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-06-stop.gif" alt="Başlarken — adım 6: yığını durdurma" width="820" />
</p>

---

## Ortam değişkenleri

Tam referans: [`.env.example`](.env.example)

### Zorunlu

| Değişken | Zorunlu | Açıklama |
|----------|---------|----------|
| `POSTGRES_DB` | Evet | Varsayılan: `nrs_finance` |
| `POSTGRES_USER` | Evet | DB kullanıcı adı |
| `POSTGRES_PASSWORD` | Evet | DB parolası |

### Önerilen (veri ve daha zengin UI)

| Değişken | Zorunlu | Açıklama |
|----------|---------|----------|
| `EVDS_API_KEY` | Hayır | TCMB EVDS makro verisi (makro paneller için önerilir) |
| `FINHUB_API_KEY` | Hayır | ABD hisse / ETF verisi (isteğe bağlı) |

### İsteğe bağlı (özellik bayrakları ve entegrasyonlar)

| Değişken | Zorunlu | Açıklama |
|----------|---------|----------|
| `OPENAI_API_KEY` | Hayır | Portföy AI analizini etkinleştirir (isteğe bağlı) |
| `GMAIL_*` | Hayır | `notification-service` üzerinden e-posta iletimi (isteğe bağlı) |
| `VITE_*` | Hayır | Frontend çalışma zamanı yapılandırması (Docker varsayılanları genelde yeterli) |

> **Not:** Bazı değişkenler `docker-compose.yml` veya servis `application-docker.yml` içinde tanımlıdır, `.env.example`'da listelenmemiş olabilir. Yerelde geçersiz kılmak için `.env`'e ekleyin. Örnekler: `KEYCLOAK_SECURITY_*`, `PORTFOLIO_AI_DAILY_LIMIT`, `NRS_INTERNAL_BACKFILL_TOKEN`, `MARKET_DATA_INTERNAL_BACKFILL_TOKEN`. Bkz. [`docker-compose.yml`](docker-compose.yml) ve modül README'leri.

Docker Compose, depo kökündeki `.env` dosyasını otomatik yükler; ek volume eşlemesi gerekmez.

---

## Servisler, portlar ve URL'ler

### Docker Compose (varsayılan demo yığını)

| Bileşen | Host portu | Swagger / UI |
|---------|------------|--------------|
| **Frontend** | 3000 | http://localhost:3000 |
| **finance-service** | 8085 | http://localhost:8085/swagger-ui.html |
| **marketdata** | 8083 | http://localhost:8083/swagger-ui.html |
| **notification-service** | 8089 | http://localhost:8089/swagger-ui.html |
| **log-consumer-service** | 8087 | http://localhost:8087/swagger-ui.html |
| **Keycloak** | 8081 | http://localhost:8081 |
| **PostgreSQL** | 5432 | — |
| **Redis** | 6379 | — |
| **Kafka** | 9092 | — |
| **OpenSearch** | 9200 | — |
| **OpenSearch Dashboards** | 5601 | http://localhost:5601 |
| **Prometheus** | 9090 | http://localhost:9090 |
| **Grafana** | 3001 | http://localhost:3001 |
| **Tempo** | 3200 | — |

Servise özel port, env ve Swagger URL'leri için aşağıdaki **modül README** dosyalarına bakın. Bu kök README yalnızca tam yığın hızlı yolunu kapsar.

| Modül | README |
|--------|--------|
| finance-service | [finance-service/README.tr.md](finance-service/README.tr.md) |
| marketdata | [marketdata/README.tr.md](marketdata/README.tr.md) |
| notification-service | [notification-service/README.tr.md](notification-service/README.tr.md) |
| log-consumer-service | [log-consumer-service/README.tr.md](log-consumer-service/README.tr.md) |
| frontend | [frontend/README.tr.md](frontend/README.tr.md) |

### Yerel geliştirme portları (Docker olmadan)

| Servis | Varsayılan port |
|--------|-----------------|
| finance-service | 8080 |
| marketdata | 8086 |
| notification-service | 8089 |
| log-consumer-service | 8090 |
| frontend (Vite) | 5173 |

---

## Depo yapısı

```
NrsFinancePortal/
├── README.md                    ← GitHub giriş sayfası (İngilizce)
├── README.tr.md                 ← GitHub giriş sayfası (Türkçe)
├── .env.example                 ← Ortam şablonu
├── docker-compose.yml           ← Tam yığın tanımı
├── pom.xml                      ← Maven parent (Java 21)
│
├── frontend/                    ← React SPA
├── finance-service/             ← Çekirdek portal API
├── marketdata/                  ← Piyasa verisi servisi
├── notification-service/        ← Bildirimler ve e-posta
├── log-consumer-service/        ← Kafka → OpenSearch log indeksleme
│
├── infra/                       ← Keycloak, Grafana, Prometheus, OTel, Postgres init
├── docs/                        ← Teknik dokümantasyon merkezi
├── scripts/                     ← Geliştirme betikleri (ör. bakım SQL)
└── .github/workflows/ci.yml     ← CI hattı
```

Backend katmanlama (servis başına):

```
src/main/java/.../
├── api/              # REST controller, DTO, GlobalExceptionHandler
├── application/      # İş mantığı, use-case servisleri
├── domain/           # Varlıklar, domain modelleri
├── infrastructure/   # JPA, Kafka, Keycloak, harici API istemcileri
└── config/           # Spring yapılandırması
```

---

## Test

### Katman 1 — Otomatik (CI)

GitHub Actions her push/PR'da: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

- 4 backend modülü: Testcontainers ile `mvn test`
- Frontend: ESLint, Vitest, üretim derlemesi
- Docker duman derlemesi

### Katman 2 — Manuel smoke (değerlendirici)

Yığın ayağa kalktıktan sonra temel akışları doğrulayın (tam liste: [`docs/getting-started.tr.md` §5](docs/getting-started.tr.md#5-fonksiyonel-smoke-test)):

- [ ] http://localhost:3000 giriş → **Dashboard** açılır
- [ ] **Piyasa** → enstrüman listesi yüklenir
- [ ] **Portföy** → sayfa açılır
- [ ] (Admin) **Admin → Audit** → Grafana paneli görünür

### Katman 3 — İsteğe bağlı yerel test (geliştirici)

Docker Desktop (Testcontainers) ve host'ta JDK/Maven/Node gerekir. Komutlar: [`docs/getting-started.tr.md` §7](docs/getting-started.tr.md#7-test-suite).

İsteğe bağlı Javadoc (yerel JDK gerekmez):

```powershell
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q javadoc:javadoc -DskipTests
```

### Başarı kriterleri (kurulum tamam)

- [ ] `docker compose ps` — kritik servisler **running** / **healthy**
- [ ] 4 backend `/actuator/health` **UP**
- [ ] http://localhost:3000 — giriş + **Dashboard**
- [ ] En az bir **Swagger UI** açılır (8085 veya 8083)
- [ ] http://localhost:3001 — **Grafana** açılır
- [ ] (Opsiyonel) OpenSearch Dashboards → `application-logs-*` indeksi

---

## Proje isterleri uyumu

| Madde | Doğrulama yeri |
|-------|----------------|
| **Madde 21** — README ve kurulum rehberi | Bu dosya + [`docs/getting-started.tr.md`](docs/getting-started.tr.md) |
| **Madde 22** — Birim / entegrasyon testleri | [Test](#test) + CI rozeti + getting-started §7 |
| **Madde 14** — Docker Compose yığını | [`docker-compose.yml`](docker-compose.yml) |
| **Madde 15** — Mikroservis mimarisi | [Sistem mimarisi](#sistem-mimarisi) + [`docs/architecture.tr.md`](docs/architecture.tr.md) |
| **Madde 16** — REST API ve Swagger | Swagger URL'leri + [`docs/api/README.tr.md`](docs/api/README.tr.md) |
| **Madde 17** — Kimlik doğrulama (Keycloak/JWT) | [Demo hesapları](#adım-5--uygulamayı-açma) + [`docs/security/README.tr.md`](docs/security/README.tr.md) |
| **Madde 18** — Gözlemlenebilirlik | Grafana :3001 + [`docs/observability/README.tr.md`](docs/observability/README.tr.md) |
| **Madde 19** — Merkezi loglama | Kafka → OpenSearch — getting-started §6 |
| **Madde 20** — CI hattı | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) |

---

## Dokümantasyon haritası

| Konu | Dosya |
|------|-------|
| **Dokümantasyon merkezi** | [docs/README.tr.md](docs/README.tr.md) |
| **Mimari derinlemesi** | [docs/architecture.tr.md](docs/architecture.tr.md) |
| **Adım adım kurulum ve doğrulama** | [docs/getting-started.tr.md](docs/getting-started.tr.md) |
| **REST API ve OpenAPI** | [docs/api/README.tr.md](docs/api/README.tr.md) |
| **Gözlemlenebilirlik (Grafana, OTel, OpenSearch)** | [docs/observability/README.tr.md](docs/observability/README.tr.md) |
| **Güvenlik (Keycloak, JWT, 2FA)** | [docs/security/README.tr.md](docs/security/README.tr.md) |
| **E-posta kurulumu (Gmail OAuth)** | [docs/email-setup.tr.md](docs/email-setup.tr.md) |
| **Keycloak bootstrap (Compose)** | [docs/ops/keycloak-bootstrap.tr.md](docs/ops/keycloak-bootstrap.tr.md) |
| **API hızlı referansı** | [docs/api/endpoints.tr.md](docs/api/endpoints.tr.md) |

---

## Sorun giderme

<details>
<summary><strong>3000 veya 8085 portu zaten kullanımda</strong></summary>

```powershell
docker compose down
netstat -ano | findstr :3000
netstat -ano | findstr :8085
```

Çakışan süreci durdurun veya `docker-compose.yml` içinde port eşlemesini değiştirin.

</details>

<details>
<summary><strong>finance-service sürekli yeniden başlıyor</strong></summary>

market-data'nın sağlıklı olmasını bekliyor olabilir:

```powershell
docker compose logs market-data-service
docker compose logs finance-service
```

</details>

<details>
<summary><strong>Piyasa / makro paneller boş</strong></summary>

`.env` içinde `EVDS_API_KEY` ayarlı mı kontrol edin. Anahtar olmadan makro paneller boş kalabilir.

</details>

<details>
<summary><strong>Keycloak redirect_uri uyuşmazlığı</strong></summary>

Frontend'i `http://localhost:3000` üzerinden kullanın. UI'ı `:5173` ile çalıştırıyorsanız Keycloak istemci yönlendirme URI'lerine `http://localhost:5173/*` ekleyin.

</details>

<details>
<summary><strong>Swagger 401 Unauthorized</strong></summary>

Swagger UI'da **Authorize**'a tıklayın ve Keycloak ile giriş yaptıktan sonra `Bearer <access_token>` yapıştırın.

</details>

---

**Bakımcı:** NRS Finance Portal ekibi · eğitim/demo teslim kapsamı
