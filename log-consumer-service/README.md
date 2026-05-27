# log-consumer-service

Kafka log tüketici servisi — uygulama loglarını OpenSearch'e indeksler (Madde 13).

---

## Özet

| Özellik | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Docker port** | 8087 |
| **Yerel port** | 8090 |
| **Swagger** | http://localhost:8087/swagger-ui.html |

---

## Sorumluluklar

1. **Application logs:** Kafka topic `application-logs` → OpenSearch index `application-logs-yyyy-MM-dd`
2. Index initializer — günlük index şablonu
3. (Varsa) transaction/event mesajları için ek consumer'lar

---

## Veri akışı

```
finance-service  ─┐
marketdata         ─┼─► Log4j2 KafkaLogAppender (WARN+)
notification-service─┘
         │
         ▼
    Kafka (application-logs)
         │
         ▼
  log-consumer-service
         │
         ▼
    OpenSearch (:9200)
```

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| Kafka | Log mesaj kaynağı |
| OpenSearch | Log depolama |
| Redis | Opsiyonel dedup/cache |

---

## Çalıştırma

### Docker Compose

```powershell
docker compose up -d log-consumer-service
```

Kafka ve OpenSearch ayakta olmalı.

---

## Doğrulama

```powershell
curl http://localhost:8087/actuator/health
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

Integration test: `ApplicationLogsFlowIntegrationTest` — Kafka → OpenSearch uçtan uca.

---

## Test

CI’da `mvn test -pl log-consumer-service`.

---

## Ortam değişkenleri (Docker)

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=nrs-kafka:9093
OPENSEARCH_HOST=nrs-opensearch
OPENSEARCH_PORT=9200
```

---

## Dokümantasyon

- [Observability](../docs/observability/README.md)
- [Kök README](../README.md)
