# Frontend — NRS Finans Portalı

React 19 + TypeScript + Vite 7 single-page application.

---

## Özet

| Özellik | Değer |
|---------|-------|
| **Framework** | React 19 |
| **Build** | Vite 7 |
| **State / data** | TanStack Query 5 |
| **Routing** | React Router 7 |
| **Auth** | keycloak-js 26 |
| **Docker port** | 3000 (Vite dev container) |
| **Yerel dev port** | 5173 |

---

## Sayfalar (route'lar)

| Path | Sayfa | Açıklama |
|------|-------|----------|
| `/` | Landing / redirect | Giriş yapmamış kullanıcı |
| `/login` | Login redirect | Keycloak akışı |
| `/dashboard` | Dashboard | Ana özet |
| `/market` | Piyasa terminali | Canlı fiyat, grafikler |
| `/market/heatmap` | Heatmap | Sektör ısı haritası |
| `/market/macro` | Makro zeka | Enflasyon, faiz, eurobond |
| `/market/bank-rates` | Banka kurları | Karşılaştırma tablosu |
| `/portfolio` | Portföy | Manuel pozisyonlar |
| `/portfolio/ai-analysis` | Portföy AI | AI analiz raporu |
| `/simulation` | Simülasyon | Geçmiş yatırım simülasyonu |
| `/viop-bond-analysis` | VİOP / Tahvil | Analiz ve pozisyon |
| `/notifications` | Bildirimler | In-app inbox |
| `/settings` | Ayarlar | Profil, 2FA, bildirim tercihleri |
| `/admin/users` | Admin | Kullanıcı yönetimi (ADMIN) |
| `/admin/audit` | Admin audit | Log + Grafana embed |

Route tanımları: `src/App.tsx`

---

## Klasör yapısı

```
src/
├── api/              # Axios client, JWT interceptor, API versioning
├── auth/             # Keycloak, ProtectedRoute, RoleGuard
├── components/       # Domain UI (market, viopBond, macro, simulation, …)
├── pages/            # Route sayfaları
├── services/         # Backend API çağrıları
├── hooks/            # Paylaşılan React hooks
├── queries/          # TanStack Query cache keys
├── types/            # TypeScript tipleri
├── i18n/             # TR / EN çeviriler
├── theme/            # Dark/light theme
├── providers/        # QueryProvider
├── utils/ + lib/     # Yardımcı fonksiyonlar
├── App.tsx
└── main.tsx
```

---

## Kurulum

### Docker (önerilen — Keycloak redirect uyumlu)

Repo kökünden:

```powershell
docker compose up -d frontend-dev
```

→ http://localhost:3000

### Yerel

```powershell
cd frontend
npm ci
npm run dev
```

→ http://localhost:5173

> Keycloak redirect URI genelde `http://localhost:3000/*` olarak yapılandırılmıştır. Yerel `:5173` redirect hatası verebilir.

---

## Ortam değişkenleri

`VITE_*` değişkenleri repo kökü `.env` veya `frontend/.env` dosyasından okunur.

| Değişken | Docker varsayılan |
|----------|-------------------|
| `VITE_API_URL` | http://localhost:8085 |
| `VITE_MARKET_API_URL` | http://localhost:8083 |
| `VITE_NOTIFICATION_API_URL` | http://localhost:8089 |
| `VITE_KEYCLOAK_URL` | http://localhost:8081 |
| `VITE_KEYCLOAK_REALM` | nrs-finance |
| `VITE_KEYCLOAK_CLIENT_ID` | nrs-frontend |
| `VITE_API_VERSION` | v1 |

Grafana embed (admin audit): `VITE_GRAFANA_*` — `docker-compose.yml` frontend-dev bölümü.

---

## Komutlar

```powershell
npm run dev      # Geliştirme sunucusu
npm run build    # Production build → dist/
npm run lint     # ESLint
npm test         # Vitest unit testleri
npm run preview  # Build önizleme
```

---

## Test

Vitest — `src/**/*.test.ts`:

- Hesaplama: `viopBondCalculations`, `marketPurchasingPower`
- API: `apiVersion`, `macroRatesApi`

```powershell
npm test
```

Component/E2E testleri kapsam dışı — backend test ağırlıklı proje standardı.

---

## Production imajı

`frontend/Dockerfile` — nginx ile statik `dist/` servisi.

Yerel demo: `docker-compose.yml` → `frontend-dev` (Vite HMR, kaynak bind mount).

---

## Dokümantasyon

- [Kök README](../README.md)
- [Güvenlik — Keycloak](../docs/security/README.md)
- [API](../docs/api/README.md)
