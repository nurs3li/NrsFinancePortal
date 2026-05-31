<p align="center">
  <img src="../docs/assets/gifs/nrs-brand-hero.gif" alt="NRS Finance Portal" width="360" />
</p>

<p align="center"><strong>Languages / Diller:</strong> <a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a></p>

---

# log-consumer-service

Kafka log tüketici servisi — uygulama loglarını OpenSearch'e indeksler (loglama hattı gereksinimi).

---

## Özet

| Öğe | Değer |
|---------|-------|
| **Java** | 21 |
| **Spring Boot** | 3.5.5 |
| **Docker portu** | 8087 |
| **Yerel port** | 8090 |
| **Swagger** | http://localhost:8087/swagger-ui.html |

---

## Sorumluluklar

1. Kafka konusu `application-logs` üzerinden **uygulama loglarını tüketmek**
2. Günlük indeksler kullanarak logları OpenSearch'e **indekslemek**: `application-logs-yyyy-MM-dd`
3. İndeks başlatma/şablonlarının mevcut olduğundan emin olmak (günlük rollover deseni)
4. (İsteğe bağlı) etkinleştirilmişse işlem/olay akışları için ek tüketiciler

---

## Veri akışı

<p align="center">
  <img src="../docs/assets/images/observability/log-pipeline-kafka-opensearch.png" alt="Log pipeline — finance, marketdata, notification → Kafka → log-consumer → OpenSearch" width="820" />
</p>

Kafka'ya iletilen minimum seviye: **`APP_LOG_KAFKA_MIN_LEVEL=INFO`** (varsayılan). [`docs/observability/README.tr.md`](../docs/observability/README.tr.md) ile aynı.

---

## Bağımlılıklar

| Bileşen | Amaç |
|---------|------|
| Kafka | Log olay kaynağı |
| OpenSearch | Log depolama + arama |
| Redis | İsteğe bağlı dedup/önbellek (yapılandırılmışsa) |

---

## Çalıştırma

### Docker Compose

```powershell
docker compose up -d log-consumer-service
```

Kafka ve OpenSearch ayakta olmalıdır (tam yığın çalıştırıldığında Docker Compose bunları da başlatır).

Servis URL: http://localhost:8087  
Sağlık: http://localhost:8087/actuator/health

---

## Doğrulama

```powershell
curl http://localhost:8087/actuator/health
curl "http://localhost:9200/_cat/indices?v" | findstr application-logs
```

İlk loglar üretildikten sonra `application-logs-YYYY-MM-DD` benzeri indeksler görmelisiniz.

İpucu: arayüzden biraz trafik üretin, ardından birkaç belge sorgulayın:

```powershell
curl "http://localhost:9200/application-logs-*/_search?size=3&pretty"
```

Entegrasyon testi: `ApplicationLogsFlowIntegrationTest` — uçtan uca Kafka → OpenSearch.

---

## Test

CI: `mvn test -pl log-consumer-service`

---

## Ortam değişkenleri (Docker)

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=nrs-kafka:9093
OPENSEARCH_HOST=nrs-opensearch
OPENSEARCH_PORT=9200
```

Yaygın geçersiz kılmalar:

| Değişken | Amaç |
|----------|---------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Tüketicinin kullandığı Kafka broker |
| `OPENSEARCH_HOST` / `OPENSEARCH_PORT` | OpenSearch hedefi |

---

## Sorun giderme

<details>
<summary><strong>İndeks oluşturulmuyor</strong></summary>

- Kafka ve OpenSearch'in konteynerden erişilebilir olduğunu doğrulayın
- Tüketici loglarını kontrol edin:

```powershell
docker compose logs -f log-consumer-service
```

</details>

<details>
<summary><strong>OpenSearch ayakta ama aramalar 0 sonuç döndürüyor</strong></summary>

- Üst servislerin gerçekten Kafka'ya log gönderdiğinden emin olun (Log4j2 Kafka appender etkin)
- Trafik üretin (UI sayfaları / API çağrıları), sonra tekrar deneyin:

```powershell
curl "http://localhost:9200/application-logs-*/_search?size=3&pretty"
```

</details>

---

## Dokümantasyon

- [Gözlemlenebilirlik](../docs/observability/README.tr.md)
- [Kök README](../README.tr.md)
