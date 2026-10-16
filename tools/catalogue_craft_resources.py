#!/usr/bin/env python3
"""Generate a reproducible original-resource catalogue for UI parity work."""
import argparse
from collections import Counter
import json
from pathlib import Path


def catalogue(original_assets: Path, output: Path):
    manifest = json.loads((original_assets / "MANIFEST.json").read_text(encoding="utf-8"))
    files = manifest["files"]
    categories = Counter()
    for entry in files:
        p = Path(entry["path"])
        if p.parts[0] == "res":
            categories[p.parts[1]] += 1
        else:
            categories["assets/" + (p.parts[1] if len(p.parts) > 2 else "(root)")] += 1
    lines = [
        "# KaScada Craft 1.0.7 — extracted resource catalogue",
        "",
        "Generated from the decoded original APK, not manually estimated.",
        f"Total original files: **{len(files)}**.",
        "",
        "## Resource groups",
        "",
        "| Original folder | Files |",
        "|---|---:|",
    ]
    lines += [f"| \\{name} | {count} |".replace("\\", "`") for name, count in sorted(categories.items())]
    lines += [
        "",
        "## Source files (path, bytes, SHA-256)",
        "",
        "| Original relative path | Bytes | SHA-256 |",
        "|---|---:|---|",
    ]
    for entry in files:
        lines.append(f"| `{entry['path']}` | {entry['size']} | `{entry['sha256']}` |")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(files)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("original_assets", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print("Catalogued original files:", catalogue(args.original_assets, args.output))
