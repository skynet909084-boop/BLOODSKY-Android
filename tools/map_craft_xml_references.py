#!/usr/bin/env python3
"""Inventory original Android XML resource references and their resolved targets.

Reads the extracted apktool/res tree. Produces a source-traceable report;
it does not claim that a preserved resource is wired into BLOODSKY.
"""
import argparse
from collections import Counter, defaultdict
from pathlib import Path
import re

REFERENCE = re.compile(r"(?<![A-Za-z0-9_])@(?:[+*])?(?P<kind>layout|menu|drawable|mipmap|anim|animator|raw|color|font|xml|string|style|dimen|array|id)/(?P<name>[A-Za-z0-9_]+)")
FILE_TYPES = {"layout", "menu", "drawable", "mipmap", "anim", "animator", "raw", "color", "font", "xml"}


def scan(res: Path):
    index = defaultdict(list)
    for file in sorted(res.rglob("*")):
        if file.is_file():
            folder = file.parent.name.split("-")[0]
            if folder in FILE_TYPES:
                index[(folder, file.stem)].append(file.relative_to(res).as_posix())
    references = defaultdict(set)
    for file in sorted(res.rglob("*.xml")):
        rel = file.relative_to(res).as_posix()
        content = file.read_text(encoding="utf-8", errors="replace")
        for match in REFERENCE.finditer(content):
            references[(match.group("kind"), match.group("name"))].add(rel)
    return index, references


def report(res: Path, output: Path):
    if not res.is_dir():
        raise ValueError("Original apktool/res directory missing")
    index, refs = scan(res)
    missing = [(kind, name) for kind, name in refs if kind in FILE_TYPES and (kind, name) not in index]
    # Values-based and generated resources can legitimately be defined in values XML.
    counts = Counter(kind for kind, name in refs)
    lines = [
        "# KaScada Craft 1.0.7 — original XML resource reference map", "",
        "Automatically extracted from original apktool XML. This maps source references,",
        "not yet implemented BLOODSKY UI bindings.", "",
        f"Original indexed file resources: **{sum(len(x) for x in index.values())}**.",
        f"Distinct XML references: **{len(refs)}**.", "",
        "## References by type", "",
        "| Type | Distinct references |", "|---|---:|",
    ]
    lines += [f"| {kind} | {count} |" for kind, count in sorted(counts.items())]
    lines += ["", "## File-backed references", "",
              "| Original reference | Original files | Referenced from |",
              "|---|---|---|"]
    for kind, name in sorted(refs):
        if kind not in FILE_TYPES:
            continue
        files = ", ".join(index.get((kind, name), [])) or "(not found as file)"
        from_files = ", ".join(sorted(refs[(kind, name)]))
        lines.append(f"| `@{kind}/{name}` | {files} | {from_files} |")
    lines += ["", "## Unresolved file-backed references (requires review)", ""]
    lines += [f"- `@{kind}/{name}`" for kind, name in sorted(missing)] or ["None."]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(refs), len(missing)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("original_res", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print("References, unresolved:", *report(args.original_res, args.output))
