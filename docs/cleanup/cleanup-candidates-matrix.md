# Cleanup Adaylari Matrisi

Bu dokuman kanitsiz silme yapmamak icin olusturuldu. Her aday once `deprecate` edilir, gozlem sonrasi silinir.

## Adaylar

## 1) `/finance/public` ve `/finance/secure`
- Dosya: `finance-service/src/main/java/com/nurseli/nrsfinanceportal/controller/FinanceController.java`
- Frontend referansi: bulunamadi
- Test referansi: bulunamadi
- Karar: **Deprecate** (silme yok)

## 2) `/internal/market/backfill/tcmb`
- Dosya: `marketdata/src/main/java/com/nurseli/marketdata/controller/BackfillController.java`
- Frontend referansi: bulunamadi
- Service referansi: dogrudan kanit yok
- Karar: **Profile'a al** + rol kisiti, gozlem sonrasi degerlendirme

## 3) `/internal/providers/health`
- Dosya: `marketdata/src/main/java/com/nurseli/marketdata/controller/ProviderHealthController.java`
- Frontend referansi: bulunamadi
- Karar: **Deprecate** ve internal admin endpoint olarak sinirla

## 4) Compose duplicate Kafka env keys
- Dosya: `docker-compose.yml`
- Not: `SPRING_KAFKA_BOOTSTRAP_SERVERS` ve `KAFKA_BOOTSTRAP_SERVERS` bir arada
- Karar: **Asamali normalize** (tek anahtara gecis canary sonrasi)

## Guvenli Temizlik Akisi
1. Deprecate annotation + release note
2. Access log ile 2 sprint gozlem
3. Cagri yoksa profile isolate
4. Sonraki asamada silme PR'i
