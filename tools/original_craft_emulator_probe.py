#!/usr/bin/env python3
"""Capture a bounded quick tour or a broader UI audit of original Craft 1.0.7."""
from __future__ import annotations

import hashlib
import io
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path
from PIL import Image

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "ru.kascada.modbus"
ADB_TIMEOUT = 45
PROFILE = "quick" if "--full" not in sys.argv[2:] else "full"
MAX_STATES = 12 if PROFILE == "quick" else 300
MAX_DEPTH = 8 if PROFILE == "quick" else 12
MAX_QUEUED = 240
MAX_ACTIONS_PER_STATE = 10 if PROFILE == "quick" else 240
MAX_RUNTIME_SECONDS = 0 if PROFILE == "quick" else 5 * 3600 + 45 * 60
WAIT = 2.0


def adb(*args: str, check: bool = True) -> str:
    try:
        p = subprocess.run(["adb", *args], text=True, capture_output=True,
                           timeout=ADB_TIMEOUT)
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"adb {' '.join(args)} timed out after {ADB_TIMEOUT}s") from exc
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout.strip()


def save_screenshot(png: Path) -> bool:
    """Save a verified screenshot, retrying transient adb output failures."""
    png.unlink(missing_ok=True)
    last_error = "empty screenshot output"
    for attempt in range(2):
        try:
            result = subprocess.run(
                ["adb", "exec-out", "screencap", "-p"],
                capture_output=True, timeout=ADB_TIMEOUT,
            )
            if result.returncode != 0:
                last_error = result.stderr.decode(errors="replace").strip() or f"adb exit {result.returncode}"
            elif not result.stdout:
                last_error = "empty screenshot output"
            else:
                with Image.open(io.BytesIO(result.stdout)) as image:
                    image.verify()
                png.write_bytes(result.stdout)
                return True
        except subprocess.TimeoutExpired:
            last_error = f"adb screenshot timed out after {ADB_TIMEOUT}s"
        except (OSError, SyntaxError, ValueError) as exc:
            last_error = f"invalid PNG: {exc}"
        if attempt == 0:
            time.sleep(0.25)
    print(f"warning: screenshot unavailable for {png.name}: {last_error}", flush=True)
    png.unlink(missing_ok=True)
    return False


def dump_ui(label: str, capture_screenshot: bool = True):
    remote = "/sdcard/craft-audit-window.xml"
    adb("shell", "uiautomator", "dump", remote, check=False)
    local = OUT / f"{label}.xml"
    adb("pull", remote, str(local), check=False)
    try:
        root = ET.parse(local).getroot()
    except (ET.ParseError, OSError):
        root = ET.Element("hierarchy")
    png = OUT / f"{label}.png"
    if capture_screenshot:
        save_screenshot(png)
    return root, local.read_text(errors="replace") if local.exists() else "", png


def controls(root):
    out = []
    permission_message = next((n.attrib.get("text", "") for n in root.iter("node")
                               if n.attrib.get("resource-id", "").endswith("permission_message")), "")
    safe_permission_terms = ("photo", "media", "file", "camera", "location", "microphone", "audio")
    safe_permission = any(term in permission_message.lower() for term in safe_permission_terms)
    for node in root.iter("node"):
        package = node.attrib.get("package", "")
        resource_id = node.attrib.get("resource-id", "")
        permission_button = package == "com.android.permissioncontroller" and resource_id in (
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_deny_button",
        )
        if package not in ("", PACKAGE) and not permission_button:
            continue
        if permission_button and resource_id.endswith("permission_allow_button") and not safe_permission:
            continue
        cls = node.attrib.get("class", "")
        if "InputMethod" in cls:
            continue
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        visual_control = package == PACKAGE and (
            (PROFILE == "full" and (cls.endswith("ImageView") or cls.endswith("TextView")))
            or (PROFILE == "quick" and resource_id in {
                "ru.kascada.modbus:id/tab_fb_macro",
                "ru.kascada.modbus:id/tab_fb_timer",
                "ru.kascada.modbus:id/tab_fb_t_trigger",
            })
        )
        clickable = (node.attrib.get("clickable") == "true" or permission_button or visual_control) and node.attrib.get("enabled") != "false"
        field = cls.endswith("EditText") and node.attrib.get("enabled") != "false"
        if not clickable and not field:
            continue
        out.append({
            "resource_id": resource_id,
            "text": node.attrib.get("text", "")[:120],
            "description": node.attrib.get("content-desc", "")[:120],
            "hint": node.attrib.get("hint", "")[:120],
            "class": cls,
            "bounds": bounds,
            "x": (x1 + x2) // 2,
            "y": (y1 + y2) // 2,
            "field": field,
            "package": package,
            "permission_message": permission_message if permission_button else "",
            "system_permission": permission_button,
        })
    return out


def action_key(a):
    return (a["resource_id"], a["text"], a["description"], a["hint"],
            a["class"], a["bounds"], a["field"])


def execute_macro_drag(source_x: int, source_y: int, drop_x: int, drop_y: int,
                        duration_ms: int = 1600):
    """Use Android's native drag-and-drop input stream, including its long-press phase."""
    adb("shell", "input", "draganddrop", str(source_x), str(source_y),
        str(drop_x), str(drop_y), str(duration_ms))

def perform(action):
    if action["kind"] == "back":
        adb("shell", "input", "keyevent", "4")
    else:
        node = action["node"]
        x, y = str(node["x"]), str(node["y"])
        if action["kind"] == "long_press":
            # Android shell has no dedicated long-press verb; a zero-distance
            # swipe keeps the pointer down long enough to trigger View.onLongClick.
            adb("shell", "input", "swipe", x, y, x, y, str(action.get("duration_ms", 1100)))
        elif action["kind"] == "drag":
            node = action["node"]
            execute_macro_drag(
                node["x"], node["y"], action["drop_x"], action["drop_y"],
                action.get("duration_ms", 1600),
            )
        else:
            adb("shell", "input", "tap", x, y)
        if node.get("field"):
            time.sleep(0.3)
            hint = (node.get("hint", "") + " " + node.get("text", "")).lower()
            if any(x in hint for x in ("парол", "password", "ключ", "token")):
                value = "test"
            elif any(x in hint for x in ("имя проекта", "название проекта", "project name", "project title")):
                value = "CraftAudit"
            elif any(x in hint for x in ("порт", "port", "unit", "id", "колич", "count")):
                value = "1"
            elif "macro name" in hint or "name macro" in hint:
                value = "MacroTest"
            elif any(x in hint for x in ("адрес", "address", "host", "ip")):
                value = "127.0.0.1"
            else:
                value = "1"
            adb("shell", "input", "text", value)
            adb("shell", "input", "keyevent", "4")
    time.sleep(WAIT)


def fresh_start(clear_data: bool = True):
    adb("shell", "am", "force-stop", PACKAGE, check=False)
    if clear_data:
        adb("shell", "pm", "clear", PACKAGE, check=False)
    for permission in (
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
    ):
        adb("shell", "pm", "grant", PACKAGE, permission, check=False)
    adb("shell", "monkey", "-p", PACKAGE, "1", check=False)
    time.sleep(2.5)


def replay(path):
    # Keep project data during fallback path replay; the audit clears data only once.
    fresh_start(clear_data=False)
    for action in path:
        # Replay already observed prefixes, waiting after each tap. The main loop
        # captures only the newly explored endpoint instead of dumping every prefix.
        perform(action)


def is_project_loading(root):
    return any("Загрузка проекта" in n.attrib.get("text", "")
               for n in root.iter("node"))


def visible_state(root):
    loading = is_project_loading(root)
    out = []
    for n in root.iter("node"):
        if n.attrib.get("package", "") not in ("", PACKAGE):
            continue
        cls = n.attrib.get("class", "")
        text = n.attrib.get("text", "")
        if re.fullmatch(r"\d{1,2}:\d{2}", text):
            text = "<clock>"
        if loading and cls.endswith("ImageView"):
            continue
        out.append((n.attrib.get("resource-id", ""), cls, text,
                    n.attrib.get("content-desc", ""), n.attrib.get("bounds", ""),
                    n.attrib.get("checked", ""), n.attrib.get("selected", "")))
    return out


seen = set()
state_rows = []
transition_rows = []
started = time.time()


def save_checkpoint(complete=False):
    report = {
        "app_package": PACKAGE,
        "offline": True,
        "profile": PROFILE,
        "execution_finished": complete,
        "max_states": MAX_STATES,
        "max_path_depth": MAX_DEPTH,
        "max_actions_per_state": MAX_ACTIONS_PER_STATE,
        "runtime_limit_seconds": MAX_RUNTIME_SECONDS,
        "runtime_limit_hit": runtime_limit_hit,
        "coverage_limited": coverage_limited,
        "coverage_complete": (not runtime_limit_hit and not coverage_limited
                              and len(state_rows) < MAX_STATES),
        "termination_reason": ("runtime_limit" if runtime_limit_hit else
                               "state_limit" if len(state_rows) >= MAX_STATES else
                               "depth_or_control_limit" if coverage_limited else "frontier_exhausted"),
        "states_captured": len(state_rows),
        "unique_state_signatures": len(seen),
        "transitions_visited": len(transition_rows),
        "elapsed_seconds": round(time.time() - started, 1),
        "project_loading_stalled_states": sum(1 for row in state_rows if row["project_loading_stalled"]),
        "states": state_rows,
        "transitions": transition_rows,
        "runtime_error_log": "android-runtime-errors.log",
        "limitations": [
            "Quick profile visits at most 25 unique states, depth 5, and 5 controls per state.",
            "Full profile explores up to 300 unique states with depth 12, up to 240 controls per state, and a 5h45m limit so artifact upload has time before the six-hour runner cap.",
            "Full traversal uses one app session with Back navigation and de-duplicates identical visible accessibility states; identical screens with different hidden data may merge.",
            "Depth, per-screen action, state, or wall-clock caps are recorded as incomplete coverage rather than a complete audit.",
            "The quick profile prioritizes project creation and editor palette navigation; create-dialog orientation/cancel branches are skipped, so coverage remains partial. Custom non-clickable canvas targets may be absent.",
            "Screenshots and accessibility trees are flushed to the report after each unique state.",
            "Unavailable Bluetooth and USB hardware is not simulated.",
            "Generic values are entered only into visible text fields to reveal subsequent screens; they are not valid device credentials.",
            "Project loading is observed for 2 seconds; a persistent loading screen is recorded once and is not expanded into duplicate branches."
        ],
    }
    target = OUT / "ui-audit.json"
    temporary = OUT / "ui-audit.json.tmp"
    temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    temporary.replace(target)
    if len(state_rows) % 10 == 0:
        log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
        (OUT / "android-runtime-errors.log").write_text(log)


def capture_state(path):
    label = f"state-{len(state_rows):03d}"
    root, xml, png = dump_ui(label, capture_screenshot=False)
    loading_stalled = is_project_loading(root)
    visible = visible_state(root)
    signature = hashlib.sha256(json.dumps(visible, ensure_ascii=False).encode()).hexdigest()
    if signature in seen:
        (OUT / f"{label}.xml").unlink(missing_ok=True)
        return None

    # Save the expensive screenshot only for a new state; every unique capture
    # still waits WAIT seconds after the preceding action before reaching here.
    screenshot_ok = save_screenshot(png)
    ctrls = [] if loading_stalled else controls(root)
    if PROFILE == "quick" and not loading_stalled:
        # Fill the project name once and confirm; skip orientation/cancel branches.
        is_create_dialog = (any(n.get("field") for n in ctrls)
                            and any(n.get("text", "").strip().upper() == "CREATE"
                                    for n in ctrls))
        if is_create_dialog:
            focused = [n for n in ctrls if n.get("field")
                       or n.get("text", "").strip().upper() == "CREATE"]
            if len(focused) < len(ctrls):
                coverage_limited = True
                ctrls = focused
    if screenshot_ok and not loading_stalled:
        with Image.open(png) as image:
            width, height = image.size
            normalized = image.crop((0, min(60, height // 10), width, max(height // 10, height - 80))).convert("L").resize((64, 64))
            screenshot_hash = hashlib.sha256(normalized.tobytes()).hexdigest()
    else:
        screenshot_hash = ""
    if loading_stalled:
        screenshot_hash = ""
    path_labels = [a["label"] for a in path]
    row = {"state": label, "path": path_labels, "control_count": len(ctrls),
           "controls": ctrls, "screenshot_sha256": screenshot_hash,
           "project_loading_stalled": loading_stalled}
    state_rows.append(row)
    print(f"{label}: depth={len(path)} controls={len(ctrls)} loading_stalled={loading_stalled} path={' / '.join(path_labels[-3:])}", flush=True)
    save_checkpoint()

    actions = []
    seen_points = set()
    for n in ctrls:
        # Only edit a populated field when it still contains a prompt/placeholder.
        # Re-tapping a project-name field used to append "1" on every traversal,
        # filling the quick state budget before reaching the editor.
        if n.get("field") and n.get("text", "").strip():
            current = n["text"].strip().lower()
            placeholders = ("please, enter", "please enter", "paste link here",
                            "enter a new name", "macro name", "введите", "укажите")
            if not any(marker in current for marker in placeholders):
                continue
        point = (n["x"], n["y"])
        if point in seen_points:
            continue
        seen_points.add(point)
        actions.append({"kind": "tap", "node": n,
                        "label": (n["text"] or n["description"] or n["hint"] or n["resource_id"] or n["class"])[:100]})
    def action_priority(action):
        node = action["node"]
        resource_id = node.get("resource_id", "")
        label = " ".join((node.get("text", ""), node.get("description", ""),
                          node.get("hint", ""), resource_id)).lower()
        if resource_id.endswith("menu_bar_component"):
            return 0
        if resource_id.endswith("switch_layout"):
            return 1
        if resource_id.endswith("tab_fb_macro") or "macro" in label or "макрос" in label:
            return 2
        if "worktoolbar" in resource_id.lower() or "menu_appbar_right" in resource_id.lower():
            return 3
        if "slidingscreen" in resource_id.lower():
            return 4
        return 10

    actions.sort(key=action_priority)
    return label, signature, actions


runtime_limit_hit = False
coverage_limited = False


def fast_walk(path, parent_signature=None):
    global runtime_limit_hit, coverage_limited
    if MAX_RUNTIME_SECONDS and time.time() - started >= MAX_RUNTIME_SECONDS:
        runtime_limit_hit = True
        return
    if len(state_rows) >= MAX_STATES:
        return
    captured = capture_state(path)
    if captured is None:
        return False
    parent_label, signature, actions = captured
    if len(path) >= MAX_DEPTH:
        if actions:
            coverage_limited = True
        return True
    if len(actions) > MAX_ACTIONS_PER_STATE:
        coverage_limited = True
    for action in actions[:MAX_ACTIONS_PER_STATE]:
        if len(state_rows) >= MAX_STATES:
            break
        if MAX_RUNTIME_SECONDS and time.time() - started >= MAX_RUNTIME_SECONDS:
            runtime_limit_hit = True
            break
        transition_rows.append({"from": parent_label, "action": action["label"], "depth": len(path)+1})
        perform(action)
        child_was_new = fast_walk(path + [action], signature)
        if child_was_new is False and action["kind"] == "tap" and not action["node"].get("field"):
            # Many Craft controls (including project actions) are long-press
            # targets. Retry only when a normal tap produced no visible state.
            hold = {
                "kind": "long_press",
                "node": action["node"],
                "duration_ms": 1100,
                "label": "LONG PRESS: " + action["label"],
            }
            transition_rows.append({"from": parent_label, "action": hold["label"], "depth": len(path)+1})
            perform(hold)
            fast_walk(path + [hold], signature)
        adb("shell", "input", "keyevent", "4", check=False)
        time.sleep(WAIT)
        # Normally Android Back restores the parent without resetting Craft data.
        # Fall back to a single path replay only when a screen does not unwind cleanly.
        remote = "/sdcard/craft-audit-restore.xml"
        local = OUT / "restore-check.xml"
        adb("shell", "uiautomator", "dump", remote, check=False)
        adb("pull", remote, str(local), check=False)
        try:
            restored = ET.parse(local).getroot()
            restored_signature = hashlib.sha256(json.dumps(
                visible_state(restored), ensure_ascii=False).encode()).hexdigest()
        except (ET.ParseError, OSError):
            restored_signature = ""
        if restored_signature != signature:
            replay(path)


def full_walk():
    queue = deque([[]])
    while queue and len(state_rows) < MAX_STATES:
        path = queue.popleft()
        replay(path)
        captured = capture_state(path)
        if captured is None:
            continue
        _, _, actions = captured
        if len(path) >= MAX_DEPTH:
            continue
        for action in actions:
            child = path + [action]
            if len(queue) + len(state_rows) >= MAX_QUEUED:
                break
            queue.append(child)
    return len(queue)


def guided_quick_walk():
    """Follow the known project -> editor -> FBD -> macro route with ADB."""
    path = []
    captured = capture_state(path)
    if captured is None:
        return
    steps = [
        ("create project dialog", lambda n: "CREATE A NEW PROJECT" in n.get("text", "").upper()),
        ("project name", lambda n: n.get("field")),
        ("create project", lambda n: n.get("text", "").strip().upper() == "CREATE" and not n.get("field")),
        ("component palette", lambda n: n.get("resource_id", "").endswith("menu_bar_component")),
        ("FBD page", lambda n: n.get("resource_id", "").endswith("switch_layout")),
        ("drag macro block to canvas", lambda n: n.get("resource_id", "").endswith("tab_fb_macro")),
        ("macro page", lambda n: n.get("resource_id", "").endswith("switch_layout")),
    ]
    for label, predicate in steps:
        if captured is None:
            print(f"guided route stopped before {label}: previous screen was unchanged", flush=True)
            break
        actions = captured[2]
        action = next((a for a in actions if predicate(a["node"])), None)
        if action is None:
            available = [a["label"] for a in actions]
            print(f"guided route stopped before {label}: target absent; controls={available}", flush=True)
            coverage_limited = True
            break
        if label == "drag macro block to canvas":
            action = {
                "kind": "drag", "node": action["node"], "drop_x": 1150,
                "drop_y": 850, "duration_ms": 1600,
                "label": "DRAG macro block to canvas",
            }
        transition_rows.append({"from": captured[0], "action": action["label"], "depth": len(path) + 1})
        perform(action)
        path = path + [action]
        captured = capture_state(path)
        if label == "drag macro block to canvas" and captured is not None:
            controls = captured[2]
            name_node = next(
                (a["node"] for a in controls
                 if a["node"].get("resource_id", "").endswith("nameMacro")),
                None,
            )
            if name_node is not None:
                name_action = {
                    "kind": "tap", "node": name_node,
                    "label": "Enter macro name MacroTest",
                }
                transition_rows.append({
                    "from": captured[0], "action": name_action["label"],
                    "depth": len(path) + 1,
                })
                # Verify the field accepted text before dismissing the keyboard or scrolling.
                entered = False
                for attempt in range(2):
                    adb("shell", "input", "tap", str(name_node["x"]), str(name_node["y"]))
                    time.sleep(0.6)
                    adb("shell", "input", "text", "MacroTest")
                    time.sleep(0.5)
                    focused_root, _, _ = dump_ui(f"macro-name-entry-{attempt + 1}", capture_screenshot=True)
                    entered = any(
                        n.attrib.get("resource-id", "").endswith("nameMacro")
                        and n.attrib.get("text", "").strip() == "MacroTest"
                        for n in focused_root.iter("node")
                    )
                    print(
                        f"macro name entry attempt {attempt + 1}: accepted={entered}",
                        flush=True,
                    )
                    if entered:
                        break
                    adb("shell", "input", "keyevent", "4")
                    time.sleep(0.3)
                if not entered:
                    coverage_limited = True
                    print("guided route stopped: MacroTest was not accepted by nameMacro", flush=True)
                    break
                adb("shell", "input", "keyevent", "4")
                time.sleep(WAIT)
                path.append(name_action)
                captured = capture_state(path)

                scroll_label = "SCROLL macro form to output and Create"
                transition_rows.append({
                    "from": captured[0] if captured else "macro dialog",
                    "action": scroll_label, "depth": len(path) + 1,
                })
                path.append({"label": scroll_label})
                create_node = None
                for scroll_attempt in range(3):
                    adb("shell", "input", "swipe", "1200", "1280", "1200", "350", "700")
                    time.sleep(WAIT)
                    captured = capture_state(path)
                    create_node = next(
                        (a["node"] for a in (captured[2] if captured else [])
                         if a["node"].get("resource_id", "").endswith("bCreateMacro")),
                        None,
                    )
                    if create_node is not None:
                        print(f"macro form Create button visible after scroll {scroll_attempt + 1}", flush=True)
                        break
                if create_node is None:
                    coverage_limited = True
                    print("guided route stopped: bCreateMacro not exposed after three scrolls", flush=True)
                    break
                if create_node is not None:
                    create_action = {
                        "kind": "tap", "node": create_node,
                        "label": "Create macro",
                    }
                    transition_rows.append({
                        "from": captured[0], "action": create_action["label"],
                        "depth": len(path) + 1,
                    })
                    perform(create_action)
                    path.append(create_action)
                    captured = capture_state(path)


fresh_start()
if PROFILE == "quick":
    guided_quick_walk()
else:
    fast_walk([])
remaining_frontier = 0

# Capture all uncaught Android Java exceptions from this audit session.
log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
(OUT / "android-runtime-errors.log").write_text(log)
save_checkpoint(complete=True)
summary = json.loads((OUT / "ui-audit.json").read_text())
print(json.dumps({k: summary[k] for k in ("profile", "states_captured", "transitions_visited", "elapsed_seconds", "execution_finished", "coverage_complete", "termination_reason")}), flush=True)

