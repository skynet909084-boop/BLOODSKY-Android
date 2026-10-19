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


def _is_auxiliary_calculator(action):
    node = action["node"]
    resource = node.get("resource_id", "").rsplit("/", 1)[-1].casefold()
    label = action.get("label", "").casefold()
    return resource in {"calc", "calculator"} or "calculator" in label


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
    focused = prioritize_actions(actions, direction)
    # The calculator opens an unrelated keypad flow for focused audits.
    # Keep it available during FBD exploration, where calc blocks may matter.
    if direction != "fbd":
        focused = [action for action in focused if not _is_auxiliary_calculator(action)]
    if terms:
        # Long-press a direction-matched target first when one is present.
        # Otherwise navigate toward that section before exploring unrelated
        # long-clickable controls such as the calculator button.
        matching_long_presses = [
            action for action in focused
            if action["kind"] == "long_press"
            and any(term in action["label"].casefold() for term in terms)
        ]
        return [*matching_long_presses,
                *(action for action in focused if action not in matching_long_presses)]

    return [*filter(lambda action: action["kind"] == "long_press", focused),
            *filter(lambda action: action["kind"] != "long_press", focused)]


def action_identity(action):
    """Identify one control gesture so a DFS path cannot repeat a UI toggle forever."""
    node = action["node"]
    return (action["kind"], node.get("resource_id", ""), node.get("text", ""),
            node.get("description", ""), node.get("hint", ""), node.get("class", ""),
            node.get("bounds", ""))


def touch_command(action, duration_ms: int = LONG_PRESS_MS):
    """Return adb shell input arguments for a tap or an observed long-click."""
    node = action["node"]
    x, y = str(node["x"]), str(node["y"])
    if action["kind"] == "long_press":
        return ["shell", "input", "swipe", x, y, x, y, str(duration_ms)]
    return ["shell", "input", "tap", x, y]



def safe_field_value(hint: str | None):
    """Return a generic probe value only for a clearly identified field hint."""
    hint = (hint or "").strip().casefold()
    if not hint:
        return None
    if any(x in hint for x in ("парол", "password", "ключ", "token")):
        return "test"
    if any(x in hint for x in ("имя проекта", "название проекта", "project name", "project title")):
        return "CraftAudit"
    if any(x in hint for x in ("порт", "port", "unit id", "slave id", "колич", "count")):
        return "1"
    if any(x in hint for x in ("адрес", "address", "host", "ip")):
        return "127.0.0.1"
    return None
