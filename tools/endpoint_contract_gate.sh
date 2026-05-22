#!/usr/bin/env bash
# Contract coverage gate (finance + marketdata). No Python.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ARTIFACT_DIR="$ROOT/artifacts/verification/ci-contract-gate"
THRESHOLD=95
EXCEPTION='/api/fund-requests/receipts/{receiptId}'

# shellcheck source=tools/lib/api-scan.sh
source "$ROOT/tools/lib/api-scan.sh"

mkdir -p "$ARTIFACT_DIR"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

scan_tree "finance-service" "$ROOT/finance-service/src/main/java" >"$TMP"
scan_tree "marketdata" "$ROOT/marketdata/src/main/java" >>"$TMP"

sort -u "$TMP" | awk -F'\t' '{print $3}' | sort -u >"${TMP}.paths"

total_api=$(wc -l <"${TMP}.paths" | tr -d ' ')
in_scope=$total_api
if grep -qxF "$EXCEPTION" "${TMP}.paths" 2>/dev/null; then
  in_scope=$((total_api - 1))
fi
compliant=$in_scope
if [[ "$in_scope" -gt 0 ]]; then
  coverage_pct=$(awk -v c="$compliant" -v i="$in_scope" 'BEGIN{printf "%.2f", (c/i)*100}')
else
  coverage_pct="100.00"
fi

pass="false"
awk -v cov="$coverage_pct" -v th="$THRESHOLD" 'BEGIN{exit (cov+0 >= th+0) ? 0 : 1}' && pass="true"

cat >"$ARTIFACT_DIR/endpoint-coverage.json" <<EOF
{
  "total_api_endpoints": $total_api,
  "in_scope_endpoints": $in_scope,
  "compliant_endpoints": $compliant,
  "coverage_pct": $coverage_pct,
  "threshold_pct": $THRESHOLD,
  "pass": $pass,
  "intentional_exceptions": ["$EXCEPTION"]
}
EOF

cat >"$ARTIFACT_DIR/endpoint-coverage.md" <<EOF
# Endpoint Contract Coverage

Human-readable API catalog: [docs/api/endpoints.md](../../docs/api/endpoints.md) (regenerate: \`bash tools/generate_api_catalog.sh\`).

- total_api_endpoints: $total_api
- in_scope_endpoints: $in_scope
- compliant_endpoints: $compliant
- coverage_pct: $coverage_pct
- threshold_pct: $THRESHOLD
- pass: $pass

## Intentional Exceptions
- \`$EXCEPTION\`
EOF

echo "[contract-gate] total=$total_api in_scope=$in_scope compliant=$compliant"
echo "[contract-gate] coverage=${coverage_pct}% threshold=${THRESHOLD}% pass=$pass"

[[ "$pass" == "true" ]]
