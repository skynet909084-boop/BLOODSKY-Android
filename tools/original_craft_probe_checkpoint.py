"""Atomic, provenance-bound checkpoints for the original Craft UI traversal."""
from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def new_checkpoint(provenance: str, apk_sha256: str) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "apk_provenance": provenance,
        "apk_sha256": apk_sha256,
        "frontier": [[]],
        "visited_signatures": [],
        "states": [],
        "transitions": [],
        "expanded_paths": [],
        "coverage_limited": False,
    }


def load_checkpoint(path: Path, provenance: str, apk_sha256: str) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("unsupported checkpoint schema")
    if data.get("apk_provenance") != provenance or data.get("apk_sha256") != apk_sha256:
        raise ValueError("checkpoint APK provenance does not match the installed APK")
    for key in ("frontier", "visited_signatures", "states", "transitions", "expanded_paths"):
        if not isinstance(data.get(key), list):
            raise ValueError(f"checkpoint field {key} must be a list")
    # De-duplicate signatures and preserve the first confirmed state row for a signature.
    data["visited_signatures"] = list(dict.fromkeys(data["visited_signatures"]))
    signatures: set[str] = set()
    unique_states = []
    for row in data["states"]:
        signature = row.get("signature")
        if not signature or signature in signatures:
            continue
        signatures.add(signature)
        unique_states.append(row)
    data["states"] = unique_states
    data["visited_signatures"] = list(dict.fromkeys(data["visited_signatures"] + list(signatures)))
    data["expanded_paths"] = list(dict.fromkeys(json.dumps(p, sort_keys=True) for p in data["expanded_paths"]))
    data["expanded_paths"] = [json.loads(p) for p in data["expanded_paths"]]
    return data


def checkpoint_from_run(frontier, seen, states, transitions, expanded_paths,
                        provenance: str, apk_sha256: str, coverage_limited: bool) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "apk_provenance": provenance,
        "apk_sha256": apk_sha256,
        "frontier": list(frontier),
        "visited_signatures": sorted(seen),
        "states": list(states),
        "transitions": list(transitions),
        "expanded_paths": list(expanded_paths),
        "coverage_limited": bool(coverage_limited),
    }
