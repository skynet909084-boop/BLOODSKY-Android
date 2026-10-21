"""Small, testable contracts for bounded original-Craft emulator probes."""
from __future__ import annotations

import time
from collections.abc import Callable


class ProbeDeadlineExceeded(TimeoutError):
    pass


class ProbeBudget:
    def __init__(self, seconds: float, clock: Callable[[], float] = time.monotonic):
        if seconds <= 0:
            raise ValueError("Probe budget must be positive")
        self.seconds = seconds
        self.clock = clock
        self.started = clock()

    @property
    def remaining(self) -> float:
        return max(0.0, self.seconds - (self.clock() - self.started))

    def check(self) -> float:
        remaining = self.remaining
        if remaining <= 0:
            raise ProbeDeadlineExceeded(f"probe exceeded its {self.seconds:g}s interaction budget")
        return remaining


def quick_smoke_passes(evidence: dict) -> bool:
    required_actions = ("project_created", "palette_opened", "component_properties_opened")
    return bool(
        evidence.get("launcher_observed")
        and evidence.get("editor_observed")
        and evidence.get("screenshot_verified")
        and evidence.get("guided_goal_reached")
        and all(evidence.get("action_results", {}).get(name) for name in required_actions)
        and not evidence.get("runtime_limit_hit")
        and not evidence.get("probe_error")
    )
