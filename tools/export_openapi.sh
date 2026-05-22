#!/usr/bin/env bash
# Export OpenAPI JSON from running services into docs/api/openapi/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

bash tools/generate_api_catalog.sh --live --export-openapi

echo "OpenAPI snapshots: docs/api/openapi/*.json"
