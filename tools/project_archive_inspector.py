#!/usr/bin/env python3
"""Read-only validator for Kaskada Craft project ZIP archives.

Never extracts files. Validates ZIP paths, archive size/count limits, JSON syntax,
and the broad project export structure while preserving the input.
"""
from __future__ import annotations
import argparse
import json
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import PurePosixPath
from typing import Any

MAX_ENTRIES = 20_000
MAX_TOTAL_UNCOMPRESSED = 512 * 1024 * 1024
MAX_SINGLE_FILE = 128 * 1024 * 1024
MAX_JSON_BYTES = 64 * 1024 * 1024

@dataclass
class Issue:
    severity: str
    message: str
    entry: str | None = None

@dataclass
class Report:
    archive: str
    entries: int = 0
    total_uncompressed_bytes: int = 0
    json_entries: int = 0
    project_json_entries: list[str] | None = None
    issues: list[Issue] | None = None

    def to_dict(self) -> dict[str, Any]:
        value = asdict(self)
        value["issues"] = [asdict(issue) for issue in (self.issues or [])]
        return value

def safe_entry_name(name: str) -> bool:
    # ZIP convention is '/', but also reject Windows separators and drive paths.
    if not name or "\\" in name or name.startswith("/") or "\x00" in name:
        return False
    # PurePosixPath normalizes away explicit "." and repeated separators.
    # Validate raw segments before normalization to avoid ambiguous ZIP names.
    if name.endswith("//") or any(part in ("", ".", "..") for part in name.removesuffix("/").split("/")):
        return False
    path = PurePosixPath(name)
    if path.parts and ":" in path.parts[0]:
        return False
    return True


def validate_craft_project(project: dict[str, Any], entry: str, report: Report) -> None:
    """Check the stable outer Craft export shape without rewriting source data."""
    required = ("TableNameProject", "TableComponent", "TableConnections")
    missing = [key for key in required if key not in project]
    for key in missing:
        report.issues.append(Issue("error", f"Missing Craft project table {key}", entry))
    if missing:
        return

    name_project = project["TableNameProject"]
    components = project["TableComponent"]
    connections = project["TableConnections"]
    if not isinstance(name_project, dict):
        report.issues.append(Issue("error", "TableNameProject must be an object", entry))
    if not isinstance(components, list):
        report.issues.append(Issue("error", "TableComponent must be an array", entry))
    if not isinstance(connections, list):
        report.issues.append(Issue("error", "TableConnections must be an array", entry))
    if not isinstance(components, list):
        return

    for index, component in enumerate(components):
        component_entry = f"{entry}#TableComponent[{index}]"
        if not isinstance(component, dict):
            report.issues.append(Issue("error", "Craft component row must be an object", component_entry))
            continue
        component_id = component.get("componentId")
        if isinstance(component_id, bool) or not isinstance(component_id, int):
            report.issues.append(Issue("error", "Craft componentId must be an integer", component_entry))

        # Original exports store component-specific properties as a JSON string.
        # Accept an object too for newer/normalized archives, while leaving it untouched.
        other = component.get("other", component.get("jsTableComponent_other"))
        if other is None:
            report.issues.append(Issue("warning", "Craft component has no other metadata", component_entry))
            continue
        if isinstance(other, str):
            try:
                other = json.loads(other)
            except json.JSONDecodeError as exc:
                report.issues.append(Issue("error", f"Invalid component other JSON: {exc}", component_entry))
                continue
        if not isinstance(other, dict):
            report.issues.append(Issue("error", "Craft component other metadata must be an object", component_entry))
            continue
        if "numScreen" in other:
            screen = other["numScreen"]
            if isinstance(screen, bool) or not isinstance(screen, int) or screen < 0:
                report.issues.append(Issue("error", "Craft component numScreen must be a non-negative integer", component_entry))

def inspect_archive(path: str) -> Report:
    report = Report(path, project_json_entries=[], issues=[])
    try:
        archive = zipfile.ZipFile(path, "r")
    except (OSError, zipfile.BadZipFile) as exc:
        report.issues.append(Issue("error", f"Cannot open ZIP: {exc}"))
        return report

    with archive:
        infos = archive.infolist()
        report.entries = len(infos)
        if len(infos) > MAX_ENTRIES:
            report.issues.append(Issue("error", f"Entry count exceeds limit ({MAX_ENTRIES})"))

        json_candidates: list[str] = []
        seen_names: set[str] = set()
        duplicate_names: set[str] = set()
        for info in infos:
            if not safe_entry_name(info.filename):
                report.issues.append(Issue("error", "Unsafe ZIP entry path", info.filename))
                continue
            if info.is_dir():
                continue
            if info.filename in seen_names:
                duplicate_names.add(info.filename)
                report.issues.append(Issue("error", "Duplicate ZIP entry name", info.filename))
                continue
            seen_names.add(info.filename)
            if info.file_size > MAX_SINGLE_FILE:
                report.issues.append(Issue("error", f"Entry exceeds per-file size limit ({MAX_SINGLE_FILE} bytes)", info.filename))
            report.total_uncompressed_bytes += info.file_size
            if info.file_size > 0 and info.compress_size == 0:
                report.issues.append(Issue("error", "Non-empty entry has zero compressed size", info.filename))
            elif info.compress_size > 0 and info.file_size / info.compress_size > 1000:
                report.issues.append(Issue("warning", "Extreme compression ratio; inspect before importing", info.filename))
            if info.filename.lower().endswith(".json"):
                json_candidates.append(info.filename)

        # ZipFile.getinfo(name) resolves duplicate names ambiguously. Do not parse
        # any JSON path that appears more than once in the archive.
        json_candidates = [name for name in json_candidates if name not in duplicate_names]
        if report.total_uncompressed_bytes > MAX_TOTAL_UNCOMPRESSED:
            report.issues.append(Issue("error", f"Total uncompressed size exceeds limit ({MAX_TOTAL_UNCOMPRESSED} bytes)"))

        for name in json_candidates:
            info = archive.getinfo(name)
            if info.file_size > MAX_JSON_BYTES:
                report.issues.append(Issue("error", f"JSON exceeds parsing limit ({MAX_JSON_BYTES} bytes)", name))
                continue
            try:
                parsed = json.loads(archive.read(name).decode("utf-8-sig"))
                report.json_entries += 1
            except (UnicodeDecodeError, json.JSONDecodeError, OSError, RuntimeError,
                    EOFError, zipfile.BadZipFile) as exc:
                report.issues.append(Issue("error", f"Invalid JSON: {exc}", name))
                continue
            if isinstance(parsed, dict) and any(key in parsed for key in ("TableNameProject", "TableComponent", "TableConnections")):
                report.project_json_entries.append(name)
                validate_craft_project(parsed, name, report)

    if not report.project_json_entries:
        report.issues.append(Issue("warning", "No JSON entry with recognized Kaskada project keys was found"))
    return report

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", help="Path to a Kaskada project ZIP")
    args = parser.parse_args(argv)
    report = inspect_archive(args.archive)
    print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    return 1 if any(issue.severity == "error" for issue in (report.issues or [])) else 0

if __name__ == "__main__":
    sys.exit(main())
