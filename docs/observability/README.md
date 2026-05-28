# Gözlemlenebilirlik (Observability)

Finans Portalı **Madde 10–13** kapsamında OpenTelemetry, Prometheus, Grafana, OpenSearch ve Kafka log pipeline içerir.

---

## Bileşen özeti

| Bileşen | Rol | URL (Docker) |
|---------|-----|--------------|
| **OpenTelemetry Collector** | Trace/metric toplama | OTLP :4318 |
| **Prometheus** | Metrik depolama | http://localhost:9090 |
| **Grafana** | Dashboard, Tempo trace | http://localhost:3001 |
| **Tempo** | Distributed trace backend | :3200 |
| **OpenSearch** | Log arama & indeks | http://localhost:9200 |
| **OpenSearch Dashboards** | Log UI | http://localhost:5601 |
| **Kafka** | Log ve event taşıyıcı | :9092 |
| **log-consumer-service** | Log indeksleyici | :8087 |

---

## Metrikler (Madde 11)

Spring Boot Actuator + Micrometer:

```
GET /actuator/health
GET /actuator/prometheus
```

Prometheus scrape: `infra/prometheus/prometheus.yml`

OTel collector metrik endpoint: `:8889`

### Grafana dashboard

- Dosya: `infra/grafana/dashboards/nrs-observability.json`
- Provisioning: `infra/grafana/provisioning/`
- Yerel giriş: admin / admin
- Yerel geliştirmede anonymous viewer açık (audit iframe'leri için)

Frontend admin audit sayfası Grafana panellerini embed eder (`VITE_GRAFANA_*` env).

---

## Trace (Madde 10)

Akış:

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

Log satırlarında `traceId` ve `spanId` alanları — OpenSearch'te korelasyon için.

---

## Log pipeline (Madde 12–13)

```
Servis (finance / marketdata / notification)
    → Log4j2 (JSON console + KafkaLog appender, INFO+ varsayılan)
        → Kafka topic: application-logs
            → log-consumer-service (ApplicationLogsConsumer)
                → OpenSearch index: application-logs-yyyy-MM-dd
                    → OpenSearch Dashboards (filtreleme burada)
```

Konfigürasyon:

- Appender: `KafkaLogAppender` — finance, marketdata, notification
- XML: `log4j2-spring.xml` — `APP_LOG_KAFKA_MIN_LEVEL` (varsayılan **INFO**)
- Consumer: `log-consumer-service/.../ApplicationLogsConsumer.java`
- Access log: `RequestLoggingFilter` — `[REQUEST] method uri status= durationMs=` (INFO)

### Ortam değişkeni

| Değişken | Varsayılan | Açıklama |
|----------|------------|----------|
| `APP_LOG_KAFKA_MIN_LEVEL` | `INFO` | Kafka/OpenSearch'e giden minimum Log4j seviyesi. `WARN` = yalnızca uyarı/hata (eski davranış). |

Framework gürültüsü (kafka, hibernate, hikari) log4j2'de **WARN** ile sınırlı; iş ve `[REQUEST]` logları INFO ile merkeze gider.

DEBUG merkeze **gitmez** (Root level INFO).

### OpenSearch Dashboards — Discover

1. http://localhost:5601
2. Index pattern: `application-logs-*`, time field: `timestamp`
3. Time range: **Last 24 hours** (son 15 dk'da az kayıt olabilir)
4. Örnek sorgular:
   - `level:INFO AND message:"[REQUEST]"`
   - `serviceName:"finance-service" AND level:ERROR`
   - `correlationId:"<uuid>"`

### Log doğrulama

```powershell
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

```powershell
curl "http://localhost:9200/application-logs-*/_search?size=3&pretty"
```

JSON log alanları: `serviceName`, `level`, `message`, `traceId`, `spanId`, `correlationId`, `userId`, `username`, `actionType`

---

## Kabul kriterleri (smoke test)

| # | Kriter | Doğrulama |
|---|--------|-----------|
| 1 | Request count | Grafana panel — `http_server_requests_seconds_count` |
| 2 | Latency p95 | Grafana panel — sum/count ratio |
| 3 | Error rate | status>=500 metrikleri |
| 4 | Service health | `/actuator/health`, `up` metric |
| 5 | Trace uçtan uca | Grafana → Tempo datasource → trace drilldown |
| 6 | Log correlation | OpenSearch'te traceId filtresi |

Smoke komutları:

```powershell
docker compose config
docker compose up -d
curl http://localhost:9090/-/ready
curl http://localhost:3001/api/health
curl http://localhost:8085/actuator/prometheus
curl http://localhost:8083/actuator/prometheus
```

---

## Admin audit entegrasyonu

finance-service admin audit API:

- OpenSearch sorguları (audit log)
- Grafana public URL template'leri
- Tempo trace linkleri

Frontend: `AdminAudit.tsx` — embed paneller + harici Grafana linkleri.

Env: `APP_OBSERVABILITY_*`, `VITE_GRAFANA_*`

---

## Üretim notları

Yerel compose'ta:

- Grafana anonymous viewer **açık** — sadece demo/local
- Keycloak `start-dev` — production profili değil
- OpenSearch security plugin devre dışı

Production'da TLS, auth ve anonymous Grafana kapatılmalıdır.

---

[← Dokümantasyon hub](../README.md)
