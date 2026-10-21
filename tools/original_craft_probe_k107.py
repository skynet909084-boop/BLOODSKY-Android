"""Conservative links from original-app probe actions to source audit cases."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

EXPECTED_APK_SHA256 = "11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f"
EXPECTED_CASE_COUNT = 550


def load_case_matrix(path: Path) -> list[dict[str, Any]]:
    matrix = json.loads(path.read_text(encoding="utf-8"))
    cases = matrix.get("test_cases")
    if matrix.get("apk_sha256") != EXPECTED_APK_SHA256:
        raise ValueError("K107 case matrix APK hash does not match the pinned original APK")
    if not isinstance(cases, list) or len(cases) != EXPECTED_CASE_COUNT:
        raise ValueError(f"K107 case matrix must contain exactly {EXPECTED_CASE_COUNT} cases")
    expected_ids = [f"K107-{i:04d}" for i in range(1, EXPECTED_CASE_COUNT + 1)]
    if [case.get("id") for case in cases] != expected_ids:
        raise ValueError("K107 case IDs are missing, duplicated, or out of order")
    return cases


def action_selector(action: dict[str, Any]) -> str:
    node = action.get("node", {})
    resource_id = str(node.get("resource_id", ""))
    match = re.search(r"(?:^|:id/|/)\s*([A-Za-z][A-Za-z0-9_]*)$", resource_id)
    return match.group(1) if match else ""


def map_action_to_case_ids(action: dict[str, Any], cases: list[dict[str, Any]]) -> list[str]:
    """Map only exact accessibility resource-ID mentions in a case's test steps."""
    selector = action_selector(action)
    if not selector:
        return []
    needle = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(selector) + r"(?![A-Za-z0-9_])", re.IGNORECASE)
    matches = []
    for case in cases:
        tested_steps = " ".join((str(case.get("feature", "")), str(case.get("procedure", ""))))
        if needle.search(tested_steps):
            matches.append(case["id"])
    return matches


def build_k107_report(cases: list[dict[str, Any]], states: list[dict[str, Any]], out_dir: Path) -> dict[str, Any]:
    case_evidence: dict[str, list[str]] = {case["id"]: [] for case in cases}
    observations = []
    for state in states:
        case_ids = list(dict.fromkeys(state.get("k107_case_ids", [])))
        if not case_ids:
            continue
        observation_id = f"obs-{state['state']}"
        for case_id in case_ids:
            if case_id in case_evidence:
                case_evidence[case_id].append(observation_id)
        state_name = state["state"]
        evidence = {"action_log": f"ui-audit.json#states/{state_name}/path"}
        for label, filename in (("accessibility_tree", f"{state_name}.xml"),
                                ("screenshot", f"{state_name}.png")):
            if (out_dir / filename).is_file():
                evidence[label] = filename
        observations.append({
            "id": observation_id,
            "case_ids": case_ids,
            "status": "WAITING",
            "evidence": evidence,
            "action_path": state.get("path", []),
        })
    statuses = [{
        "id": case["id"],
        "status": "WAITING",
        "evidence_refs": case_evidence[case["id"]],
    } for case in cases]
    return {
        "case_statuses": statuses,
        "remaining_case_ids": [case["id"] for case in cases],
        "observations": observations,
        "mapped_case_count": sum(bool(refs) for refs in case_evidence.values()),
        "unmapped_state_count": sum(not state.get("k107_case_ids") for state in states),
    }
