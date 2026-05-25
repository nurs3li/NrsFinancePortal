# Dokümantasyon — NRS Finans Portalı

Bu klasör, Finans Portalı projesinin **teknik dokümantasyon merkezidir**. GitHub ana sayfasındaki [`README.md`](../README.md) hızlı başlangıç içindir; derinlemesine konular burada toplanır.

---

## Hızlı navigasyon

| Ne arıyorsunuz? | Dosya |
|-----------------|-------|
| Projeyi ilk kez çalıştırma | [getting-started.md](./getting-started.md) |
| Mimari ve veri akışları | [architecture.md](./architecture.md) |
| REST API, Swagger, OpenAPI | [api/README.md](./api/README.md) |
| Java sınıf dokümantasyonu (Javadoc) | [javadoc.md](./javadoc.md) |
| Grafana, Prometheus, OTel, OpenSearch | [observability/README.md](./observability/README.md) |
| Keycloak, JWT, 2FA | [security/README.md](./security/README.md) |
| README GIF/görsel ekleme | [assets/README.md](./assets/README.md) |

---

## Modül README'leri

Her çalıştırılabilir bileşenin kendi README'si vardır:

| Modül | README | Sorumluluk |
|-------|--------|------------|
| Frontend | [../frontend/README.md](../frontend/README.md) | React SPA, Keycloak client |
| finance-service | [../finance-service/README.md](../finance-service/README.md) | Portföy, kullanıcı, admin, simülasyon |
| marketdata | [../marketdata/README.md](../marketdata/README.md) | Piyasa verisi ingest & API |
| notification-service | [../notification-service/README.md](../notification-service/README.md) | Bildirim, e-posta |
| log-consumer-service | [../log-consumer-service/README.md](../log-consumer-service/README.md) | Kafka → OpenSearch log |

---

## Altyapı dosyaları (kod dışı)

| Klasör | İçerik |
|--------|--------|
| [`../infra/keycloak/`](../infra/keycloak/import/) | Keycloak realm import (`nrs-finance-realm.json`) |
| [`../infra/grafana/`](../infra/grafana/) | Dashboard ve datasource provisioning |
| [`../infra/prometheus/`](../infra/prometheus/) | Scrape config |
| [`../infra/postgres/init/`](../infra/postgres/init/) | İlk kurulumda `nrs_market` DB oluşturma |
| [`../infra/otel-collector-config.yaml`](../infra/otel-collector-config.yaml) | OpenTelemetry collector |

---

## Otomasyon araçları

| Araç | Komut | Amaç |
|------|-------|------|
| API katalog | `bash tools/generate_api_catalog.sh` | Controller taraması → endpoint listesi |
| OpenAPI export | `bash tools/export_openapi.sh` | Canlı `/v3/api-docs` JSON |
| Contract gate | `bash tools/endpoint_contract_gate.sh` | CI endpoint metadata kontrolü |
| Javadoc | `.\scripts\Generate-Javadoc.ps1` | HTML API dokümantasyonu |

---

## Dokümantasyon güncelleme ilkesi

1. **Davranış değişikliği** → ilgili modül README + gerekirse `docs/architecture.md`.
2. **Yeni API endpoint** → Swagger + isteğe bağlı `tools/generate_api_catalog.sh`.
3. **Yeni ortam değişkeni** → `.env.example` + kök README tablosu.
4. **Güvenlik / Keycloak değişikliği** → `docs/security/README.md`.

---

[← Ana README](../README.md)
