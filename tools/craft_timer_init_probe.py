#!/usr/bin/env python3
"""Inspect original timer constructors, mode setters and time-unit constant.

No execution or redistribution of the original archive. All evidence is
bounded, SHA-pinned, with file names and original 1-based line numbers.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import zipfile

from original_fbd_source_probe import (
    EXPECTED_SHA256, SOURCE_PREFIX, _java_visible_lines, safe_name,
)

METHODS = {
    "FB_TIMER.java": ("FB_TIMER", "setActionExecute", "setTypeTime", "getOut"),
    "FB_TRIGGER.java": ("FB_TRIGGER", "setActionExecute", "getOut"),
}
SYMBOL = "SILABS_CP2102"


def excerpt_method(lines, name, limit=90):
    visible = list(_java_visible_lines(lines))
    calls = []
    method_pattern = re.compile(r"\b" + re.escape(name) + r"\s*\(")
    for start, (_, executable) in enumerate(visible):
        if not method_pattern.search(executable):
            continue
        if not (re.search(r"\b(?:public|protected|private)\b", executable)
                or name.startswith("FB_")):
            continue
        source = []
        started = False
        depth = 0
        for at in range(start, min(len(visible), start + limit)):
            literal, masked = visible[at]
            if "{" in masked:
                started = True
            if not started:
                # Method declaration may span several lines.
                source.append({"line": at + 1, "code": literal.strip()[:200]})
                continue
            depth += masked.count("{") - masked.count("}")
            source.append({"line": at + 1, "code": literal.strip()[:200]})
            if depth <= 0:
                break
        if started:
            calls.append({"method": name, "start_line": start + 1,
                          "source_lines": source, "truncated": len(source) >= limit})
    return calls[:5]


def report(archive_path, expected_sha=EXPECTED_SHA256):
    archive_path = Path(archive_path)
    if hashlib.sha256(archive_path.read_bytes()).hexdigest() != expected_sha:
        raise ValueError("Wrong pinned Craft source archive SHA256")
    entries = []
    constant = []
    with zipfile.ZipFile(archive_path) as z:
        infos = z.infolist()
        if (len(infos) > 5000 or sum(info.file_size for info in infos) > 64*1024*1024
                or any(not safe_name(i.filename) or stat.S_ISLNK(i.external_attr >> 16)
                       for i in infos)):
            raise ValueError("Unsafe Craft ZIP archive")
        for info in infos:
            name = info.filename
            base = name.split("/")[-1]
            is_timer = name.startswith(SOURCE_PREFIX) and base in METHODS
            is_usb_id = base == "UsbId.java"
            if not (is_timer or is_usb_id) or info.is_dir():
                continue
            if info.file_size > 1024*1024:
                raise ValueError("Original source file too large")
            raw = z.read(info)
            lines = raw.decode("utf-8", errors="replace").splitlines()
            if is_timer:
                for method in METHODS[base]:
                    for piece in excerpt_method(lines, method):
                        entries.append({"class": name, "sha256": hashlib.sha256(raw).hexdigest(),
                                        **piece})
            if is_usb_id:
                for i, (literal, executable) in enumerate(_java_visible_lines(lines), 1):
                    if SYMBOL not in executable:
                        continue
                    match = re.search(r"\b" + SYMBOL + r"\s*=\s*(0[xX][0-9a-fA-F]+|\d+)", executable)
                    if match:
                        constant.append({"class": name, "line": i,
                                         "sha256": hashlib.sha256(raw).hexdigest(),
                                         "value": int(match.group(1), 0)
                                         if match.group(1).lower().startswith("0x")
                                         else int(match.group(1), 10)})
    if not entries:
        raise ValueError("FB_TIMER/FB_TRIGGER methods absent from pinned source")
    distinct = set(row["value"] for row in constant)
    resolved = distinct.pop() if len(distinct) == 1 else None
    return {
        "archive_sha256": expected_sha,
        "methods": entries,
        "symbol": SYMBOL,
        "unit3_value": resolved,
        "unit3_claim": "numeric declaration in pinned original UsbId.java" if resolved is not None
                       else "unverified: no unique pinned definition",
        "unit3_declarations": constant,
        "remaining": "TP initial Q and action-mode transition semantics need constructor/setter review",
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--archive", type=Path, default=Path("reference/craft-reference.zip"))
    ap.add_argument("--output", type=Path, default=Path("build-reports/craft-timer-init-evidence.json"))
    args = ap.parse_args()
    data = report(args.archive)
    args.output.parent.mkdir(exist_ok=True, parents=True)
    args.output.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Source-bound timer methods:", len(data["methods"]))
    print("Verified unit-3 multiplier:", data["unit3_value"] or "UNVERIFIED")
    print("Report:", args.output)


if __name__ == "__main__":
    main()
