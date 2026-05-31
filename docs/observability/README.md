<p align="center">
  <img src="../../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Observability

NRS Finance Portal ships with an end-to-end observability stack: **OpenTelemetry**, **Prometheus**, **Grafana**, **Tempo**, **Kafka**, and **OpenSearch** (plus Dashboards).

---

## Component overview

| Component | Role | URL (Docker) |
|---------|-----|--------------|
| **OpenTelemetry Collector** | Trace/metric ingestion | OTLP http :4318 |
| **Prometheus** | Metrics storage | http://localhost:9090 |
| **Grafana** | Dashboards + Tempo UI | http://localhost:3001 |
| **Tempo** | Distributed tracing backend | http://localhost:3200 |
| **OpenSearch** | Log storage + search | http://localhost:9200 |
| **OpenSearch Dashboards** | Log UI | http://localhost:5601 |
| **Kafka** | Event/log transport | :9092 |
| **log-consumer-service** | Kafka → OpenSearch indexer | http://localhost:8087 |

---

## Metrics

Services expose metrics via Spring Boot Actuator + Micrometer:

```
GET /actuator/health
GET /actuator/prometheus
```

Prometheus scrape configuration: `infra/prometheus/prometheus.yml`

OTel Collector metrics endpoint: `:8889`

### Grafana dashboard

- Dashboard JSON: `infra/grafana/dashboards/nrs-observability.json`
- Provisioning: `infra/grafana/provisioning/`
- Local login: `admin / admin`
- Local/demo: anonymous viewer may be enabled to support audit embeds

The Admin Audit screen in the frontend can embed Grafana panels (via `VITE_GRAFANA_*`).

---

## Traces

Flow:

```
Spring Boot (Micrometer OTel bridge)
    → OTel Collector (:4318)
        → Tempo (trace storage)
        → Prometheus (span metrics)
            → Grafana (visualization)
```

Env (Docker):

```
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces
```

Application logs include `traceId` and `spanId` fields to enable correlation in OpenSearch.

---

## Logs (Kafka → OpenSearch pipeline)

```
Service (finance / marketdata / notification)
    → Log4j2 (JSON console + Kafka appender, default INFO+)
        → Kafka topic: application-logs
            → log-consumer-service (ApplicationLogsConsumer)
                → OpenSearch index: application-logs-yyyy-MM-dd
                    → OpenSearch Dashboards (Discover)
```

Key configuration points:

- Appender: `KafkaLogAppender` — finance, marketdata, notification
- Log4j2 config: `log4j2-spring.xml` — `APP_LOG_KAFKA_MIN_LEVEL` (default **INFO**)
- Consumer: `log-consumer-service/.../ApplicationLogsConsumer.java`
- Access log: `RequestLoggingFilter` — `[REQUEST] method uri status= durationMs=` (INFO)

### Environment variable

| Variable | Default | Description |
|----------|------------|----------|
| `APP_LOG_KAFKA_MIN_LEVEL` | `INFO` | Minimum Log4j level forwarded to Kafka/OpenSearch. `WARN` forwards only warnings/errors. |

Framework noise (kafka, hibernate, hikari) is typically capped at **WARN**; business and `[REQUEST]` logs are forwarded at **INFO**.

> **Consistency:** [`log-consumer-service/README.md`](../../log-consumer-service/README.md) describes the same default (**INFO+**, not WARN+). Override with `APP_LOG_KAFKA_MIN_LEVEL`.

DEBUG logs are not forwarded by default (root level INFO).

### OpenSearch Dashboards — Discover

1. http://localhost:5601
2. Index pattern: `application-logs-*`, time field: `timestamp`
3. Time range: **Last 24 hours**
4. Example queries:
   - `level:INFO AND message:"[REQUEST]"`
   - `serviceName:"finance-service" AND level:ERROR`
   - `correlationId:"<uuid>"`

### Log verification

```powershell
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

```powershell
curl "http://localhost:9200/application-logs-*/_search?size=3&pretty"
```

Common JSON fields: `serviceName`, `level`, `message`, `traceId`, `spanId`, `correlationId`, `userId`, `username`, `actionType`

---

## Smoke test checklist

| # | Check | How to verify |
|---|--------|-----------|
| 1 | Request count | Grafana panel — `http_server_requests_seconds_count` |
| 2 | Latency p95 | Grafana panel — sum/count ratio |
| 3 | Error rate | HTTP 5xx metrics / dashboards |
| 4 | Service health | `/actuator/health`, `up` metric |
| 5 | End-to-end tracing | Grafana → Tempo datasource → trace drilldown |
| 6 | Log correlation | Filter by `traceId` in OpenSearch |

Smoke commands:

```powershell
docker compose config
docker compose up -d
curl http://localhost:9090/-/ready
curl http://localhost:3001/api/health
curl http://localhost:8085/actuator/prometheus
curl http://localhost:8083/actuator/prometheus
```

---

## Admin audit integration

finance-service admin audit API:

- OpenSearch queries (audit logs)
- Grafana public URL templates
- Tempo trace links

Frontend: `AdminAudit.tsx` — embedded panels + external Grafana links.

<p align="center">
  <img src="../assets/gifs/features/admin-audit-grafana.gif" alt="Admin audit — Grafana embed" width="720" />
</p>

Env: `APP_OBSERVABILITY_*`, `VITE_GRAFANA_*`

---

## Production notes

In the local Docker Compose stack:

- Grafana anonymous viewer may be **enabled** — demo/local only
- Keycloak runs in a dev-friendly mode — not a production profile
- OpenSearch security plugins may be disabled for local convenience

In production, you should enable TLS, authentication/authorization, and disable anonymous Grafana access.

---

[← Documentation hub](../README.md)
