#!/usr/bin/env python3
"""Add /api/v1 aliases to public REST controllers using compile-time path literals."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SKIP_FILES = {
    "AdminController.java",
    "InternalUserController.java",
    "BackfillController.java",
    "ProviderHealthController.java",
    "HealthController.java",
    "InternalEmailController.java",
}

MODULES = [
    "finance-service/src/main/java",
    "marketdata/src/main/java",
    "notification-service/src/main/java",
]

MAPPING_ANNOTATIONS = (
    "RequestMapping",
    "GetMapping",
    "PostMapping",
    "PutMapping",
    "PatchMapping",
    "DeleteMapping",
)

API_PATHS_IMPORT = re.compile(r"^import .+\.config\.ApiPaths;\n", re.MULTILINE)
V1_WITH_LEGACY = re.compile(
    r'@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\(ApiPaths\.v1WithLegacy\("([^"]+)"\)\)'
)
PLAIN_API = re.compile(
    r'@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\("(/api/[^"]+)"\)'
)


def literal_paths(suffix: str) -> str:
    if not suffix.startswith("/"):
        suffix = "/" + suffix
    return '{{"/api/v1{}", "/api{}"}}'.format(suffix, suffix)


def replace_mapping_annotation(content: str) -> str:
    """Replace ApiPaths.v1WithLegacy(...) and plain /api/... mappings with dual literals."""
    content = V1_WITH_LEGACY.sub(
        lambda m: f"@{m.group(1)}({literal_paths(m.group(2))})", content
    )

    for ann in MAPPING_ANNOTATIONS:
        pattern = rf'@{ann}\("(/api/[^"]+)"\)'

        def repl(match: re.Match[str], annotation: str = ann) -> str:
            full_path = match.group(1)
            if not full_path.startswith("/api/"):
                return match.group(0)
            suffix = full_path[4:]
            return f"@{annotation}({literal_paths(suffix)})"

        content = re.sub(pattern, repl, content)

    return content


def strip_controller_api_paths_import(content: str) -> str:
    return API_PATHS_IMPORT.sub("", content)


def process_file(path: Path) -> bool:
    if path.name in SKIP_FILES:
        return False
    original = path.read_text(encoding="utf-8")
    updated = replace_mapping_annotation(original)
    updated = strip_controller_api_paths_import(updated)
    if updated == original:
        return False
    path.write_text(updated, encoding="utf-8")
    return True


def main() -> None:
    changed: list[str] = []
    for module_root in MODULES:
        base = ROOT / module_root
        for path in sorted(base.rglob("*Controller.java")):
            if process_file(path):
                changed.append(str(path.relative_to(ROOT)))
    print(f"Updated {len(changed)} controller files")
    for item in changed:
        print(f"  - {item}")


if __name__ == "__main__":
    main()
