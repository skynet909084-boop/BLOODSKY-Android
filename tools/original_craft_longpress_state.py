"""Long-press action selection for the isolated Craft UI probe."""
from __future__ import annotations

from original_craft_probe_state import prioritize_actions

_LONG_PRESS_FOCUS = {
    "fbd-timer-modes": ("timer", "таймер", "delay", "задерж", "mode", "режим", "time", "время"),
    "fbd-blocks": ("fbd", "block", "блок", "input", "output", "вход", "выход"),
    "components": ("indicator", "component", "sensor", "индикатор", "компонент", "датчик", "кноп"),
    "macros": ("macro", "макрос"),
    "projects": ("project", "save", "open", "project list", "проект", "сохран", "открыть"),
    "devices": ("device", "server", "modbus", "устройств", "сервер", "модбас"),
    "editor-canvas": ("canvas", "screen", "element", "холст", "экран", "элемент"),
}

LONG_PRESS_MS = 1000


def actions_for_controls(controls, direction: str):
    """Create taps for navigation and long presses only for explicitly marked views."""
    actions = []
    seen_taps = set()
    seen_long_presses = set()
    for control in controls:
        point = (control["x"], control["y"])
        label = (control.get("text") or control.get("description") or
                 control.get("hint") or control.get("resource_id") or
                 control.get("class") or "control")[:100]
        if (control.get("clickable") or control.get("field")) and point not in seen_taps:
            seen_taps.add(point)
            actions.append({"kind": "tap", "node": control, "label": label})
        if control.get("long_clickable") and point not in seen_long_presses:
            seen_long_presses.add(point)
            actions.append({"kind": "long_press", "node": control,
                            "label": f"LONG PRESS: {label}"})

    terms = _LONG_PRESS_FOCUS.get(direction)
    if terms:
        indexed = list(enumerate(actions))
        indexed.sort(key=lambda pair: (
            pair[1]["kind"] != "long_press",
            not any(term in pair[1]["label"].casefold() for term in terms),
            pair[0],
        ))
        return [action for _, action in indexed]

    focused = prioritize_actions(actions, direction)
    return [*filter(lambda action: action["kind"] == "long_press", focused),
            *filter(lambda action: action["kind"] != "long_press", focused)]


def touch_command(action, duration_ms: int = LONG_PRESS_MS):
    """Return adb shell input arguments for a tap or an observed long-click."""
    node = action["node"]
    x, y = str(node["x"]), str(node["y"])
    if action["kind"] == "long_press":
        return ["shell", "input", "swipe", x, y, x, y, str(duration_ms)]
    return ["shell", "input", "tap", x, y]
