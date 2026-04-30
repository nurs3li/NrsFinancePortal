# Observability Kabul Matrisi

Bu matris, Finans Portal icin OTel + log + metrik kabul kriterlerini dogrulamak icin kullanilir.

## Kriterler

1. **Request Count**
   - Kaynak: Prometheus `http_server_requests_seconds_count`
   - Dashboard: `infra/grafana/dashboards/nrs-observability.json`

2. **Response Time (Latency)**
   - Kaynak: `http_server_requests_seconds_sum / count`
   - Hedef: servis bazli p95 gorunebilir olmasi

3. **Error Rate**
   - Kaynak: `status>=500` etiketli request metricleri
   - Hedef: servis bazli izlenebilir panel

4. **Service Health**
   - Kaynak: `/actuator/health` ve `up` metrici
   - Hedef: tum servisler icin panelde gorunur olmasi

5. **Trace/Span Uctan Uca**
   - Kaynak: OTel -> Tempo
   - Hedef: Grafana Tempo datasource uzerinden trace drilldown

6. **Log Correlation**
   - Kaynak: JSON log satirlari (`service`, `traceId`, `spanId`, `correlationId`)
   - Hedef: OpenSearch'te bu alanlarla filtrelenebilirlik

## Smoke Dogrulama Komutlari

- `docker compose config`
- `docker compose up -d`
- `curl http://localhost:9090/-/ready`
- `curl http://localhost:3001/api/health`
- `curl http://localhost:8085/actuator/prometheus`
- `curl http://localhost:8083/actuator/prometheus`


