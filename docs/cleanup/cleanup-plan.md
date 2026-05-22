# Servis Bazlı Temizlik Planı (güvenli mod)

Amaç: Çalışan akışlara **dokunmadan** ölü kod, kullanılmayan endpoint ve legacy parçaları kademeli temizlemek.  
Prensip: **Önce kanıt → sonra deprecate → gözlem → en son silme.** Tek PR’da büyük silme yok.

Mevcut referans: [cleanup-candidates-matrix.md](./cleanup-candidates-matrix.md)

---

## Korunan yüzeyler (dokunulmaz)

Aşağıdakiler smoke test ile her dalga sonrası doğrulanır:

| Alan | Kritik uçlar |
|------|----------------|
| Auth | Keycloak login, `/api/users/me`, `/api/public/register/**` |
| Dashboard | `/api/dashboard/summary`, `/api/me/starred-assets`, `/api/portfolio/snapshots/me` |
| Portföy | `/api/portfolio/manual/**`, AI `/api/portfolio/ai/**` |
| Piyasa | `/api/market/**` (finance proxy), marketdata doğrudan çağrılar |
| Banka kurları | `/api/market/bank-rates/board`, dovizborsa ingest |
| Vadeli | `/api/me/viop-positions/**`, `/api/me/bond-positions/**` |
| Bildirim | `notification-service` → `/api/notifications/internal/email/send` |
| Admin | `/api/admin/users/**`, `/api/admin/audit/**`, observability |

---

## Dalga 0 — Envanter (silme yok, 0.5–1 gün)

**Çıktı:** `docs/cleanup/inventory-YYYY-MM-DD.md` (tek seferlik snapshot)

| # | Görev | Komut / yöntem |
|---|--------|----------------|
| 0.1 | API katalog snapshot | `bash tools/generate_api_catalog.sh` → `docs/api/endpoints.md` |
| 0.2 | Frontend → API eşlemesi | `frontend/src/services/*.ts`, sayfa içi `financeClient` / `marketClient` grep |
| 0.3 | Backend → tüketici | Servisler arası: `finance-service` içinde `MARKET_DATA`, `notification` URL grep |
| 0.4 | Ölü Java sınıfları | IDE / `mvn -DskipTests compile` + manuel: hiç inject edilmeyen `@Service` |
| 0.5 | DB vs kod | `v22-jury-domain-cleanup` sonrası kalan domain referansları (fund/trade/account) |

**Kabul:** Envanter tablosu: *aday | kanıt (dosya satır) | risk | önerilen dalga*

---

## Dalga 1 — Sıfır risk (hemen yapılabilir)

Kanıt: repoda **hiç referans yok** veya domain **v22 ile kaldırılmış**.

| # | Servis | Aday | Kanıt | Aksiyon |
|---|--------|------|-------|---------|
| 1.1 | frontend | `portfolioAiMockStore.ts` | Hiç import yok | Dosyayı sil |
| 1.2 | finance | `FinanceController` (`/finance/public`, `/finance/secure`) | `@Deprecated`, FE/test yok | Controller sil + security path temizliği |
| 1.3 | finance | `FundReceiptStorageService` + `app.receipt-storage` | `fund_requests` tablosu v22’de drop; controller yok | Service, DTO (`ReceiptUploadResponseDto` vb.), yml blokları sil |
| 1.4 | finance | `AuditContextMdcFilter` fund/trade dalları | `/api/fund-requests`, `/api/trades` artık yok | Ölü `if` dallarını kaldır |
| 1.5 | docs/artifacts | Eski `TradeController`/`WalletController` envanter satırları | Sınıf yok | Dokümantasyon düzelt |

**PR başına doğrulama:**
```bash
cd finance-service && bash ../mvnw -q test
cd frontend && npm run build
bash tools/generate_api_catalog.sh
```

**Manuel smoke (5 dk):** login → dashboard → portföy → piyasa → banka kurları → çıkış

---

## Dalga 2 — Zaten `@Deprecated` (silme değil, sınırlandırma)

| # | Servis | Aday | Not | Aksiyon |
|---|--------|------|-----|---------|
| 2.1 | marketdata | `BackfillController` `/internal/market/backfill/**` | Ops/cron; FE yok | `@Profile("ops")` veya sadece docker profil; dokümante curl |
| 2.2 | marketdata | `ProviderHealthController` | Stub health listesi | Admin observability ile birleştir **veya** profile `ops` |
| 2.3 | marketdata | `VakifBankFxProvider` | Boş `getLatest()` | Faz 3 kararı gelene kadar: config’ten `BANK` kaldırma **yapma**; sadece javadoc + log seviyesi; opsiyonel sınıfı registry’den çıkarıp yalnız EVDS+TCMB |

**Kabul:** Normal kullanıcı JWT akışı değişmez; ops profili açıkken backfill çalışır.

---

## Dalga 3 — “Endpoint var, UI yok” (ürün kararı gerekir)

| # | Endpoint / kod | Durum | Seçenek A (tut) | Seçenek B (temizle) |
|---|----------------|-------|-----------------|---------------------|
| 3.1 | `GET /api/portfolio/me/unified` | FE çağırmıyor; backend iç kullanım var | Public API olarak tut, dokümante et | Internal’a taşı veya kaldır |
| 3.2 | marketdata `/api/admin/market/**` | Sadece operasyon | Admin panel linki ekle | Profile `admin` + runbook |
| 3.3 | `GET /api/market/overview` | Simulation + marketDataService | Tut | — |
| 3.4 | Fund-request UI | Tablo v22’de yok; audit filtresi enum kaldı | AdminAudit enum temizle | — |

**Öneri:** 3.1 → **tut** (düşük maliyet, ileride dashboard birleşik görünüm). 3.4 → enum/label temizliği Dalga 1’e alınabilir.

---

## Dalga 4 — Frontend yönlendirme / ölü route

| # | Aday | Kanıt | Aksiyon |
|---|------|-------|---------|
| 4.1 | `/market/advanced` | `App.tsx` → `Navigate` to `/market` | Route kalsın (bookmark); `MarketHeatmap` linklerini `/market?...` query’ye çevir |
| 4.2 | Kullanılmayan i18n anahtarları | fund-request, eski jury metinleri | `tr.ts` / `en.ts` tarama (opsiyonel, düşük öncelik) |

**Kabul:** Heatmap karo tıklanınca Market sayfası açılır (regresyon yok).

---

## Dalga 5 — Altyapı / config (davranış aynı)

| # | Aday | Aksiyon |
|---|------|---------|
| 5.1 | `docker-compose.yml` çift Kafka env | Tek canonical key; diğeri bir release deprecated comment |
| 5.2 | `MarketViopProperties` `@Deprecated` cron alanları | YAML’den kaldır (kullanılmıyorsa) |
| 5.3 | `marketdata` `bist.scheduler-enabled: false` | Bilinçli kapalıysa dokümante; değilse açılış kararı ayrı iş |

---

## Dalga 6 — Yapılmayacaklar (kapsam dışı)

- Liquibase geçmiş dosyalarını silmek (`v12-fund-requests` vb.) — **yasak** (eski DB migrate bozulur)
- `Market.tsx` parçalama / büyük refactor
- Testcontainers / CI genişletme (Faz 5–6)
- Keycloak (Faz 2)

---

## Servis özet matrisi

| Servis | Yaklaşık `/api/**` | Öncelikli temizlik |
|--------|-------------------|-------------------|
| **finance-service** | 67 | Fund receipt, FinanceController, audit filter |
| **marketdata** | 71 | Deprecated internal, Vakıf stub (karar bekler) |
| **notification-service** | 4 | Temiz; internal email + health tutulur |
| **log-consumer-service** | 0 controller | Kafka consumer only; ayrı dead-code taraması |
| **frontend** | — | mock store, `/market/advanced` linkleri |

---

## PR sırası (önerilen)

```
PR-C0  docs: cleanup inventory snapshot
PR-C1  chore(finance): remove fund receipt + FinanceController
PR-C2  chore(frontend): remove portfolioAiMockStore
PR-C3  chore(finance): audit filter dead branches
PR-C4  chore(marketdata): profile-gate internal backfill/health
PR-C5  chore(frontend): heatmap links off /market/advanced
PR-C6  docs: update cleanup-candidates-matrix (closed items)
```

Her PR: tek servis ağırlıklı, geri alınabilir commit.

---

## Doğrulama checklist (her PR sonrası)

| Alan | Kontrol |
|------|---------|
| Derleme | `mvnw test` (dokunulan modül) |
| FE | `npm run build` (frontend değiştiyse) |
| Katalog | `bash tools/generate_api_catalog.sh` + commit drift |
| Contract gate | `bash tools/endpoint_contract_gate.sh` |
| Smoke | Login, Dashboard, Portföy manuel pozisyon, Piyasa FX, Banka kurları, Vadeli, Admin (admin ise) |

---

## Fazlarla ilişki

| Önce | Sonra |
|------|--------|
| Bu temizlik planı (Dalga 0–2 minimum) | Faz 2 Keycloak |
| Dalga 0–2 | Faz 5 CI |
| Stabil kod tabanı | Faz 6 IT, Faz 7 README |

**Donut (Faz 4):** kullanıcı onayı — atlandı.

---

## Sonraki adım

Onay sonrası uygulama sırası: **Dalga 0 (envanter)** → **PR-C1 + PR-C2** (en net ölü kod).

Hangi dalgadan kodlamaya başlanacağını belirtin; önerim **Dalga 0 + PR-C1** ile başlamak.
