# Mobil test matrisi (telefon modu)

Desteklenen minimum genişlik: **320px**. Önerilen test viewport’ları: **375×667**, **390×844**, **360×800** (portrait + landscape).

| Rota | CSS / not | Beklenen mobil davranış | Kod incelemesi |
|------|-----------|-------------------------|----------------|
| `/` (Landing) | `LandingPage.css` | Hero stack, 420px altı tipografi | Yeşil |
| `/dashboard` | `Dashboard.css` | KPI tek sütun ≤760px | Yeşil |
| `/news` | `News.css` | Kart/liste wrap | Yeşil |
| `/market` | `MarketTerminal.css` | Tek sütun ≤767px, treemap scroll | Sarı — geniş tablolar yatay kaydırma |
| `/market/heatmap` | `MarketHeatmap.css` | overflow-x hidden, toolbar wrap | Yeşil |
| `/market/macro` | `MacroIntelligence.css` | ≤640px grid | Yeşil |
| `/market/bank-rates` | `BankRatesPage.css` | ≤600px | Yeşil |
| `/portfolio` | `Portfolio.css` | ≤767px bloklar, tablo scroll | Sarı |
| `/portfolio/ai-analysis` | `PortfolioAiAnalysis.css` | ≤640px | Sarı — wizard detayı |
| `/simulation` | `Simulation.css` | ≤768/540px | Yeşil |
| `/viop-bond-analysis` | `ViopBondAnalysis.css` | ≤639px touch | Yeşil |
| `/notifications` | `Notifications.css` | ≤640px | Yeşil |
| `/settings` | `UserSettings.css` | ≤640px | Yeşil |
| `/admin/users` | `AdminUsersAndAccounts.css` | Kart layout ≤639px | Yeşil (Faz 1) |
| `/admin/audit` | inline + 640px grid | Grafana 1 sütun | Yeşil |

## Kontrol listesi (manuel)

Her rota için:

- [ ] Sayfa genelinde yatay scroll yok (istisna: bilinçli tablo wrap)
- [ ] Mobil menü açılıp kapanıyor, overlay tıklanınca kapanıyor
- [ ] Modallar `max-height: 90dvh` ve iç scroll
- [ ] Butonlar dokunma için ≥40px yükseklik
- [ ] Grafikler orientation sonrası genişliğe uyum
