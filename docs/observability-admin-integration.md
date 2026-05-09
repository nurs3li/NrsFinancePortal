# Admin panel ↔ OpenSearch / Tempo / Grafana

## Özet

- **Audit log listesi**: `finance-service` → `GET /api/admin/audit/logs` (OpenSearch `application-logs-*`, sadece `ROLE_ADMIN`). Sonuçlar en fazla **500** kayıtla sınırlıdır (`track_total_hits` + sayfalama). Çoklu seviye: `levels=WARN,ERROR,INFO` (tek `level` yerine).
- **Log detayı**: `GET /api/admin/audit/logs/detail?cursor=...` (cursor = base64url(`index|_id`)).
- **Tempo özet topoloji**: `GET /api/admin/observability/traces/{traceId}/summary`.
- **Grafana Explore deep link**: `GET /api/admin/observability/explore/trace?traceId=...` → `{ url }` (tarayıcıda `window.open`).

## Feature flag

`app.observability.enabled` (`APP_OBSERVABILITY_ENABLED`): `false` iken audit uçları boş / devre dışı mesajı döner; mevcut API’ler bozulmaz.

## Ortam değişkenleri (Docker örnek)

| Değişken | Açıklama |
|----------|----------|
| `APP_OBSERVABILITY_OPENSEARCH_BASE_URL` | Örn. `http://nrs-opensearch:9200` |
| `APP_OBSERVABILITY_TEMPO_BASE_URL` | Örn. `http://nrs-tempo:3200` |
| `APP_OBSERVABILITY_GRAFANA_PUBLIC_BASE_URL` | **Kullanıcı tarayıcısından** erişilen Grafana kökü (örn. `http://localhost:3001`) |
| `APP_OBSERVABILITY_GRAFANA_TEMPO_UID` | Grafana Tempo datasource uid (varsayılan `nrs-tempo`) |

## iframe + Keycloak notu

Portal ve Grafana farklı origin’deyse tarayıcı oturumu paylaşılmaz. Üretim için öneri: reverse proxy ile aynı site altında Grafana (`/grafana/`) ve Grafana’da Keycloak OIDC. Geliştirmede: deep link yeni sekmede veya Grafana’da sınırlı anonymous viewer (risk kabulüyle).

## Log alanı

Kafka → OpenSearch dokümanlarına **`traceId`** ve **`spanId`** (MDC `trace_id` / `span_id`) eklendi; Tempo ile eşleşme için kullanılır.

## Opsiyonel: Admin dashboard’ta Grafana iframe

Frontend build arg / env: **`VITE_GRAFANA_DASHBOARD_EMBED_URL`** — Grafana’daki dashboard/panel “Share → Embed” URL’si (geliştirmede `&kiosk` ile menüsüz). Boşsa iframe gösterilmez. Tarayıcı **CSP `frame-src`** ve Grafana **allow embedding** ayarları gerekir.
