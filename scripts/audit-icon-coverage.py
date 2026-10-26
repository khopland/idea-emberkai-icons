#!/usr/bin/env python3
"""List files whose current static mapping is generic or missing in one or more repos."""

from __future__ import annotations

import argparse
import os
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = ROOT / "src/main/resources"
SKIP_DIRS = {
    ".cache", ".git", ".gradle", ".idea", ".intellijPlatform", ".kotlin", ".venv",
    "build", "dist", "node_modules", "out", "Pods", "target", "vendor",
}
GENERIC_ICONS = {
    "code-gray.svg", "document.svg", "json.svg", "text.svg", "xml.svg", "yaml.svg",
}


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.search(r"(?<!\\)[=:\s]", line)
        if match:
            key = line[: match.start()].strip()
            value = line[match.end() :].strip()
        else:
            key, value = line, ""
        values[key] = value
    return values


def mappings() -> dict[str, str]:
    return {
        **load_properties(RESOURCE_DIR / "symbols/icons.properties"),
        **load_properties(RESOURCE_DIR / "emberkai/icons.properties"),
    }


def extension_candidates(name: str):
    current = name.lower()
    while True:
        yield current
        dot = current.find(".")
        if dot < 0:
            return
        current = current[dot + 1 :]


def parent_suffixes(relative_path: Path):
    parts = relative_path.parent.parts
    for start in range(len(parts) - 1, -1, -1):
        yield "/".join(parts[start:])


def find_static_icon(path: Path, repo: Path, entries: dict[str, str]) -> str | None:
    relative_path = path.relative_to(repo)
    name = path.name.lower()

    for suffix in parent_suffixes(relative_path):
        key = f"file.{suffix}/{name}"
        if key in entries:
            return entries[key]
    if f"file.{name}" in entries:
        return entries[f"file.{name}"]

    for current in extension_candidates(name):
        for suffix in parent_suffixes(relative_path):
            key = f"extension.{suffix}/{current}"
            if key in entries:
                return entries[key]

    for current in extension_candidates(name):
        key = f"extension.{current}"
        if key in entries:
            return entries[key]
    return None


def should_skip(directory: Path, repo: Path) -> bool:
    return any(part in SKIP_DIRS for part in directory.relative_to(repo).parts)


def audit(repo: Path, entries: dict[str, str]) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    for current_root, directories, filenames in os.walk(repo, followlinks=False):
        root = Path(current_root)
        directories[:] = [name for name in directories if not should_skip(root / name, repo)]
        for filename in filenames:
            path = root / filename
            if path.is_symlink():
                continue
            mapped_icon = find_static_icon(path, repo, entries)
            if mapped_icon is None:
                findings.append((path.relative_to(repo).as_posix(), "no static mapping"))
                continue
            icon_name = Path(mapped_icon).name
            if icon_name in GENERIC_ICONS:
                findings.append((path.relative_to(repo).as_posix(), f"generic icon: {icon_name}"))
    return sorted(findings)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repositories", nargs="*", type=Path, default=[Path.cwd()])
    parser.add_argument("--limit", type=int, default=100, help="maximum findings printed per repository (default: 100)")
    args = parser.parse_args()
    entries = mappings()

    limit = max(0, args.limit)
    for supplied_path in args.repositories:
        repo = supplied_path.resolve()
        if not repo.is_dir():
            parser.error(f"not a directory: {supplied_path}")
        findings = audit(repo, entries)
        print(f"{repo}: {len(findings)} coverage candidates")
        for relative, reason in findings[:limit]:
            print(f"  {relative}\t{reason}")
        if len(findings) > limit:
            print(f"  ... {len(findings) - limit} more")

    print("Context-sensitive rules are not evaluated; review candidates in the IDE before changing mappings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
