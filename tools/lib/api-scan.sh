#!/usr/bin/env bash
# Emit TSV lines: service<TAB>method<TAB>path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AWK="$SCRIPT_DIR/scan-controller.awk"

scan_tree() {
  local service="$1"
  local dir="$2"
  [[ -d "$dir" ]] || return 0
  find "$dir" -name '*Controller.java' -print0 | while IFS= read -r -d '' f; do
    awk -v svc="$service" -f "$AWK" "$f"
  done
}

scan_all() {
  scan_tree "finance-service" "$ROOT/finance-service/src/main/java"
  scan_tree "marketdata" "$ROOT/marketdata/src/main/java"
  scan_tree "notification-service" "$ROOT/notification-service/src/main/java"
  scan_tree "log-consumer-service" "$ROOT/log-consumer-service/src/main/java"
}
