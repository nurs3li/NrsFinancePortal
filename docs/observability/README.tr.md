<p align="center">
  <img src="../../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# Gözlemlenebilirlik

NRS Finance Portal, uçtan uca bir gözlemlenebilirlik yığınıyla gelir: **OpenTelemetry**, **Prometheus**, **Grafana**, **Tempo**, **Kafka** ve **OpenSearch** (Dashboards ile birlikte).

---

## Bileşen özeti

| Bileşen | Rol | URL (Docker) |
|---------|-----|--------------|
| **OpenTelemetry Collector** | İz/metrik alımı | OTLP http :4318 |
| **Prometheus** | Metrik depolama | http://localhost:9090 |
| **Grafana** | Panolar + Tempo UI | http://localhost:3001 |
| **Tempo** | Dağıtık izleme backend'i | http://localhost:3200 |
| **OpenSearch** | Log depolama + arama | http://localhost:9200 |
| **OpenSearch Dashboards** | Log UI | http://localhost:5601 |
| **Kafka** | Olay/log taşıma | :9092 |
| **log-consumer-service** | Kafka → OpenSearch indeksleyici | http://localhost:8087 |

---

## Metrikler

Servisler Spring Boot Actuator + Micrometer ile metrik sunar:

```
GET /actuator/health
GET /actuator/prometheus
```

Prometheus scrape yapılandırması: `infra/prometheus/prometheus.yml`

OTel Collector metrik endpoint'i: `:8889`

### Grafana panosu

- Pano JSON: `infra/grafana/dashboards/nrs-observability.json`
- Provisioning: `infra/grafana/provisioning/`
- Yerel giriş: `admin / admin`
- Yerel/demo: denetim gömüleri için anonim görüntüleyici etkin olabilir

Frontend'deki Admin Denetim ekranı Grafana panellerini gömebilir (`VITE_GRAFANA_*`).

---

## İzler (traces)

Akış:

```
Spring Boot (Micrometer OTel köprüsü)
    → OTel Collector (:4318)
        → Tempo (iz depolama)
        → Prometheus (span metrikleri)
            → Grafana (görselleştirme)
```

Ortam (Docker):

```
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces
```

Uygulama logları OpenSearch'te korelasyon için `traceId` ve `spanId` alanlarını içerir.

---

## Loglar (Kafka → OpenSearch hattı)

```
Servis (finance / marketdata / notification)
    → Log4j2 (JSON konsol + Kafka appender, varsayılan INFO+)
        → Kafka konusu: application-logs
            → log-consumer-service (ApplicationLogsConsumer)
                → OpenSearch indeksi: application-logs-yyyy-MM-dd
                    → OpenSearch Dashboards (Discover)
```

Ana yapılandırma noktaları:

- Appender: `KafkaLogAppender` — finance, marketdata, notification
- Log4j2 yapılandırması: `log4j2-spring.xml` — `APP_LOG_KAFKA_MIN_LEVEL` (varsayılan **INFO**)
- Tüketici: `log-consumer-service/.../ApplicationLogsConsumer.java`
- Erişim logu: `RequestLoggingFilter` — `[REQUEST] method uri status= durationMs=` (INFO)

### Ortam değişkeni

| Değişken | Varsayılan | Açıklama |
|----------|------------|----------|
| `APP_LOG_KAFKA_MIN_LEVEL` | `INFO` | Kafka/OpenSearch'e iletilen minimum Log4j seviyesi. `WARN` yalnızca uyarı/hata iletir. |

Çerçeve gürültüsü (kafka, hibernate, hikari) genelde **WARN** ile sınırlanır; iş ve `[REQUEST]` logları **INFO** ile iletilir.

> **Tutarlılık:** [`log-consumer-service/README.tr.md`](../../log-consumer-service/README.tr.md) aynı varsayılanı (**INFO+**) kullanır. `APP_LOG_KAFKA_MIN_LEVEL` ile geçersiz kılın.

DEBUG logları varsayılan olarak iletilmez (kök seviye INFO).

### OpenSearch Dashboards — Discover

1. http://localhost:5601
2. İndeks deseni: `application-logs-*`, zaman alanı: `timestamp`
3. Zaman aralığı: **Son 24 saat**
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

Yaygın JSON alanları: `serviceName`, `level`, `message`, `traceId`, `spanId`, `correlationId`, `userId`, `username`, `actionType`

---

## Duman test kontrol listesi

| # | Kontrol | Nasıl doğrulanır |
|---|--------|-----------|
| 1 | İstek sayısı | Grafana paneli — `http_server_requests_seconds_count` |
| 2 | gecikme p95 | Grafana paneli — sum/count oranı |
| 3 | Hata oranı | HTTP 5xx metrikleri / panolar |
| 4 | Servis sağlığı | `/actuator/health`, `up` metriği |
| 5 | Uçtan uca izleme | Grafana → Tempo veri kaynağı → iz detayı |
| 6 | Log korelasyonu | OpenSearch'te `traceId` ile filtre |

Duman komutları:

```powershell
docker compose config
docker compose up -d
curl http://localhost:9090/-/ready
curl http://localhost:3001/api/health
curl http://localhost:8085/actuator/prometheus
curl http://localhost:8083/actuator/prometheus
```

---

## Admin denetim entegrasyonu

finance-service admin denetim API'si:

- OpenSearch sorguları (denetim logları)
- Grafana genel URL şablonları
- Tempo iz bağlantıları

Frontend: `AdminAudit.tsx` — gömülü paneller + harici Grafana bağlantıları.

<p align="center">
  <img src="../assets/gifs/features/admin-audit-grafana.gif" alt="Admin denetim — Grafana gömüsü" width="720" />
</p>

Ortam: `APP_OBSERVABILITY_*`, `VITE_GRAFANA_*`

---

## Üretim notları

Yerel Docker Compose yığınında:

- Grafana anonim görüntüleyici **etkin** olabilir — yalnızca demo/yerel
- Keycloak geliştirici dostu modda çalışır — üretim profili değildir
- OpenSearch güvenlik eklentileri yerel kolaylık için devre dışı olabilir

Üretimde TLS, kimlik doğrulama/yetkilendirme etkinleştirmeli ve anonim Grafana erişimini kapatmalısınız.

---

[← Dokümantasyon merkezi](../README.tr.md)
