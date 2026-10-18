#!/usr/bin/env python3
"""Breadth-first UI audit of the original Craft 1.0.7 APK on an offline emulator.

Every discovered branch is replayed from cleared app data, then all visible in-app
controls are explored. Screenshots, accessibility trees, action paths and crashes are
saved as CI artifacts. This tests the UI surface without pretending to emulate hardware.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "ru.kascada.modbus"
MAX_STATES = 140
MAX_DEPTH = 7
WAIT = 0.8


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(["adb", *args], text=True, capture_output=True)
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout.strip()


def dump_ui(label: str):
    remote = "/sdcard/craft-audit-window.xml"
    adb("shell", "uiautomator", "dump", remote, check=False)
    local = OUT / f"{label}.xml"
    adb("pull", remote, str(local), check=False)
    try:
        root = ET.parse(local).getroot()
    except (ET.ParseError, OSError):
        root = ET.Element("hierarchy")
    png = OUT / f"{label}.png"
    with png.open("wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=False)
    return root, local.read_text(errors="replace") if local.exists() else "", png


def controls(root):
    out = []
    for node in root.iter("node"):
        package = node.attrib.get("package", "")
        if package not in ("", PACKAGE):
            continue
        cls = node.attrib.get("class", "")
        if "InputMethod" in cls:
            continue
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        clickable = node.attrib.get("clickable") == "true" and node.attrib.get("enabled") != "false"
        field = cls.endswith("EditText") and node.attrib.get("enabled") != "false"
        if not clickable and not field:
            continue
        out.append({
            "text": node.attrib.get("text", "")[:120],
            "description": node.attrib.get("content-desc", "")[:120],
            "hint": node.attrib.get("hint", "")[:120],
            "class": cls,
            "bounds": bounds,
            "x": (x1 + x2) // 2,
            "y": (y1 + y2) // 2,
            "field": field,
            "package": package,
        })
    return out


def action_key(a):
    return (a["text"], a["description"], a["hint"], a["class"], a["bounds"], a["field"])


def perform(action):
    if action["kind"] == "back":
        adb("shell", "input", "keyevent", "4")
    else:
        node = action["node"]
        adb("shell", "input", "tap", str(node["x"]), str(node["y"]))
        if node.get("field"):
            time.sleep(0.3)
            hint = (node.get("hint", "") + " " + node.get("text", "")).lower()
            if any(x in hint for x in ("парол", "password", "ключ", "token")):
                value = "test"
            elif any(x in hint for x in ("порт", "port", "адрес", "address", "unit", "id", "колич", "count")):
                value = "1"
            else:
                value = "127.0.0.1"
            adb("shell", "input", "text", value)
    time.sleep(WAIT)


def fresh_start():
    adb("shell", "am", "force-stop", PACKAGE, check=False)
    adb("shell", "pm", "clear", PACKAGE, check=False)
    adb("shell", "monkey", "-p", PACKAGE, "1", check=False)
    time.sleep(4)


def replay(path):
    fresh_start()
    for action in path:
        perform(action)


queue = deque([[]])
seen = set()
state_rows = []
transition_rows = []
started = time.time()
while queue and len(state_rows) < MAX_STATES:
    path = queue.popleft()
    replay(path)
    label = f"state-{len(state_rows):03d}"
    root, xml, png = dump_ui(label)
    ctrls = controls(root)
    screenshot_hash = hashlib.sha256(png.read_bytes()).hexdigest() if png.exists() else ""
    visible = [(n.attrib.get("class", ""), n.attrib.get("text", ""),
                n.attrib.get("content-desc", ""), n.attrib.get("bounds", ""),
                n.attrib.get("checked", ""), n.attrib.get("selected", ""))
               for n in root.iter("node") if n.attrib.get("package", "") in ("", PACKAGE)]
    signature = hashlib.sha256(json.dumps([visible, screenshot_hash], ensure_ascii=False).encode()).hexdigest()
    if signature in seen:
        (OUT / f"{label}.xml").unlink(missing_ok=True)
        (OUT / f"{label}.png").unlink(missing_ok=True)
        continue
    seen.add(signature)
    path_labels = [a["label"] for a in path]
    row = {"state": label, "path": path_labels, "control_count": len(ctrls),
           "controls": ctrls, "screenshot_sha256": screenshot_hash}
    state_rows.append(row)
    print(f"{label}: depth={len(path)} controls={len(ctrls)} path={' / '.join(path_labels[-3:])}")

    if len(path) >= MAX_DEPTH:
        continue
    actions = [{"kind": "tap", "node": n, "label": (n["text"] or n["description"] or n["hint"] or n["class"])[:100]}
               for n in ctrls]
    actions.append({"kind": "back", "label": "Android Back"})
    for action in actions:
        child = path + [action]
        if len(queue) + len(state_rows) >= MAX_STATES * 4:
            break
        queue.append(child)
        transition_rows.append({"from": label, "action": action["label"], "depth": len(child)})

# Capture all uncaught Android Java exceptions from this audit session.
log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
(OUT / "android-runtime-errors.log").write_text(log)
report = {
    "app_package": PACKAGE,
    "offline": True,
    "max_states": MAX_STATES,
    "max_path_depth": MAX_DEPTH,
    "states_captured": len(state_rows),
    "unique_state_signatures": len(seen),
    "transitions_queued": len(transition_rows),
    "remaining_frontier": len(queue),
    "elapsed_seconds": round(time.time() - started, 1),
    "states": state_rows,
    "transitions": transition_rows,
    "runtime_error_log": "android-runtime-errors.log",
    "limitations": [
        "Traversal is bounded by state and depth limits; report lists any remaining frontier.",
        "Unavailable Bluetooth and USB hardware is not simulated.",
        "Generic values are entered only into visible text fields to reveal subsequent screens; they are not valid device credentials.",
    ],
}
(OUT / "full-ui-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps({k: report[k] for k in ("states_captured", "transitions_queued", "remaining_frontier", "elapsed_seconds")}))
