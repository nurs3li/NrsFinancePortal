# API documentation

## Files

| File | Purpose |
|------|---------|
| [response-standardization-inventory.md](./response-standardization-inventory.md) | Unified `ApiResponse` / `ApiEnvelope` contract |
| `endpoints.md` (optional) | Regenerate with `bash tools/generate_api_catalog.sh` when you want a committed catalog |
| [openapi/](./openapi/) | Optional JSON snapshots from live Springdoc (`--export-openapi`) |

## Regenerate the catalog

**Static mode (CI-safe, no running services):**

```bash
bash tools/generate_api_catalog.sh
```

Scans `*Controller.java` in all four backends (bash + awk) and writes `endpoints.md`.

**Live mode (Springdoc merge, requires `curl` and `jq`):**

```bash
docker compose up -d
# wait for health, then:
bash tools/generate_api_catalog.sh --live
# or:
bash tools/export_openapi.sh
```

### Service URLs (OpenAPI)

| Service | Local dev | Docker host |
|---------|-----------|-------------|
| finance-service | http://localhost:8085/v3/api-docs | http://localhost:8085/v3/api-docs (maps container 8080) |
| marketdata | http://localhost:8086/v3/api-docs | http://localhost:8083/v3/api-docs |
| notification-service | http://localhost:8091/v3/api-docs | http://localhost:8089/v3/api-docs |
| log-consumer-service | http://localhost:8090/v3/api-docs | http://localhost:8087/v3/api-docs |

Swagger UI: same host/port with `/swagger-ui/index.html`.

## Review workflow

1. Run `bash tools/generate_api_catalog.sh` after controller changes.
2. Optionally commit `docs/api/endpoints.md` (and `openapi/*.json` if you used `--export-openapi`).
3. CI runs `tools/endpoint_contract_gate.sh` on controllers (no committed catalog required).

## Contract gate

```bash
bash tools/endpoint_contract_gate.sh
```

Checks finance + marketdata endpoint inventory metadata.  
CI artifact: `artifacts/verification/ci-contract-gate/endpoint-coverage.md`

## Priority sections (manual curation)

When enriching summaries or examples, prioritize:

- **Auth:** `/api/users/me`, `/api/public/register`
- **Portfolio:** `/api/portfolio/**`
- **Market:** finance `/api/market/**` proxy + marketdata `/api/market/**`, `/api/news/**`
- **Bank rates:** `/api/market/bank-rates/**`
- **Admin:** `/api/admin/**`

## Requirements

- **bash**, **awk**, **sort**, **find** (static catalog)
- **curl**, **jq** (optional `--live` / `export_openapi.sh`)
