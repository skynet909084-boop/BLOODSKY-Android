#!/usr/bin/env python3
"""Extract component class/property keys from decompiled mbCanvas.m()."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


CLASS_RE = re.compile(
    r"new\s+([\w.$]+)\s*\(\s*this(?:\s*,|\s*\))"
)
JSON_KEY_RE = re.compile(
    r'jSONObject\w*\.get(?:Int|String|Boolean|Long|Double|Float)\(\s*"([^"]+)"\s*\)'
)


def extract_factory(source: str) -> dict[int, tuple[str | None, list[str], list[str]]]:
    marker = "private ru.himik.nightscream.kascada.CustomComponents.z m("
    start = source.find(marker)
    if start < 0:
        raise ValueError("Could not find mbCanvas component factory m(...)")
    next_method = source.find("\n    private ", start + len(marker))
    factory = source[start : next_method if next_method >= 0 else len(source)]
    starts: dict[int, int] = {}
    for component_id in range(1, 29):
        if component_id == 1 or component_id == 2:
            pattern = rf"if\s*\(\s*i2\s*==\s*{component_id}\s*\)\s*\{{"
        else:
            pattern = rf"case\s+{component_id}\s*:"
        match = re.search(pattern, factory)
        if match:
            starts[component_id] = match.start()

    result: dict[int, tuple[str | None, list[str], list[str]]] = {}
    for component_id, branch_start in starts.items():
        later = [pos for key, pos in starts.items() if key > component_id and pos > branch_start]
        branch = factory[branch_start : min(later) if later else len(factory)]
        if component_id == 28:
            default_branch = re.search(r"\n\s*default\s*:", branch)
            if default_branch:
                branch = branch[: default_branch.start()]
        class_match = CLASS_RE.search(branch)
        json_sections = list(re.finditer(r'new\s+JSONObject\(\s*(str2|str3)\s*\)', branch))
        keys_by_arg: dict[str, set[str]] = {"str2": set(), "str3": set()}
        for index, match in enumerate(json_sections):
            arg = match.group(1)
            end = json_sections[index + 1].start() if index + 1 < len(json_sections) else len(branch)
            keys_by_arg[arg].update(JSON_KEY_RE.findall(branch[match.end() : end]))
        result[component_id] = (
            class_match.group(1) if class_match else None,
            sorted(keys_by_arg["str2"]),
            sorted(keys_by_arg["str3"]),
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mbcanvas", type=Path, help="Path to JADX mbCanvas.java")
    args = parser.parse_args()
    try:
        result = extract_factory(args.mbcanvas.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    print("id\tclass\tconnectionInfo_keys\tother_keys")
    for component_id in range(1, 29):
        if component_id not in result:
            print(f"{component_id}\tUNRESOLVED\t")
            continue
        class_name, connection_keys, other_keys = result[component_id]
        print(f"{component_id}\t{class_name or 'CLASS_UNRESOLVED'}\t{','.join(connection_keys)}\t{','.join(other_keys)}")

    # These guards catch accidental confusion with the deletion dispatcher.
    if 3 in result or 16 not in result or 17 not in result:
        print("error: expected factory evidence (ID 3 absent; IDs 16 and 17 present) changed", file=sys.stderr)
        return 1
    if result[16][0] != "ru.himik.nightscream.kascada.CustomComponents.n":
        print("error: ID 16 factory class differs from audited mapping", file=sys.stderr)
        return 1
    if result[17][0] != "ru.himik.nightscream.kascada.CustomComponents.h":
        print("error: ID 17 factory class differs from audited mapping", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
