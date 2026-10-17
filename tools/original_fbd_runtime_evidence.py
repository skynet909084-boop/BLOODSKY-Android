#!/usr/bin/env python3
"""Read-only, hash-pinned evidence for remaining original Craft FBD logic gaps.

Only reads user-supplied source archive; does not execute untrusted Java.
Outputs selected methods/branches and referenced superclasses as concise,
line-numbered *evidence*, never inferred runtime contracts.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from original_fbd_source_probe import (
    EXPECTED_SHA256, SOURCE_PREFIX, _java_visible_lines, safe_name,
)

TARGETS = ("FB_CTU.java", "FB_RS.java", "FB_TIMER.java", "FB_TRIGGER.java")
OPTIONAL = ("FB_BLINK.java", "FB_CTD.java", "FB_SR.java")
MAX_ARCHIVE_FILES = 5000
MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
MAX_EVIDENCE_LINES = 96
EVIDENCE = re.compile(
    r"\b(?:class|extends|implements|if|else|switch|case|return|"
    r"getValue|setValue|getInput|getOutput|setInput|setOutput|"
    r"currentTimeMillis|elapsedRealtime|SystemClock|"
    r"counter|count|timer|start|reset|trigger|rising|falling|"
    r"increment|decrement|run|execute|calculate|update)\b|"
    r"\b(?:public|protected|private)\b.*\(",
    re.IGNORECASE,
)
EXTENDS = re.compile(r"\bclass\s+\w+\s+extends\s+([A-Za-z_$][\w.$]*)")
IMPORT = re.compile(r"^\s*import\s+([A-Za-z_$][\w.$]*);", re.MULTILINE)
EXECUTE = re.compile(r"\bpublic\s+void\s+f\s*\(\s*\)\s*\{")


def read_java(archive, relative_name):
    if relative_name not in archive.namelist():
        return None
    raw = archive.read(relative_name)
    if len(raw) > 1024 * 1024:
        raise ValueError("Original FBD Java class exceeds 1 MiB")
    return raw


def inspect_class(path, raw, max_lines=MAX_EVIDENCE_LINES):
    lines = raw.decode("utf-8", errors="replace").splitlines()
    visible = list(_java_visible_lines(lines))
    code = "\n".join(exec_view for _, exec_view in visible)
    supermatch = EXTENDS.search(code)
    superclass = supermatch.group(1) if supermatch else None
    imports = [name for name in IMPORT.findall(code)
               if ".Logic." in name or ".logic." in name][:25]
    # Capture only the compact execution routine f(), not the whole file.
    # decompiled source may use meaningless names but branch order still matters.
    execution = []
    for index, (_, executable) in enumerate(visible):
        if EXECUTE.search(executable):
            brace_depth = 0
            for j in range(index, min(index + 130, len(visible))):
                literal, statement = visible[j]
                brace_depth += statement.count("{") - statement.count("}")
                execution.append({"line": j + 1, "code": literal.strip()[:180]})
                if brace_depth <= 0:
                    break
            break
    candidates = []
    for index, (literal, executable) in enumerate(visible):
        if EVIDENCE.search(executable):
            # Preserve source-authored short string labels, but strip comments
            # and avoid accidentally emitting thousands of bytes of Java.
            candidates.append({"line": index + 1,
                               "code": literal.strip()[:160]})
    if len(candidates) > max_lines:
        # Evenly sample *whole* class so timer-mode endings are visible too.
        count = len(candidates)
        candidates = [candidates[(i * (count - 1)) // (max_lines - 1)]
                      for i in range(max_lines)]
    return {"path": path, "sha256": hashlib.sha256(raw).hexdigest(),
            "line_count": len(lines), "extends": superclass,
            "imports": imports, "execution": execution,
            "matched_lines": len([1 for _, execution in visible if EVIDENCE.search(execution)]),
            "evidence": candidates,
            "disclaimer": "static Java control-flow hints; priority, signal types and units unproven"}


def generate_report(archive_path, expected_sha256=EXPECTED_SHA256):
    archive_path = Path(archive_path)
    if hashlib.sha256(archive_path.read_bytes()).hexdigest() != expected_sha256:
        raise ValueError("Pinned source archive SHA256 mismatch")
    entries = []
    with zipfile.ZipFile(archive_path) as z:
        infos = z.infolist()
        if len(infos) > MAX_ARCHIVE_FILES or sum(v.file_size for v in infos) > MAX_ARCHIVE_BYTES:
            raise ValueError("Original source archive exceeds audited limits")
        if any(not safe_name(v.filename) for v in infos):
            raise ValueError("Unsafe original archive entry path")
        known = {v.filename for v in infos if v.filename.startswith(SOURCE_PREFIX)
                 and v.filename.endswith(".java")}
        found = set()
        missing = []
        pending = [(name, True) for name in TARGETS]
        pending += [(name, False) for name in OPTIONAL]
        while pending:
            name, required = pending.pop(0)
            path = SOURCE_PREFIX + name
            if path in found:
                continue
            found.add(path)
            if path not in known:
                if required:
                    missing.append(name)
                continue
            raw = read_java(z, path)
            row = inspect_class(path, raw)
            entries.append(row)
            parent = row["extends"]
            if not parent or len(found) >= 24:
                continue
            # Fully qualified parent ru.himik...Logic.c.d -> c/d.java.
            # A short obfuscated class name c may be disambiguated by imports.
            full = parent
            if "." not in parent:
                full = next((imp for imp in row["imports"]
                             if imp.endswith("." + parent)), parent)
            marker = ".Logic."
            if marker in full:
                relative = full.split(marker, 1)[1].replace(".", "/") + ".java"
            else:
                relative = full.replace(".", "/") + ".java"
            if relative != name:
                pending.append((relative, False))
        if missing:
            raise ValueError("Required original FBD classes missing: " + ", ".join(missing))
    return {
        "source_sha256": expected_sha256,
        "classes": entries,
        "scope": "read-only static evidence; do not equate labels with behavior",
        "pending_acceptance": ["CTU +,-,R numeric direction and saturation",
                               "RS concurrent-set/reset precedence",
                               "TON/TOF/TP units and modes", "BLINK execution",
                               "trigger reset and rising/falling edge semantics"],
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--archive", type=Path,
                    default=Path("reference/craft-reference.zip"))
    ap.add_argument("--output", type=Path,
                    default=Path("build-reports/original-fbd-runtime-evidence.json"))
    args = ap.parse_args()
    evidence = generate_report(args.archive)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Pinned Craft source:", evidence["source_sha256"])
    for row in evidence["classes"]:
        print(row["path"], "sha256", row["sha256"],
              "extends", row["extends"], "lines", row["line_count"],
              "sampled", len(row["evidence"]))
    print("Evidence:", args.output)


if __name__ == "__main__":
    main()
