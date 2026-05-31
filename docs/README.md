<p align="center">
  <img src="./assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Documentation — NRS Finance Portal

This folder is the **technical documentation hub** for NRS Finance Portal. The root [`README.md`](../README.md) is the quickest entrypoint; deeper, topic-focused guides live here.

---

## Quick navigation

| Looking for… | Document |
|-----------------|-------|
| First-time setup & verification | [getting-started.md](./getting-started.md) · [TR](./getting-started.tr.md) |
| System architecture & flows | [architecture.md](./architecture.md) · [TR](./architecture.tr.md) |
| REST APIs, OpenAPI, Swagger | [api/README.md](./api/README.md) · [Quick reference](./api/endpoints.md) · [TR](./api/endpoints.tr.md) |
| Observability (Grafana/Prometheus/Tempo/OpenSearch) | [observability/README.md](./observability/README.md) |
| Security (Keycloak/JWT/2FA) | [security/README.md](./security/README.md) |
| Email setup (Gmail OAuth) | [email-setup.md](./email-setup.md) · [TR](./email-setup.tr.md) |
| Keycloak bootstrap (Compose) | [ops/keycloak-bootstrap.md](./ops/keycloak-bootstrap.md) · [TR](./ops/keycloak-bootstrap.tr.md) |

---

## Module READMEs

Each runnable module has its own README:

| Module | README | Scope |
|-------|--------|------------|
| frontend | [../frontend/README.md](../frontend/README.md) | React SPA + Keycloak client |
| finance-service | [../finance-service/README.md](../finance-service/README.md) | Core portal API (users, portfolio, admin, simulations) |
| marketdata | [../marketdata/README.md](../marketdata/README.md) | Market ingestion + Market REST APIs |
| notification-service | [../notification-service/README.md](../notification-service/README.md) | In-app notifications + optional email |
| log-consumer-service | [../log-consumer-service/README.md](../log-consumer-service/README.md) | Kafka → OpenSearch log indexing |

---

## Infrastructure (non-code configuration)

| Folder | What it contains |
|--------|--------|
| [`../infra/keycloak/import/`](../infra/keycloak/import/) | Keycloak realm import (`nrs-finance-realm.json`) |
| [`../infra/grafana/`](../infra/grafana/) | Grafana dashboards + provisioning |
| [`../infra/prometheus/`](../infra/prometheus/) | Prometheus scrape config |
| [`../infra/postgres/init/`](../infra/postgres/init/) | Postgres init scripts (e.g., create `nrs_market`) |
| [`../infra/otel-collector-config.yaml`](../infra/otel-collector-config.yaml) | OpenTelemetry collector |

---

## Documentation update policy

1. **Behavior change** → update the relevant module README and (if architectural) `docs/architecture.md` **and** `docs/architecture.tr.md`.
2. **New or changed endpoint** → update SpringDoc/Swagger (source of truth) and [`docs/api/endpoints.md`](./api/endpoints.md) / [`endpoints.tr.md`](./api/endpoints.tr.md).
3. **New env var** → update `.env.example` and reference it from root README (when the var belongs in example file).
4. **Security / Keycloak change** → update `docs/security/README.md` and `docs/security/README.tr.md`.
5. **Email / Gmail flows** → update `docs/email-setup.md` and `docs/email-setup.tr.md`.

---

[← Root README](../README.md)
