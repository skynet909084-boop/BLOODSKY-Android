#!/usr/bin/env python3
"""Audit imported original drawable resources against runtime Android references.

A compiled resource is not necessarily wired to a view. This report distinguishes
available original PNGs from references in BLOODSKY source, without claiming UI parity.
"""
import argparse
from pathlib import Path
import re

DYNAMIC = re.compile(r'getIdentifier\(\s*"kc_"\s*\+\s*([A-Za-z_][A-Za-z_0-9]*)')
DIRECT = re.compile(r'\bR\.drawable\.kc_([a-z][a-z0-9_]*)\b')
LITERAL = re.compile(r'"kc_([a-z][a-z0-9_]*)"')
VALID = re.compile(r'^[a-z][a-z0-9_]*$')


def audit(original_drawables: Path, source_root: Path, report_file: Path):
    originals = sorted(p for p in original_drawables.glob("*.png")
                       if not p.name.endswith(".9.png") and VALID.fullmatch(p.stem))
    if len(originals) < 10:
        raise ValueError("Original drawable source is missing or unexpectedly small")
    java_files = sorted(source_root.rglob("*.java"))
    source = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in java_files)
    direct = set(DIRECT.findall(source)) | set(LITERAL.findall(source))
    dynamic = sorted(set(DYNAMIC.findall(source)))
    wired = sorted(p.stem for p in originals if p.stem in direct)
    unwired = sorted(p.stem for p in originals if p.stem not in direct)
    lines = [
        "# BLOODSKY — original drawable wiring audit",
        "",
        "Original APK drawables copied into Android resources are **available**,",
        "but this report only proves explicit source references, not runtime display.",
        "Dynamic lookup and indirect references require manual review.",
        "",
        f"Original regular PNG files: **{len(originals)}**.",
        f"Explicitly referenced by name: **{len(wired)}**.",
        f"No explicit literal reference: **{len(unwired)}**.",
        "",
        "## Explicit source references", "",
    ]
    lines += [f"- `kc_{name}`" for name in wired] or ["None."]
    lines += ["", "## Not explicitly referenced (review before claiming parity)", ""]
    lines += [f"- `kc_{name}`" for name in unwired] or ["None."]
    lines += ["", "## Dynamic getIdentifier variables", ""]
    lines += [f"- `{name}`" for name in dynamic] or ["None."]
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(originals), len(wired)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("original_drawables", type=Path)
    parser.add_argument("java_source_root", type=Path)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    print("Original PNGs, explicit references:",
          *audit(args.original_drawables, args.java_source_root, args.report))
