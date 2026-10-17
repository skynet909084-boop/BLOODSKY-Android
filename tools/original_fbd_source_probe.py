#!/usr/bin/env python3
"""Read-only, reproducible FBD source index from the pinned original Craft zip.

Never executes, copies or modifies decompiled Java; emits short keyword-level
evidence with file+line+content SHA. The index is NOT proof of runtime parity.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import zipfile

EXPECTED_SHA256 = "316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd"
SOURCE_PREFIX = "jadx/sources/ru/himik/nightscream/kascada/Logic/"
MARKERS = ("FB_CTU", "FB_CTD", "CTU", "CTD", "FB_RS", "FB_SR",
           "TON", "TOF", "TP", "R_TRIG", "F_TRIG", "VarFloat",
           "getInput", "getOutput", "setValue", "getValue")
MAX_ARCHIVE_FILES = 5000
MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
MAX_EVIDENCE_FILES = 60


def safe_name(name):
    path = PurePosixPath(name)
    return (bool(name) and not name.startswith("/") and "\\" not in name
            and ":" not in path.parts[0]
            and all(part not in ("", ".", "..") for part in name.rstrip("/").split("/")))


def report_from_zip(archive, expected_sha256=EXPECTED_SHA256):
    archive = Path(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected_sha256:
        raise ValueError("Original Craft reference SHA256 mismatch")
    evidence = []
    totals = {"java": 0, "logic_java": 0, "layout_xml": 0}
    found_markers = set()
    with zipfile.ZipFile(archive) as src:
        infos = src.infolist()
        if len(infos) > MAX_ARCHIVE_FILES or sum(x.file_size for x in infos) > MAX_ARCHIVE_BYTES:
            raise ValueError("Original Craft source ZIP exceeds verified limits")
        for info in infos:
            if not safe_name(info.filename) or stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError("Unsafe ZIP entry")
        for info in infos:
            name = info.filename
            if not name.endswith((".java", ".xml")) or info.is_dir():
                continue
            if name.endswith(".java"):
                totals["java"] += 1
            is_logic_java = name.startswith(SOURCE_PREFIX) and name.endswith(".java")
            is_layout = name.endswith("fragment_component_logic_bar.xml") or name.endswith(
                "fragment_component_logic_bar_portrait.xml")
            if is_logic_java:
                totals["logic_java"] += 1
            elif is_layout:
                totals["layout_xml"] += 1
            else:
                continue
            if info.file_size > 1024 * 1024:
                raise ValueError("Oversized original Java/XML file: " + name)
            raw = src.read(info)
            lines = raw.decode("utf-8", errors="replace").splitlines()
            file_hits = []
            for lineno, line in enumerate(lines, 1):
                stripped = line.strip()
                matches = [marker for marker in MARKERS
                           if re.search(r"(?i)(?<![a-z0-9_])" + re.escape(marker)
                                        + r"(?![a-z0-9_])", line)]
                if matches:
                    found_markers.update(matches)
                    if len(file_hits) < 16:
                        file_hits.append({"line": lineno, "markers": matches,
                                          "source": stripped[:180]})
            # Include runtime signatures without blindly equating UI labels to behavior.
            interesting_path = bool(re.search(r"(?i)(ctu|ctd|timer|rs|sr|trig)", name))
            if (file_hits or interesting_path) and len(evidence) < MAX_EVIDENCE_FILES:
                evidence.append({
                    "archive_path": name,
                    "source_sha256": hashlib.sha256(raw).hexdigest(),
                    "hit_count": len(file_hits),
                    "hits": file_hits,
                })
    return {
        "source_archive": str(archive),
        "source_sha256": expected_sha256,
        "scope": "read-only source keyword audit; not executable Craft semantics or visual parity",
        "totals": totals,
        "markers_present": sorted(found_markers),
        "markers_absent": sorted(set(MARKERS) - found_markers),
        "evidence": evidence,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=Path("reference/craft-reference.zip"))
    parser.add_argument("--output", type=Path, default=Path("build-reports/original-fbd-source-probe.json"))
    args = parser.parse_args()
    result = report_from_zip(args.archive)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Craft FBD source paths: " + str(result["totals"]))
    print("Markers present: " + ", ".join(result["markers_present"]))
    print("Evidence files: " + str(len(result["evidence"])))
    print("Report: " + str(args.output))


if __name__ == "__main__":
    main()
