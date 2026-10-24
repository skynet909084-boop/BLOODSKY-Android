#!/usr/bin/env python3
"""Cross-reference every public Craft view ID against layouts, Java and BLOODSKY.

This is an inventory, not a semantic-parity claim. A candidate ID match still
needs an interaction/source check; runtime XML only records a control being seen.
"""
from __future__ import annotations

import argparse
import csv
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

SOURCE_ID = re.compile(r"@(?:\+)?id/([A-Za-z0-9_]+)")
RESOURCE_ID = re.compile(r"R\.id\.([A-Za-z0-9_]+)")
RUNTIME_ID = re.compile(r"(?:^|:)id/([A-Za-z0-9_]+)$")
FRAMEWORK_LAYOUT_PREFIXES = (
    "abc_", "design_", "material_", "mtrl_", "notification_", "select_dialog_",
    "support_", "test_", "zxing_",
)


def public_ids(original_res: Path) -> dict[str, str]:
    public_xml = original_res / "values" / "public.xml"
    root = ET.parse(public_xml).getroot()
    return {
        node.attrib["name"]: node.attrib.get("id", "")
        for node in root.findall("public")
        if node.attrib.get("type") == "id" and node.attrib.get("name")
    }


def id_candidates(name: str) -> set[str]:
    candidates = {name, f"kc_{name}"}
    if name.startswith("tab_"):
        candidates.update((f"kc_picker_{name}", f"kc_logic_{name}"))
    candidates.add(f"kc_toolbar_{name}")
    return candidates


def collect_layout_ids(root: Path) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    layouts: dict[str, set[str]] = defaultdict(set)
    all_ids: dict[str, set[str]] = defaultdict(set)
    for file in sorted(root.rglob("*.xml")):
        try:
            tree = ET.parse(file)
        except ET.ParseError:
            continue
        rel = file.relative_to(root).as_posix()
        for element in tree.iter():
            for value in element.attrib.values():
                for name in SOURCE_ID.findall(value):
                    all_ids[name].add(rel)
                    if file.parent.name.split("-")[0] == "layout":
                        layouts[name].add(rel)
    return layouts, all_ids


def collect_java_ids(root: Path, package_prefix: str | None = None) -> dict[str, set[str]]:
    references: dict[str, set[str]] = defaultdict(set)
    for file in sorted(root.rglob("*.java")):
        rel = file.as_posix()
        if package_prefix and package_prefix not in rel:
            continue
        try:
            lines = file.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        for number, line in enumerate(lines, 1):
            for name in RESOURCE_ID.findall(line):
                references[name].add(f"{file.name}:{number}")
    return references


def collect_runtime_ids(root: Path | None) -> dict[str, set[str]]:
    seen: dict[str, set[str]] = defaultdict(set)
    if root is None or not root.exists():
        return seen
    for file in sorted(root.rglob("*.xml")):
        try:
            tree = ET.parse(file)
        except ET.ParseError:
            continue
        for node in tree.iter("node"):
            value = node.attrib.get("resource-id", "")
            match = RUNTIME_ID.search(value)
            if match:
                seen[match.group(1)].add(file.name)
    return seen


def audit(original_res: Path, original_java: Path, bloodsky_res: Path,
          bloodsky_java: Path, output_csv: Path,
          runtime_xml: Path | None = None) -> dict[str, int]:
    ids = public_ids(original_res)
    original_layouts, original_xml_ids = collect_layout_ids(original_res)
    original_refs = collect_java_ids(original_java, "ru/himik/nightscream/kascada")
    bloodsky_layouts, bloodsky_ids = collect_layout_ids(bloodsky_res)
    bloodsky_refs = collect_java_ids(bloodsky_java, "com/bloodsky/hmi")
    runtime = collect_runtime_ids(runtime_xml)

    fields = (
        "resource_id", "apk_local_integer", "product_owned_evidence",
        "original_layouts", "original_xml_references", "original_java_references",
        "bloodsky_candidate_ids", "bloodsky_layouts", "bloodsky_java_references",
        "runtime_seen_names", "runtime_screenshot_or_tree_files", "review_status",
    )
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for name, integer in sorted(ids.items()):
            product_layouts = original_layouts.get(name, set())
            xml_refs = original_xml_ids.get(name, set())
            java_refs = original_refs.get(name, set())
            product_source = sorted(x for x in xml_refs if not Path(x).name.startswith(FRAMEWORK_LAYOUT_PREFIXES))
            candidates = id_candidates(name)
            bloodsky_matches = set().union(
                *(bloodsky_ids.get(candidate, set()) | bloodsky_refs.get(candidate, set())
                  for candidate in candidates)
            )
            runtime_matches = set().union(*(runtime.get(candidate, set()) for candidate in candidates))
            if not xml_refs and not java_refs:
                status = "public_only_unclassified"
            elif not product_source and not java_refs:
                status = "framework_or_library_candidate"
            elif not bloodsky_matches:
                status = "no_bloodsky_name_candidate"
            elif runtime_matches:
                status = "runtime_seen_candidate_only"
            else:
                status = "name_candidate_needs_behavior_review"
            writer.writerow({
                "resource_id": name,
                "apk_local_integer": integer,
                "product_owned_evidence": "; ".join(sorted(set(product_source) | set(java_refs))),
                "original_layouts": "; ".join(sorted(product_layouts)),
                "original_xml_references": "; ".join(sorted(xml_refs)),
                "original_java_references": "; ".join(sorted(java_refs)),
                "bloodsky_candidate_ids": "; ".join(sorted(candidates & (set(bloodsky_ids) | set(bloodsky_refs)))),
                "bloodsky_layouts": "; ".join(sorted(set().union(*(bloodsky_layouts.get(c, set()) for c in candidates)))),
                "bloodsky_java_references": "; ".join(sorted(set().union(*(bloodsky_refs.get(c, set()) for c in candidates)))),
                "runtime_seen_names": "; ".join(sorted(candidates & set(runtime))),
                "runtime_screenshot_or_tree_files": "; ".join(sorted(runtime_matches)),
                "review_status": status,
            })
    return {
        "public_ids": len(ids),
        "ids_with_original_layouts": sum(bool(original_layouts.get(n)) for n in ids),
        "ids_with_original_java_refs": sum(bool(original_refs.get(n)) for n in ids),
        "ids_with_bloodsky_name_candidates": sum(
            bool(id_candidates(n) & (set(bloodsky_ids) | set(bloodsky_refs))) for n in ids
        ),
        "ids_seen_in_runtime_xml": sum(bool(id_candidates(name) & set(runtime)) for name in ids),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original_res", type=Path)
    parser.add_argument("original_jadx_sources", type=Path)
    parser.add_argument("bloodsky_res", type=Path)
    parser.add_argument("bloodsky_java", type=Path)
    parser.add_argument("output_csv", type=Path)
    parser.add_argument("--runtime-xml", type=Path)
    args = parser.parse_args()
    print(audit(args.original_res, args.original_jadx_sources, args.bloodsky_res,
                args.bloodsky_java, args.output_csv, args.runtime_xml))


if __name__ == "__main__":
    main()
