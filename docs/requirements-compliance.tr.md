# Proje isterleri uyumu

NRS Finance Portal teslim isterleri (Madde 14–22) için doğrulama haritası.

| Madde | Doğrulama yeri |
|-------|----------------|
| **Madde 21** — README ve kurulum rehberi | [Kök README](../README.tr.md) + [getting-started.tr.md](./getting-started.tr.md) |
| **Madde 22** — Birim / entegrasyon testleri | [Test](../README.tr.md#test) + CI rozeti + [getting-started §7](./getting-started.tr.md#7-test-suite) |
| **Madde 14** — Docker Compose yığını | [`docker-compose.yml`](../docker-compose.yml) |
| **Madde 15** — Mikroservis mimarisi | [Sistem mimarisi](../README.tr.md#sistem-mimarisi) + [architecture.tr.md](./architecture.tr.md) |
| **Madde 16** — REST API ve Swagger | [Servis uç noktaları](../README.tr.md#servis-uc-noktalari) + [api/README.tr.md](./api/README.tr.md) |
| **Madde 17** — Kimlik doğrulama (Keycloak/JWT) | [Demo hesapları](../README.tr.md#adım-5--uygulamayı-açma) + [security/README.tr.md](./security/README.tr.md) |
| **Madde 18** — Gözlemlenebilirlik | Grafana :3001 + [observability/README.tr.md](./observability/README.tr.md) |
| **Madde 19** — Merkezi loglama | Kafka → OpenSearch — [getting-started §6](./getting-started.tr.md#6-log-pipeline-doğrulama-kafka--opensearch) |
| **Madde 20** — CI hattı | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |

---

[← Dokümantasyon merkezi](./README.tr.md) · [English](./requirements-compliance.md)
