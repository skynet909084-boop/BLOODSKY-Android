"""Stable detection for original Craft's project-loading state."""
from __future__ import annotations

import re

_LOADING_RE = re.compile(r"(?:загр.{0,40}проек|подождит)", re.IGNORECASE | re.DOTALL)


def is_project_loading(root) -> bool:
    """Detect project loading across text, hint, and content-description variants."""
    for node in root.iter("node"):
        visible = " ".join(node.attrib.get(key, "") for key in ("text", "content-desc", "hint"))
        if _LOADING_RE.search(visible):
            return True
    return False

_DIRECTION_TERMS = {
    "project-create": ("create", "создать", "project"),
    "import-paste": ("paste", "link", "import", "ссыл", "импорт"),
    "import-qr": ("scan", "qr", "скан", "camera", "камера"),
    "exit-policy": ("exit", "privacy", "policy", "выход", "полит"),
    "components": ("component", "индикатор", "sensor", "датчик", "button", "кноп"),
    "fbd": ("fbd", "fb_", "block", "блок", "logic", "таймер"),
    "macros": ("macro", "макрос"),
    "devices": ("device", "server", "устройств", "сервер"),
    "network": ("tcp", "modbus", "bluetooth", "usb", "wi-fi", "wifi", "сеть"),
    "runtime": ("run", "start", "рабоч", "запуск", "экран"),
}


def prioritize_actions(actions, direction: str):
    """Try direction-relevant controls first while preserving other coverage."""
    terms = _DIRECTION_TERMS.get(direction, ())
    indexed = list(enumerate(actions))
    indexed.sort(key=lambda pair: (
        not any(term in pair[1].get("label", "").casefold() for term in terms),
        pair[0],
    ))
    return [action for _, action in indexed]
