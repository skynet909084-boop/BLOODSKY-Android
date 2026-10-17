#!/usr/bin/env python3
"""Validate the isolated cloud AI pilot without accessing credentials or network."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
workflow = ROOT / ".github/workflows/cloud-ai-preflight.yml"
plan = ROOT / "docs/FREELLMAPI_CLOUD_PILOT.md"

def main():
    errors = []
    for path in (workflow, plan):
        if not path.is_file():
            errors.append(f"Missing: {path.relative_to(ROOT)}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    w = workflow.read_text(encoding="utf-8")
    p = plan.read_text(encoding="utf-8").lower()
    required_workflow = ("workflow_dispatch:", "contents: read", "timeout-minutes:", "actions/checkout@")
    for item in required_workflow:
        if item not in w:
            errors.append(f"Workflow missing: {item}")
    for forbidden in ("pull_request_target:", "secrets.", "curl | bash", "git push", "permissions: write-all"):
        if forbidden in w:
            errors.append(f"Unsafe preflight directive: {forbidden}")
    for item in ("freeLLMAPI", "human approval", "secrets", "pull request"):
        if item.lower() not in p:
            errors.append(f"Plan missing: {item}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Cloud AI pilot static safety checks passed.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
