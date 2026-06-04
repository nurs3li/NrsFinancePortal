<p align="center">
  <img src="./assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
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

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Dokümantasyon — NRS Finance Portal

Bu klasör, NRS Finance Portal için **teknik dokümantasyon merkezidir**. Kök [`README.tr.md`](../README.tr.md) en hızlı giriş noktasıdır; konuya odaklı derinlemesine kılavuzlar burada yer alır.

---

## Hızlı gezinme

| Aradığınız… | Belge |
|-----------------|-------|
| İlk kurulum ve doğrulama | [getting-started.tr.md](./getting-started.tr.md) · [EN](./getting-started.md) |
| Sistem mimarisi ve akışlar | [architecture.tr.md](./architecture.tr.md) · [EN](./architecture.md) |
| REST API'ler, OpenAPI, Swagger | [api/README.tr.md](./api/README.tr.md) · [Hızlı referans](./api/endpoints.tr.md) |
| Gözlemlenebilirlik | [observability/README.tr.md](./observability/README.tr.md) |
| Güvenlik (Keycloak/JWT/2FA) | [security/README.tr.md](./security/README.tr.md) |
| E-posta kurulumu (Gmail OAuth) | [email-setup.tr.md](./email-setup.tr.md) |
| Keycloak bootstrap (Compose) | [ops/keycloak-bootstrap.tr.md](./ops/keycloak-bootstrap.tr.md) |

---

## Modül README'leri

Her çalıştırılabilir modülün kendi README'si vardır:

| Modül | README | Kapsam |
|-------|--------|------------|
| frontend | [../frontend/README.tr.md](../frontend/README.tr.md) | React SPA + Keycloak istemcisi |
| finance-service | [../finance-service/README.tr.md](../finance-service/README.tr.md) | Çekirdek portal API (kullanıcılar, portföy, admin, simülasyonlar) |
| marketdata | [../marketdata/README.tr.md](../marketdata/README.tr.md) | Piyasa verisi toplama + Market REST API'leri |
| notification-service | [../notification-service/README.tr.md](../notification-service/README.tr.md) | Uygulama içi bildirimler + isteğe bağlı e-posta |
| log-consumer-service | [../log-consumer-service/README.tr.md](../log-consumer-service/README.tr.md) | Kafka → OpenSearch log indeksleme |

---

## Altyapı (kod dışı yapılandırma)

| Klasör | İçerik |
|--------|--------|
| [`../infra/keycloak/import/`](../infra/keycloak/import/) | Keycloak realm içe aktarma (`nrs-finance-realm.json`) |
| [`../infra/grafana/`](../infra/grafana/) | Grafana panoları + provisioning |
| [`../infra/prometheus/`](../infra/prometheus/) | Prometheus scrape yapılandırması |
| [`../infra/postgres/init/`](../infra/postgres/init/) | Postgres init betikleri (ör. `nrs_market` oluşturma) |
| [`../infra/otel-collector-config.yaml`](../infra/otel-collector-config.yaml) | OpenTelemetry collector |

---

## Dokümantasyon güncelleme politikası

1. **Davranış değişikliği** → modül README + `docs/architecture.md` **ve** `docs/architecture.tr.md`.
2. **Yeni/değişen endpoint** → SpringDoc + [`docs/api/endpoints.md`](./api/endpoints.md) / [`endpoints.tr.md`](./api/endpoints.tr.md).
3. **Yeni ortam değişkeni** → `.env.example` (uygunsa) + kök README.
4. **Güvenlik / Keycloak** → `docs/security/README.md` ve `docs/security/README.tr.md`.
5. **E-posta / Gmail** → `docs/email-setup.md` ve `docs/email-setup.tr.md`.

---

[← Kök README](../README.tr.md)
