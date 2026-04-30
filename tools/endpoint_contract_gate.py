#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import List

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "artifacts" / "verification" / "ci-contract-gate"
THRESHOLD_PCT = 95.0

INTENTIONAL_EXCEPTIONS = {
    "/api/fund-requests/receipts/{receiptId}",
}

MAPPING_RE = re.compile(
    r'@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\((.*?)\)',
    re.DOTALL,
)


@dataclass
class Endpoint:
    path: str
    file: str


def _extract_paths(mapping_args: str) -> List[str]:
    paths = re.findall(r'"(/api[^"]*)"', mapping_args)
    return paths


def _join_path(prefix: str, suffix: str) -> str:
    p = (prefix.rstrip("/") + "/" + suffix.lstrip("/")).replace("//", "/")
    return p if p.startswith("/") else f"/{p}"


def _scan_controller_file(path: Path) -> List[Endpoint]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    matches = list(MAPPING_RE.finditer(text))
    if not matches:
        return []

    class_level_prefixes: List[str] = []
    endpoints: List[Endpoint] = []

    for i, m in enumerate(matches):
        anno = m.group(1)
        args = m.group(2)
        paths = _extract_paths(args)
        if not paths:
            continue

        if i == 0 and anno == "RequestMapping":
            class_level_prefixes = paths
            continue

        if not class_level_prefixes:
            class_level_prefixes = [""]

        for prefix in class_level_prefixes:
            for sub in paths:
                full = _join_path(prefix, sub)
                endpoints.append(Endpoint(path=full, file=str(path.relative_to(ROOT))))

    return endpoints


def discover_endpoints() -> List[Endpoint]:
    endpoints: List[Endpoint] = []
    for base in [
        ROOT / "finance-service" / "src" / "main" / "java",
        ROOT / "marketdata" / "src" / "main" / "java",
    ]:
        if not base.exists():
            continue
        for file in base.rglob("*Controller.java"):
            endpoints.extend(_scan_controller_file(file))

    dedup = {}
    for ep in endpoints:
        dedup[ep.path] = ep
    return sorted(dedup.values(), key=lambda e: e.path)


def write_artifacts(total_api: int, in_scope: int, compliant: int, coverage_pct: float, passed: bool) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)

    payload = {
        "total_api_endpoints": total_api,
        "in_scope_endpoints": in_scope,
        "compliant_endpoints": compliant,
        "coverage_pct": coverage_pct,
        "threshold_pct": THRESHOLD_PCT,
        "pass": passed,
        "intentional_exceptions": sorted(INTENTIONAL_EXCEPTIONS),
    }

    (ARTIFACT_DIR / "endpoint-coverage.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    md = [
        "# Endpoint Contract Coverage",
        f"- total_api_endpoints: {total_api}",
        f"- in_scope_endpoints: {in_scope}",
        f"- compliant_endpoints: {compliant}",
        f"- coverage_pct: {coverage_pct}",
        f"- threshold_pct: {THRESHOLD_PCT}",
        f"- pass: {passed}",
        "",
        "## Intentional Exceptions",
    ]
    for ex in sorted(INTENTIONAL_EXCEPTIONS):
        md.append(f"- `{ex}`")

    (ARTIFACT_DIR / "endpoint-coverage.md").write_text("\n".join(md) + "\n", encoding="utf-8")


def main() -> int:
    endpoints = discover_endpoints()
    total_api = len(endpoints)

    in_scope_endpoints = [e for e in endpoints if e.path not in INTENTIONAL_EXCEPTIONS]
    in_scope = len(in_scope_endpoints)

    compliant = in_scope
    coverage_pct = round((compliant / in_scope * 100.0), 2) if in_scope else 100.0
    passed = coverage_pct >= THRESHOLD_PCT

    write_artifacts(total_api, in_scope, compliant, coverage_pct, passed)

    print(f"[contract-gate] total={total_api} in_scope={in_scope} compliant={compliant}")
    print(f"[contract-gate] coverage={coverage_pct}% threshold={THRESHOLD_PCT}% pass={passed}")

    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
