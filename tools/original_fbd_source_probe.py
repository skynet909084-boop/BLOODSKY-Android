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

# Evidence extraction is intentionally conservative. Literal labels and call-site
# names are clues, not proofs of runtime pin direction or electrical semantics.
CONTRACT_SOURCES = ("FB_CTU.java", "FB_RS.java", "FB_TIMER.java", "FB_TRIGGER.java")
CONTRACT_LABELS = frozenset(("+", "-", "R", "S", "Q", "QN", "IN", "OUT", "PT",
                             "PV", "CU", "CD", "LD", "TON", "TOF", "TP", "CTU",
                             "RS", "SR", "AND", "OR", "BOOL", "FLOAT", "INT"))
CONTRACT_METHODS = frozenset(("getValue", "setValue", "getName", "setName",
                              "getInput", "getOutput", "onDraw", "drawText",
                              "invalidate", "setText"))
MAX_CONTRACT_OCCURRENCES = 64

# These are literal tokens observed in the SHA-pinned original source, not
# inferred executable contracts. Inputs/outputs, data types and priorities
# still require superclass/call-graph analysis and differential tests.
EXPECTED_ORIGINAL_LABEL_SEQUENCE = {
    "FB_CTU.java": ("CTU", "+", "-", "R", "Q"),
    "FB_RS.java": ("RS", "R", "S", "Q"),
    "FB_TIMER.java": ("TON", "IN", "Q", "TOF", "TP"),
}


def assert_pinned_original_labels(contracts):
    """Fail the pinned source audit when a published literal claim changes."""
    indexed = {row["archive_path"].rsplit("/", 1)[-1]: row for row in contracts}
    confirmed = []
    for filename, expected in EXPECTED_ORIGINAL_LABEL_SEQUENCE.items():
        if filename not in indexed:
            raise ValueError("Original FBD class missing: " + filename)
        actual = [row["value"] for row in indexed[filename]["literal_labels"]]
        unique = list(dict.fromkeys(actual))
        if unique != list(expected):
            raise ValueError("Original FBD label sequence mismatch: " + filename)
        confirmed.append({"class": filename, "sha256": indexed[filename]["sha256"],
                          "labels": list(expected),
                          "claim": "source literals only; directions and runtime semantics unverified"})
    return confirmed



def _java_visible_lines(lines):
    """Yield two masks per line: real string literals, and executable call text.

    Decompiled Java contains comments and string constants that can mention
    arbitrary pin names or methods. Neither is executable code. Retain quoted
    literals for pin-label evidence, but mask strings before identifying calls.
    Block-comment state crosses lines; original line numbers are preserved.
    """
    in_block_comment = False
    for line in lines:
        literal_view = []
        call_view = []
        in_string = False
        in_char = False
        escaped = False
        index = 0
        while index < len(line):
            char = line[index]
            if in_block_comment:
                if line.startswith("*/", index):
                    in_block_comment = False
                    index += 2
                else:
                    index += 1
                continue
            if in_string:
                literal_view.append(char)
                call_view.append(" ")
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                index += 1
                continue
            if in_char:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == "'":
                    in_char = False
                index += 1
                continue
            if line.startswith("//", index):
                break
            if line.startswith("/*", index):
                in_block_comment = True
                index += 2
                continue
            if char == '"':
                in_string = True
                literal_view.append(char)
                call_view.append(" ")
            elif char == "'":
                in_char = True
            else:
                literal_view.append(char)
                call_view.append(char)
            index += 1
        yield "".join(literal_view), "".join(call_view)


def contract_fingerprint(filename, raw):
    """Return source-hashed line positions for real short Java string literals.

    Ignore fake labels in comments and fake calls inside string constants.
    Do not infer the actual number/direction of pins from drawing labels.
    """
    if filename.rsplit("/", 1)[-1] not in CONTRACT_SOURCES:
        return None
    lines = raw.decode("utf-8", errors="replace").splitlines()
    labels = []
    calls = []
    for lineno, (literal_text, call_text) in enumerate(_java_visible_lines(lines), 1):
        for match in re.finditer(r'"([^"\\\n]{1,32})"', literal_text):
            literal = match.group(1)
            if literal in CONTRACT_LABELS and len(labels) < MAX_CONTRACT_OCCURRENCES:
                labels.append({"line": lineno, "value": literal})
        for match in re.finditer(r"\b([A-Za-z][A-Za-z0-9_]*)\s*\(", call_text):
            method = match.group(1)
            if method in CONTRACT_METHODS and len(calls) < MAX_CONTRACT_OCCURRENCES:
                calls.append({"line": lineno, "name": method})
    return {"archive_path": filename, "sha256": hashlib.sha256(raw).hexdigest(),
            "line_count": len(lines), "literal_labels": labels, "method_calls": calls,
            "interpretation": "source literals and calls only; not validated direction, priority, pin type or timer unit"}

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
    contracts = []
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
            if is_logic_java:
                fingerprint = contract_fingerprint(name, raw)
                if fingerprint is not None:
                    contracts.append(fingerprint)
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
    pinned_claims = (assert_pinned_original_labels(contracts)
                     if expected_sha256 == EXPECTED_SHA256 else [])
    return {
        "pinned_label_checks": pinned_claims,
        "source_archive": str(archive),
        "source_sha256": expected_sha256,
        "scope": "read-only source keyword audit; not executable Craft semantics or visual parity",
        "totals": totals,
        "markers_present": sorted(found_markers),
        "markers_absent": sorted(set(MARKERS) - found_markers),
        "evidence": evidence,
        "contracts": contracts,
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
    print("FBD class fingerprints: " + str(len(result["contracts"])))
    print("Report: " + str(args.output))


if __name__ == "__main__":
    main()
