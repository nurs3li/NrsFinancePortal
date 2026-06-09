# Requirements compliance

Verification map for NRS Finance Portal delivery requirements (Items 14–22).

| Requirement | Where to verify |
|-------------|-----------------|
| **Item 21** — README & setup guide | [Root README](../README.md) + [getting-started.md](./getting-started.md) |
| **Item 22** — Unit / integration tests | [Testing](../README.md#testing) + CI badge + [getting-started §7](./getting-started.md#7-test-suite) |
| **Item 14** — Docker Compose stack | [`docker-compose.yml`](../docker-compose.yml) |
| **Item 15** — Microservices architecture | [System architecture](../README.md#system-architecture) + [architecture.md](./architecture.md) |
| **Item 16** — REST API & Swagger | [Service endpoints](../README.md#service-endpoints) + [api/README.md](./api/README.md) |
| **Item 17** — Authentication (Keycloak/JWT) | [Demo accounts](../README.md#step-5--open-the-app) + [security/README.md](./security/README.md) |
| **Item 18** — Observability | Grafana :3001 + [observability/README.md](./observability/README.md) |
| **Item 19** — Centralized logging | Kafka → OpenSearch — [getting-started §6](./getting-started.md#6-verify-the-log-pipeline-kafka--opensearch) |
| **Item 20** — CI pipeline | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |

---

[← Documentation hub](./README.md) · [Türkçe](./requirements-compliance.tr.md)
