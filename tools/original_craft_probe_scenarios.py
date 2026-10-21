"""CLI and evidence mapping for bounded original Craft scenarios."""

QUICK_SCENARIOS = {
    "project-create": {
        "case_ids": ["K107-0001"],
        "expected": "The create flow reaches the editor with screen navigation controls.",
    },
    "hmi-palette": {
        "case_ids": ["K107-0023"],
        "expected": "The HMI palette exposes the dynamic-picture component and layout switch.",
    },
    "hmi-id17-properties": {
        "case_ids": ["K107-0023"],
        "expected": "After placing Craft ID17, the properties editor opens with editable fields.",
    },
}


def parse_probe_options(arguments):
    """Return (profile, scenario); reject unknown or incomplete CLI arguments."""
    profile = "full" if "--full" in arguments else "quick"
    scenario = "hmi-id17-properties"
    args = list(arguments)
    if "--full" in args:
        args.remove("--full")
    if "--scenario" in args:
        index = args.index("--scenario")
        if index + 1 >= len(args):
            raise ValueError("--scenario requires a scenario name")
        scenario = args[index + 1]
        del args[index:index + 2]
    unknown = [arg for arg in args if arg]
    if unknown:
        raise ValueError(f"unknown probe arguments: {unknown}")
    if scenario not in QUICK_SCENARIOS:
        raise ValueError(f"unknown quick scenario: {scenario}")
    return profile, scenario
