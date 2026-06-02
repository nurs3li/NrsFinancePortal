<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Frontend — NRS Finance Portal

React 19 + TypeScript + Vite 7 tek sayfa uygulaması.

---

## Mobil (telefon) uyumluluk

- Desteklenen minimum genişlik: **320px**
- Test viewport önerisi: **375×667**, **390×844**, **360×800** (portrait)
- Mobil shell: `Layout.css` (`<1024px` hamburger menü), global sıkılaştırma: `src/styles/mobile-compact.css`
- Kontrol listesi: [`docs/mobile-test-matrix.md`](docs/mobile-test-matrix.md)

---

## Özet

| Öğe | Değer |
|---------|-------|
| **Çerçeve** | React 19 |
| **Derleme** | Vite 7 |
| **Durum / veri** | TanStack Query 5 |
| **Yönlendirme** | React Router 7 |
| **Kimlik doğrulama** | keycloak-js 26 |
| **Uygulama URL (Docker yığını)** | http://localhost:3000 |
| **Yerel port (isteğe bağlı)** | 5173 |

---

## Rotalar

| Yol | Sayfa | Açıklama |
|------|-------|----------|
| `/` | Açılış / yönlendirme | **Kayıt** ve **Şifremi unuttum** burada (ayrı route değil) |
| `/login` | Giriş yönlendirmesi | Keycloak giriş akışı |
| `/dashboard` | Gösterge paneli | Özet ve KPI'lar |
| `/news` | Haberler | Finans haber akışı (marketdata) |
| `/market` | Piyasa terminali | Canlı kotasyonlar ve grafikler |
| `/market/heatmap` | Isı haritası | Sektör ısı haritası |
| `/market/macro` | Makro | Enflasyon, faizler, eurobondlar |
| `/market/bank-rates` | Banka döviz kurları | Karşılaştırma tablosu |
| `/portfolio` | Portföy | Manuel pozisyonlar |
| `/portfolio/ai-analysis` | Portföy AI | AI raporu (`OPENAI_API_KEY`) |
| `/simulation` | Simülasyon | Tarihsel senaryolar |
| `/viop-bond-analysis` | VİOP / Tahvil | Analiz ve pozisyonlar |
| `/notifications` | Bildirimler | Uygulama içi gelen kutusu |
| `/settings` | Ayarlar | Profil, 2FA, bildirim tercihleri |
| `/admin/users` | Admin | Kullanıcı yönetimi — askıya al / kaldır |
| `/admin/audit` | Admin denetim | Loglar + Grafana gömüleri |

### Yönlendirme rotaları (ayrı sayfa yok)

| Yol | Yönlendirme |
|-----|-------------|
| `/market/advanced` | `/market` |
| `/trade`, `/transactions`, `/wallet` | `/portfolio` |
| `/admin` | `/admin/users` |
| `/admin/accounts`, `/admin/settings`, `/admin/market-ops` | `/admin/users` |

**Landing akışları (sayfa içi):**

- **Kayıt** — `/api/public/register` (`GMAIL_*` gerekir)
- **Şifremi unuttum** — Giriş sekmesi → `/api/public/password-reset/*`

Tanımlar: `src/App.tsx`, landing: `src/pages/LandingPage.tsx`

<p align="center">
  <img src="../docs/assets/gifs/features/registration-email-flow.gif" alt="Kayıt akışı" width="720" />
</p>

<p align="center">
  <img src="../docs/assets/gifs/features/password-reset-flow.gif" alt="Şifre sıfırlama" width="720" />
</p>

<p align="center">
  <img src="../docs/assets/gifs/features/market-terminal-browse.gif" alt="Piyasa terminali" width="720" />
</p>

<p align="center">
  <img src="../docs/assets/gifs/features/simulation-run.gif" alt="Simülasyon" width="720" />
</p>

---

## Proje yapısı

```
src/
├── api/              # Axios istemcisi, JWT interceptor, API sürümleme
├── auth/             # Keycloak, ProtectedRoute, RoleGuard
├── components/       # Alan UI (market, viopBond, macro, simulation, …)
├── pages/            # Rota sayfaları
├── services/         # Backend API çağrıları
├── hooks/            # Paylaşılan React hook'ları
├── queries/          # TanStack Query önbellek anahtarları
├── types/            # TypeScript tipleri
├── i18n/             # TR / EN çeviriler
├── theme/            # Koyu/açık tema
├── providers/        # QueryProvider
├── utils/ + lib/     # Yardımcılar
├── App.tsx
└── main.tsx
```

---

## Çalıştırma

### Docker Compose (önerilen)

Depo kökünden:

```powershell
docker compose up -d
```

Açın: http://localhost:3000

### Yerel (isteğe bağlı)

```powershell
cd frontend
npm ci
npm run dev
```

→ http://localhost:5173

> Keycloak yönlendirme URI'leri genelde `http://localhost:3000/*` için yapılandırılır. `:5173` kullanıyorsanız Keycloak istemci yönlendirme URI'lerini buna göre güncelleyin.

---

## Ortam değişkenleri

`VITE_*` değişkenleri depo kökü `.env` veya `frontend/.env` dosyasından yüklenir.

| Değişken | Varsayılan (Docker yığını) |
|----------|-------------------|
| `VITE_API_URL` | http://localhost:8085 |
| `VITE_MARKET_API_URL` | http://localhost:8083 |
| `VITE_NOTIFICATION_API_URL` | http://localhost:8089 |
| `VITE_KEYCLOAK_URL` | http://localhost:8081 |
| `VITE_KEYCLOAK_REALM` | nrs-finance |
| `VITE_KEYCLOAK_CLIENT_ID` | nrs-frontend |
| `VITE_API_VERSION` | v1 |

Grafana gömüleri (Admin Denetim): `VITE_GRAFANA_*` (`docker-compose.yml`).

---

## Komutlar

```powershell
npm run dev      # Yerel geliştirme sunucusu
npm run build    # Üretim derlemesi → dist/
npm run lint     # ESLint
npm test         # Vitest birim testleri
npm run preview  # Üretim derlemesini önizleme
```

---

## Test

Vitest testleri `src/**/*.test.ts` altındadır (örnekler):

- Hesaplamalar: `viopBondCalculations`, `marketPurchasingPower`
- API: `apiVersion`, `macroRatesApi`

```powershell
npm test
```

Bileşen/E2E testleri şu an kapsam dışı (backend ağırlıklı test stratejisi).

---

## Üretim imajı

`frontend/Dockerfile` statik `dist/` dosyasını nginx ile sunar.

UI'ı Keycloak ve backend servisleriyle uçtan uca çalıştırmanın önerilen yolu Docker yığınıdır.

---

## Dokümantasyon

- [Kök README](../README.tr.md)
- [Güvenlik (Keycloak)](../docs/security/README.tr.md)
- [API](../docs/api/README.tr.md)
