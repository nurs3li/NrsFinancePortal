<p>
  <img src="./docs/assets/gifs/architecture-overview.gif" alt="NRS Finance Portal — mimari özeti" width="100%" />
</p>

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
  <a href="LICENSE">
    <img alt="Lisans: MIT" src="https://img.shields.io/badge/License-MIT-blue.svg" />
  </a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 3.5.5" src="https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=000000" />
  <img alt="Docker Compose" src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" />
</p>

<p align="center">
  Portföy yönetimi, canlı piyasalar, reel getiri analitiği, simülasyonlar, alarmlar ve üretim kalitesinde gözlemlenebilirlik — <strong>4 Spring Boot mikroservisi</strong> + <strong>1 React SPA</strong>, Docker Compose üzerinde.
</p>

### Temel yetenekler

- **Çok varlıklı piyasa terminali** — döviz, kripto, hisse, fon, VİOP, tahvil
- **Portföy analitiği** — manuel pozisyonlar, enflasyona göre reel getiri, yoğunlaşma
- **Yatırım simülasyonu** — geçmişe dönük what-if senaryoları
- **Kimlik ve güvenlik** — Keycloak JWT, isteğe bağlı TOTP 2FA
- **Gözlemlenebilirlik** — OpenTelemetry, Prometheus, Grafana, Tempo, merkezi loglar (Kafka → OpenSearch)
- **Açık API'ler** — servis başına SpringDoc OpenAPI / Swagger

<table>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/market-terminal-browse.gif" alt="Piyasa terminali" width="100%" />
    </td>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/simulation-run.gif" alt="Yatırım simülasyonu" width="100%" />
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/portfolio-manual-position.gif" alt="Portföy — manuel pozisyon ekleme" width="100%" />
    </td>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/portfolio-ai-report.gif" alt="Portföy AI — analiz raporu" width="100%" />
    </td>
  </tr>
</table>

---

## İçindekiler

1. [Hızlı başlangıç](#hızlı-başlangıç)
2. [Ürün özeti](#ürün-özeti)
3. [Teknoloji yığını](#teknoloji-yığını)
4. [Sistem mimarisi](#sistem-mimarisi)
5. [Başlarken (Docker)](#başlarken-docker)
6. [Servis uç noktaları](#servis-uç-noktaları)
7. [Depo yapısı](#depo-yapısı)
8. [Test](#test)
9. [Dokümantasyon haritası](#dokümantasyon-haritası)
10. [Sorun giderme](#sorun-giderme)

---

## Hızlı başlangıç

| | |
|---|---|
| **Gerekenler** | Git + Docker Desktop (**8 GB RAM** önerilir) |
| **1. Klon & ortam** | `git clone` → `copy .env.example .env` → en az `POSTGRES_PASSWORD` ayarlayın |
| **2. Başlat** | `docker compose up -d --build` (ilk sefer **5–15 dk**) |
| **3. Aç** | http://localhost:3000 |
| **4. Demo giriş** | `testuser` / `123456789` (kullanıcı) veya `nrsadmin` / `123456789` (admin) |

> Demo şifreler yalnızca **yerel geliştirme** içindir. Tam rehber: [`docs/getting-started.tr.md`](docs/getting-started.tr.md).

---

## Ürün özeti

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

Derinlemesine: [`docs/architecture.tr.md`](docs/architecture.tr.md) (bileşen diyagramları, istek akışları, güvenlik).

<a href="docs/architecture.tr.md">
  <img src="./docs/assets/images/architecture/02-component-diagram.png" alt="Bileşen diyagramı — mimari dokümantasyonu için tıklayın" width="100%" />
</a>

<a href="docs/security/README.tr.md">
  <img src="./docs/assets/images/architecture/05-security-architecture.png" alt="Güvenlik mimarisi — Keycloak + JWT" width="100%" />
</a>

<p>
  <img src="./docs/assets/gifs/system-architecture.gif" alt="Docker Compose topolojisi" width="100%" />
</p>

---

## Başlarken (Docker)

Tam GIF rehberi, smoke test ve doğrulama listesi: [`docs/getting-started.tr.md`](docs/getting-started.tr.md).

**Ön koşullar:** Git 2.x + Docker Desktop 4.x (`docker compose version`). Öncelikle Docker — servisleri Compose dışında çalıştırmadığınız sürece host'ta JDK/Maven/Node gerekmez.

### Adım 1 — Klonlama

```powershell
git clone https://github.com/nurs3li/NrsFinancePortal.git
cd NrsFinancePortal
```

<table>
<tr>
<td valign="top" width="50%">
<h3>Adım 2 — Ortam dosyası</h3>
<pre><code>copy .env.example .env</code></pre>
<p>Yalnızca <code>POSTGRES_PASSWORD</code> değiştirin; varsayılan DB/kullanıcı değerlerine dokunmayın. <strong>Zorunlu</strong> <code>POSTGRES_PASSWORD</code> · <strong>Önerilen</strong> <code>EVDS_API_KEY</code> (<a href="https://evds3.tcmb.gov.tr/">TCMB EVDS</a>) · <strong>Opsiyonel</strong> <code>FINHUB_API_KEY</code>, <code>OPENAI_API_KEY</code>, <code>GMAIL_*</code></p>
</td>
<td valign="top" width="50%">
<h3>Adım 3 — Yığını başlat</h3>
<pre><code>docker compose up -d --build</code></pre>
<p>İlk çalıştırma <strong>5–15 dk</strong>. React SPA, 4 Spring servisi, Postgres, Redis, Kafka, Keycloak, OpenSearch, Prometheus, Grafana, Tempo başlar.</p>
</td>
</tr>
</table>

### Adım 4 — Doğrulama

```powershell
docker compose ps
```

<code>nrs-*</code> konteynerleri <strong>running/healthy</strong> olmalı. İlk açılışta <code>market-data-service</code> 5–10 dk <em>starting</em> kalabilir.

<pre><code>curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8083/actuator/health
curl.exe http://localhost:8089/actuator/health
curl.exe http://localhost:8087/actuator/health
docker compose logs -f finance-service</code></pre>

PowerShell: <code>curl.exe</code> kullanın (<code>curl</code> alias'ı değil). Detaylar: [`docs/getting-started.tr.md`](docs/getting-started.tr.md).

<table>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/getting-started/step-01-clone.gif" alt="Adım 1: klonlama" width="100%" />
    </td>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/getting-started/step-02-env.gif" alt="Adım 2: ortam dosyası" width="100%" />
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/getting-started/step-03-compose-up.gif" alt="Adım 3: docker compose up" width="100%" />
    </td>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/getting-started/step-04-verify.gif" alt="Adım 4: doğrulama" width="100%" />
    </td>
  </tr>
</table>

### Adım 5 — Uygulamayı açma

http://localhost:3000 adresini açın. Servis URL'leri: aşağıdaki [Servis uç noktaları](#servis-uç-noktaları).

| Hesap | Şifre | Rol |
|-------|-------|-----|
| `nrsadmin` | `123456789` | ADMIN |
| `testuser` | `123456789` | USER |

<p align="center">
  <img src="./docs/assets/gifs/getting-started/step-05-open-app.gif" alt="Adım 5: uygulamayı açma ve giriş" width="100%" />
</p>

<details>
<summary><strong>Kayıt, şifre sıfırlama ve 2FA (isteğe bağlı)</strong></summary>

E-posta akışları için <code>.env</code> içinde <code>GMAIL_*</code> gerekir — bkz. <a href="docs/email-setup.tr.md">docs/email-setup.tr.md</a>. Swagger: http://localhost:3000 giriş → <strong>Authorize</strong> → <code>Bearer &lt;access_token&gt;</code>.

<table>
  <tr>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/registration-email-flow.gif" alt="Kayıt akışı" width="100%" />
    </td>
    <td align="center" width="50%">
      <img src="./docs/assets/gifs/features/password-reset-flow.gif" alt="Şifre sıfırlama akışı" width="100%" />
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <img src="./docs/assets/gifs/features/settings-totp-enable.gif" alt="Ayarlar — 2FA etkinleştirme" width="50%" />
    </td>
  </tr>
</table>

</details>

> Minimum ortam: `POSTGRES_PASSWORD`. Önerilen: `EVDS_API_KEY`. Tam referans: [`.env.example`](.env.example).

---

## Servis uç noktaları

### Docker Compose (varsayılan yığın)

<p align="center">
  <a href="http://localhost:3000" title="Frontend (nginx, production profili)"><img alt="Portal :3000" src="https://img.shields.io/badge/:3000-Portal-61DAFB?style=flat-square&logo=react&logoColor=000" height="22" /></a>
  <a href="http://localhost:3000" title="Frontend dev (Vite, dev profili)"><img alt="Vite :3000" src="https://img.shields.io/badge/:3000-Vite-646CFF?style=flat-square&logo=vite&logoColor=white" height="22" /></a>
  <a href="http://localhost:8085/swagger-ui.html" title="finance-service Swagger UI"><img alt="finance :8085" src="https://img.shields.io/badge/:8085-finance-6DB33F?style=flat-square&logo=springboot&logoColor=white" height="22" /></a>
  <a href="http://localhost:8083/swagger-ui.html" title="marketdata Swagger UI"><img alt="marketdata :8083" src="https://img.shields.io/badge/:8083-marketdata-6DB33F?style=flat-square&logo=springboot&logoColor=white" height="22" /></a>
  <a href="http://localhost:8089/swagger-ui.html" title="notification-service Swagger UI"><img alt="notification :8089" src="https://img.shields.io/badge/:8089-notification-6DB33F?style=flat-square&logo=springboot&logoColor=white" height="22" /></a>
  <a href="http://localhost:8087/swagger-ui.html" title="log-consumer-service Swagger UI"><img alt="log-consumer :8087" src="https://img.shields.io/badge/:8087-log--consumer-6DB33F?style=flat-square&logo=springboot&logoColor=white" height="22" /></a>
  <a href="http://localhost:8081" title="Keycloak (realm: nrs-finance)"><img alt="Keycloak :8081" src="https://img.shields.io/badge/:8081-Keycloak-4D4D4D?style=flat-square&logo=keycloak&logoColor=white" height="22" /></a>
</p>
<p align="center">
  <a href="http://localhost:5432" title="PostgreSQL"><img alt="Postgres :5432" src="https://img.shields.io/badge/:5432-Postgres-4169E1?style=flat-square&logo=postgresql&logoColor=white" height="22" /></a>
  <a href="http://localhost:6379" title="Redis"><img alt="Redis :6379" src="https://img.shields.io/badge/:6379-Redis-DC382D?style=flat-square&logo=redis&logoColor=white" height="22" /></a>
  <a href="http://localhost:9092" title="Kafka"><img alt="Kafka :9092" src="https://img.shields.io/badge/:9092-Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white" height="22" /></a>
  <a href="http://localhost:9200" title="OpenSearch API"><img alt="OpenSearch :9200" src="https://img.shields.io/badge/:9200-OpenSearch-005EB8?style=flat-square&logo=opensearch&logoColor=white" height="22" /></a>
  <a href="http://localhost:5601" title="OpenSearch Dashboards"><img alt="Dashboards :5601" src="https://img.shields.io/badge/:5601-Dashboards-005EB8?style=flat-square&logo=opensearch&logoColor=white" height="22" /></a>
  <a href="http://localhost:9090" title="Prometheus"><img alt="Prometheus :9090" src="https://img.shields.io/badge/:9090-Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white" height="22" /></a>
  <a href="http://localhost:3001" title="Grafana (admin / admin)"><img alt="Grafana :3001" src="https://img.shields.io/badge/:3001-Grafana-F46800?style=flat-square&logo=grafana&logoColor=white" height="22" /></a>
  <a href="http://localhost:3200" title="Tempo"><img alt="Tempo :3200" src="https://img.shields.io/badge/:3200-Tempo-F46800?style=flat-square&logo=grafana&logoColor=white" height="22" /></a>
</p>

<p align="center">
  <a href="http://localhost:8085/swagger-ui.html">
    <img src="./docs/assets/images/api/swagger-ui-finance.png" alt="finance-service Swagger UI" width="80%" />
  </a>
</p>

| Modül | README |
|--------|--------|
| finance-service | [finance-service/README.tr.md](finance-service/README.tr.md) |
| marketdata | [marketdata/README.tr.md](marketdata/README.tr.md) |
| notification-service | [notification-service/README.tr.md](notification-service/README.tr.md) |
| log-consumer-service | [log-consumer-service/README.tr.md](log-consumer-service/README.tr.md) |
| frontend | [frontend/README.tr.md](frontend/README.tr.md) |

<details>
<summary><strong>Yerel geliştirme portları (Docker olmadan)</strong></summary>

| Servis | Varsayılan port |
|--------|-----------------|
| finance-service | 8080 |
| marketdata | 8086 |
| notification-service | 8089 |
| log-consumer-service | 8090 |
| frontend (Vite) | 5173 |

</details>

---

## Depo yapısı

```
NrsFinancePortal/
├── README.md                    ← GitHub giriş sayfası (bu dosya)
├── .env.example                 ← Ortam şablonu
├── docker-compose.yml           ← Tam yığın tanımı
├── frontend/                    ← React SPA
├── finance-service/             ← Çekirdek portal API
├── marketdata/                  ← Piyasa verisi servisi
├── notification-service/        ← Bildirimler ve e-posta
├── log-consumer-service/        ← Kafka → OpenSearch log indeksleme
├── infra/                       ← Keycloak, Grafana, Prometheus, OTel
├── docs/                        ← Teknik dokümantasyon merkezi
└── .github/workflows/ci.yml     ← CI hattı
```

Backend katmanlama (`api` → `application` → `domain` → `infrastructure`): [`docs/architecture.tr.md`](docs/architecture.tr.md).

---

## Test

CI her push/PR'da çalışır: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (backend Testcontainers, frontend lint/build, Docker smoke).

**Smoke kontrol listesi** (`docker compose up` sonrası):

- [ ] http://localhost:3000 giriş → **Dashboard** açılır
- [ ] **Piyasa** → enstrüman listesi yüklenir
- [ ] **Portföy** → sayfa açılır
- [ ] (Admin) **Admin → Audit** → Grafana paneli gömülür

<p align="center">
  <img src="./docs/assets/gifs/features/admin-audit-grafana.gif" alt="Admin denetim — Grafana gömüsü" width="80%" />
</p>

<details>
<summary><strong>Genişletilmiş test paketi ve başarı kriterleri</strong></summary>

- Yerel `mvn test` / Vitest komutları: [`docs/getting-started.tr.md` §7](docs/getting-started.tr.md#7-test-suite)
- Kurulum tamam: 4 `/actuator/health` **UP**, Swagger UI açılır, Grafana :3001 açılır
- Opsiyonel Javadoc: `docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q javadoc:javadoc -DskipTests`

</details>

---

## Dokümantasyon haritası

| Konu | Dosya |
|------|-------|
| **Dokümantasyon merkezi** | [docs/README.tr.md](docs/README.tr.md) |
| **Mimari** | [docs/architecture.tr.md](docs/architecture.tr.md) |
| **Kurulum ve doğrulama** | [docs/getting-started.tr.md](docs/getting-started.tr.md) |
| **REST API ve OpenAPI** | [docs/api/README.tr.md](docs/api/README.tr.md) |
| **Gözlemlenebilirlik** | [docs/observability/README.tr.md](docs/observability/README.tr.md) |
| **Güvenlik** | [docs/security/README.tr.md](docs/security/README.tr.md) · [SECURITY.md](SECURITY.md) |
| **E-posta kurulumu** | [docs/email-setup.tr.md](docs/email-setup.tr.md) |
| **Proje isterleri uyumu** | [docs/requirements-compliance.tr.md](docs/requirements-compliance.tr.md) |

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

```powershell
docker compose logs market-data-service
docker compose logs finance-service
```

</details>

<details>
<summary><strong>Piyasa / makro paneller boş</strong></summary>

`.env` içinde `EVDS_API_KEY` ayarlayın. Anahtar olmadan makro paneller boş kalabilir.

</details>

<details>
<summary><strong>Keycloak redirect_uri uyuşmazlığı</strong></summary>

http://localhost:3000 kullanın. Vite `:5173` için Keycloak istemci yönlendirme URI'lerine `http://localhost:5173/*` ekleyin.

</details>

<details>
<summary><strong>Swagger 401 Unauthorized</strong></summary>

http://localhost:3000 üzerinden giriş yapın, ardından Swagger UI'da **Authorize** → `Bearer <access_token>`.

</details>

---

<p align="center">
  <strong>NRS Finance Portal</strong> · MIT Lisansı · <a href="SECURITY.md">Güvenlik</a>
</p>

<p align="center">
  <em>Demo / eğitim platformu — yatırım tavsiyesi değildir. Piyasa verileri yerel ortamda gecikmeli veya sentetik olabilir.</em>
</p>

<p align="center">
  <strong>Bakımcı:</strong> NRS Finance Portal ekibi
</p>
