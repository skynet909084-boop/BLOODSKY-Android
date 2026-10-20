#!/usr/bin/env python3
"""Ensure the retired cloud AI pilot remains disabled without credentials or network."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
plan = ROOT / "docs/FREELLMAPI_CLOUD_PILOT.md"
retired_workflows = (
    ROOT / ".github/workflows/cloud-ai-preflight.yml",
    ROOT / ".github/workflows/cloud-ai-agent.yml",
)

def main():
    errors = []
    for path in retired_workflows:
        if path.exists():
            errors.append(f"Retired cloud-agent workflow must remain disabled: {path.relative_to(ROOT)}")
    if not plan.is_file():
        errors.append(f"Missing status document: {plan.relative_to(ROOT)}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    p = plan.read_text(encoding="utf-8").lower()
    for item in ("pilot is paused", "workflows were removed", "no code-writing agent runs automatically"):
        if item not in p:
            errors.append(f"Status document missing disabled-state statement: {item}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Retired cloud AI pilot remains disabled.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
