"""Small independent runtime checks for the original Craft emulator probe."""
from __future__ import annotations


QUICK_SCENARIOS = {
    "project-create": {
        "case_ids": ["K107-0001"],
        "expected": "Creating a project reaches the editor and shows screen navigation.",
        "steps": ["create project dialog", "project name", "create project"],
    },
    "hmi-palette": {
        "case_ids": ["K107-0023"],
        "expected": "The editor exposes dynamic pictures and the layout switch.",
        "steps": ["create project dialog", "project name", "create project", "component palette"],
    },
    "hmi-id17-properties": {
        "case_ids": ["K107-0023"],
        "expected": "Placing Craft ID17 opens its property editor with editable fields.",
        "steps": ["create project dialog", "project name", "create project", "component palette",
                  "drag Craft ID17 dynamic picture to canvas"],
    },
}


def parse_probe_options(arguments: list[str]) -> tuple[str, str]:
    """Parse the bounded scenario selector and the manual-only full flag."""
    profile = "quick"
    scenario = "hmi-id17-properties"
    index = 0
    while index < len(arguments):
        option = arguments[index]
        if option == "--full":
            profile = "full"
        elif option == "--scenario":
            index += 1
            if index >= len(arguments):
                raise ValueError("--scenario requires a scenario name")
            scenario = arguments[index]
        else:
            raise ValueError(f"unknown probe option: {option}")
        index += 1
    if scenario not in QUICK_SCENARIOS:
        raise ValueError(f"unknown quick scenario: {scenario}")
    return profile, scenario
