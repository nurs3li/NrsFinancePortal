# Javadoc — Java API dokümantasyonu

**Madde 20:** Kaynak koddaki `/** ... */` Javadoc yorumlarından HTML dokümantasyon üretilir.

REST HTTP sözleşmesi için **OpenAPI/Swagger** kullanın — Javadoc HTTP endpoint'leri değil, **Java sınıflarını** (controller, service, domain) dokümante eder.

---

## Hızlı üretim

### Windows (PowerShell)

```powershell
.\scripts\Generate-Javadoc.ps1
```

### Tüm platformlar (Maven wrapper)

```powershell
.\mvnw.cmd javadoc:javadoc -DskipTests
```

Linux/macOS:

```bash
./mvnw javadoc:javadoc -DskipTests
```

Temiz build:

```powershell
.\mvnw.cmd clean javadoc:javadoc -DskipTests
```

---

## Çıktı konumları

| Modül | HTML giriş sayfası |
|--------|-------------------|
| finance-service | `finance-service/target/reports/apidocs/index.html` |
| marketdata | `marketdata/target/reports/apidocs/index.html` |
| notification-service | `notification-service/target/reports/apidocs/index.html` |
| log-consumer-service | `log-consumer-service/target/reports/apidocs/index.html` |

Tarayıcıda `index.html` dosyasını açın — paket ağacı ve sınıf listesi görünür.

> **Not:** `target/` klasörü `.gitignore`'dadır. Teslim zip'ine HTML çıktısını **manuel kopyalayın** veya değerlendiriciye `mvn javadoc:javadoc` komutunu çalıştırmasını söyleyin.

---

## Tek modül

```powershell
.\mvnw.cmd -pl finance-service javadoc:javadoc -DskipTests
```

---

## Maven yapılandırması

Parent `pom.xml`:

- Plugin: `maven-javadoc-plugin` 3.11.2
- Java release: 21
- `failOnError: false` — eksik tag'ler build'i durdurmaz
- `show: protected` — protected üyeler de listelenir

---

## Javadoc vs OpenAPI

| | Javadoc | OpenAPI (Swagger) |
|---|---------|-------------------|
| **Ne dokümante eder?** | Java sınıfları, metotlar | HTTP endpoint'ler, request/response şeması |
| **Nerede görünür?** | `target/reports/apidocs/` | `/swagger-ui.html` |
| **Madde** | 20 | 19 |
| **Güncelleme** | Kaynak yorumları + build | SpringDoc annotation + runtime |

---

## Teslim / sunum checklist

- [ ] `.\scripts\Generate-Javadoc.ps1` hatasız tamamlandı
- [ ] En az bir modülün `index.html` tarayıcıda açıldı
- [ ] Sunumda finance-service Javadoc gösterildi
- [ ] OpenAPI (Swagger) ayrıca demo edildi

---

[← Dokümantasyon hub](../README.md) · [API dokümantasyonu →](./api/README.md)
