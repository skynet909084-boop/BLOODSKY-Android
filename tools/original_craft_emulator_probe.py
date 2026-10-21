#!/usr/bin/env python3
"""Capture a bounded quick tour or a broader UI audit of original Craft 1.0.7."""
from __future__ import annotations

import hashlib
import io
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter, deque
from pathlib import Path
from PIL import Image
from original_craft_probe_contract import ProbeBudget, ProbeDeadlineExceeded, quick_smoke_passes
from original_craft_probe_checkpoint import (
    atomic_write_json, checkpoint_from_run, load_checkpoint, new_checkpoint,
)

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "ru.kascada.modbus"
ADB_TIMEOUT = 45
PROFILE = "quick" if "--full" not in sys.argv[2:] else "full"
MAX_STATES = 12 if PROFILE == "quick" else 300
MAX_DEPTH = 8 if PROFILE == "quick" else 12
MAX_QUEUED = 240
MAX_ACTIONS_PER_STATE = 10 if PROFILE == "quick" else 240
MAX_RUNTIME_SECONDS = 5 * 60 if PROFILE == "quick" else 5 * 3600 + 45 * 60
WAIT = 2.0
APK_PROVENANCE = os.environ.get("ORIGINAL_CRAFT_APK_PROVENANCE", "unspecified")
APK_SHA256 = os.environ.get("ORIGINAL_CRAFT_APK_SHA256", "")
metrics = {"adb_calls": 0, "adb_seconds": 0.0, "ui_dumps": 0, "screenshots": 0,
           "fresh_starts": 0, "path_replays": 0, "states_new": 0,
           "states_duplicate": 0, "timeouts": 0}
budget = None


def adb(*args: str, check: bool = True) -> str:
    timeout = ADB_TIMEOUT
    if budget is not None:
        try:
            timeout = min(timeout, budget.check())
        except ProbeDeadlineExceeded:
            metrics["timeouts"] += 1
            raise
    began = time.monotonic()
    metrics["adb_calls"] += 1
    try:
        p = subprocess.run(["adb", *args], text=True, capture_output=True,
                           timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        metrics["timeouts"] += 1
        raise RuntimeError(f"adb {' '.join(args)} timed out after {timeout:.1f}s") from exc
    finally:
        metrics["adb_seconds"] += time.monotonic() - began
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout.strip()


def save_screenshot(png: Path) -> bool:
    """Save a verified screenshot, retrying transient adb output failures."""
    png.unlink(missing_ok=True)
    last_error = "empty screenshot output"
    for attempt in range(2):
        timeout = ADB_TIMEOUT
        if budget is not None:
            try:
                timeout = min(timeout, budget.check())
            except ProbeDeadlineExceeded:
                metrics["timeouts"] += 1
                last_error = "interaction budget expired"
                break
        began = time.monotonic()
        metrics["adb_calls"] += 1
        try:
            result = subprocess.run(
                ["adb", "exec-out", "screencap", "-p"],
                capture_output=True, timeout=timeout,
            )
            if result.returncode != 0:
                last_error = result.stderr.decode(errors="replace").strip() or f"adb exit {result.returncode}"
            elif not result.stdout:
                last_error = "empty screenshot output"
            else:
                with Image.open(io.BytesIO(result.stdout)) as image:
                    image.verify()
                png.write_bytes(result.stdout)
                metrics["screenshots"] += 1
                return True
        except subprocess.TimeoutExpired:
            metrics["timeouts"] += 1
            last_error = f"adb screenshot timed out after {timeout:.1f}s"
        except (OSError, SyntaxError, ValueError) as exc:
            last_error = f"invalid PNG: {exc}"
        finally:
            metrics["adb_seconds"] += time.monotonic() - began
        if attempt == 0:
            time.sleep(0.25)
    print(f"warning: screenshot unavailable for {png.name}: {last_error}", flush=True)
    png.unlink(missing_ok=True)
    return False


def dump_ui(label: str, capture_screenshot: bool = True):
    metrics["ui_dumps"] += 1
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
                "ru.kascada.modbus:id/tab_dyn_pictures",
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


def on_screen(node, state_label: str) -> bool:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    left, top, right, bottom = map(int, match.groups())
    with Image.open(OUT / f"{state_label}.png") as image:
        width, height = image.size
    return right > 0 and bottom > 0 and left < width and top < height


def execute_macro_drag(source_x: int, source_y: int, drop_x: int, drop_y: int,
                        duration_ms: int = 1600):
    """Use Android's native drag-and-drop input stream, including its long-press phase."""
    adb("shell", "input", "draganddrop", str(source_x), str(source_y),
        str(drop_x), str(drop_y), str(duration_ms))


def canvas_region(state_label: str) -> tuple[int, int, int]:
    """Return screenshot width, canvas top and bottom, using its flat background."""
    with Image.open(OUT / f"{state_label}.png") as image:
        width, height = image.size
        pixels = image.convert("RGB")
        left, right = int(width * 0.06), int(width * 0.92)
        flat_rows = []
        for y in range(int(height * 0.4), height):
            colors = [pixels.getpixel((x, y)) for x in range(left, right, 8)]
            color, count = Counter(colors).most_common(1)[0]
            if count / len(colors) >= 0.85 and min(color) >= 80 and max(color) - min(color) <= 20:
                flat_rows.append(y)
    runs = []
    for y in flat_rows:
        if not runs or y > runs[-1][-1] + 1:
            runs.append([y])
        else:
            runs[-1].append(y)
    region = max((run for run in runs if len(run) >= int(height * 0.08)), key=len, default=[])
    if region:
        return width, region[0], region[-1]
    return width, int(height * 0.64), height - 1


def canvas_probe_point(state_label: str) -> tuple[int, int]:
    """Pick the middle of the large, flat canvas region below Craft's palette."""
    width, top, bottom = canvas_region(state_label)
    return int(width * 0.75), (top + bottom) // 2


def placed_widget_point(state_label: str) -> tuple[int, int]:
    """Prefer the placed widget's accessibility bounds over a guessed canvas point."""
    try:
        root = ET.parse(OUT / f"{state_label}.xml").getroot()
    except (ET.ParseError, OSError):
        return canvas_probe_point(state_label)
    width, canvas_top, canvas_bottom = canvas_region(state_label)
    candidates = []
    for node in root.iter("node"):
        if node.attrib.get("package") != PACKAGE or not node.attrib.get("class", "").endswith("ImageView"):
            continue
        if node.attrib.get("clickable") != "true":
            continue
        if node.attrib.get("resource-id", "").endswith("tab_dyn_pictures"):
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        area = (right - left) * (bottom - top)
        if left >= 0 and top >= 0 and area >= 100_000:
            visible_top, visible_bottom = max(top, canvas_top), min(bottom, canvas_bottom)
            if visible_bottom > visible_top:
                candidates.append((area, (left + right) // 2, (visible_top + visible_bottom) // 2))
    if candidates:
        _, x, y = max(candidates)
        return x, y
    return canvas_probe_point(state_label)

def perform(action):
    if action["kind"] == "back":
        adb("shell", "input", "keyevent", "4")
    else:
        node = action["node"]
        x, y = str(node["x"]), str(node["y"])
        if action["kind"] == "fling_left":
            adb("shell", "input", "swipe", x, y, str(action["end_x"]), y,
                str(action.get("duration_ms", 260)))
        elif action["kind"] == "long_press":
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
    metrics["fresh_starts"] += 1
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
    metrics["path_replays"] += 1
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
frontier = [[]]
expanded_paths = []
started = time.time()
startup_seconds = None
marker = os.environ.get("ORIGINAL_CRAFT_START_MARKER", "")
if marker:
    try:
        startup_seconds = round(max(0.0, started - float(Path(marker).read_text().strip())), 1)
    except (OSError, ValueError):
        startup_seconds = None
budget = ProbeBudget(MAX_RUNTIME_SECONDS)
quick_goal_reached = False
launcher_observed = False
editor_observed = False
screenshot_verified = False
action_results = {"project_created": False, "palette_opened": False,
                  "component_properties_opened": False}
probe_error = ""


def save_checkpoint(complete=False):
    checkpoint = checkpoint_from_run(
        frontier, seen, state_rows, transition_rows, expanded_paths,
        APK_PROVENANCE, APK_SHA256, coverage_limited, PROFILE,
    )
    atomic_write_json(OUT / "checkpoint.json", checkpoint)
    report = {
        "app_package": PACKAGE,
        "apk_provenance": APK_PROVENANCE,
        "apk_sha256": APK_SHA256,
        "offline": True,
        "profile": PROFILE,
        "execution_finished": complete,
        "max_states": MAX_STATES,
        "max_path_depth": MAX_DEPTH,
        "max_actions_per_state": MAX_ACTIONS_PER_STATE,
        "runtime_limit_seconds": MAX_RUNTIME_SECONDS,
        "runtime_limit_hit": runtime_limit_hit,
        "coverage_limited": coverage_limited,
        "coverage_complete": (PROFILE == "full" and not runtime_limit_hit and not coverage_limited
                              and not probe_error and not frontier and len(state_rows) < MAX_STATES),
        "run_status": ("complete" if PROFILE == "full" and not runtime_limit_hit
                        and not coverage_limited and not probe_error and not frontier
                        and len(state_rows) < MAX_STATES else "incomplete"),
        "pending_frontier_paths": len(frontier),
        "coverage_cap_reasons": ([reason for reason, hit in (
            ("runtime_limit", runtime_limit_hit),
            ("state_limit", len(state_rows) >= MAX_STATES),
            ("depth_or_action_or_queue_limit", coverage_limited),
            ("probe_error", bool(probe_error)),
        ) if hit]),
        "guided_goal_reached": quick_goal_reached,
        "launcher_observed": launcher_observed,
        "editor_observed": editor_observed,
        "screenshot_verified": screenshot_verified,
        "action_results": dict(action_results),
        "quick_smoke_passed": quick_smoke_passes({
            "launcher_observed": launcher_observed,
            "editor_observed": editor_observed,
            "screenshot_verified": screenshot_verified,
            "guided_goal_reached": quick_goal_reached,
            "action_results": action_results,
            "runtime_limit_hit": runtime_limit_hit,
            "probe_error": probe_error,
        }) if PROFILE == "quick" else False,
        "probe_error": probe_error,
        "termination_reason": ("runtime_limit" if runtime_limit_hit else
                               "probe_error" if probe_error else
                               "guided_goal_reached" if quick_goal_reached else
                               "state_limit" if len(state_rows) >= MAX_STATES else
                               "depth_or_control_limit" if coverage_limited else "frontier_exhausted"),
        "states_captured": len(state_rows),
        "unique_state_signatures": len(seen),
        "transitions_visited": len(transition_rows),
        "elapsed_seconds": round(time.time() - started, 1),
        "interaction_seconds": round(time.time() - started, 1),
        "emulator_startup_seconds": startup_seconds,
        "metrics": {**metrics, "adb_seconds": round(metrics["adb_seconds"], 2)},
        "project_loading_stalled_states": sum(1 for row in state_rows if row["project_loading_stalled"]),
        "states": state_rows,
        "transitions": transition_rows,
        "k107_case_status": "WAITING_FOR_ANDROID_RUNTIME",
        "k107_cases_total": 550,
        "k107_cases_remaining": 550,
        "k107_case_ids": [f"K107-{i:04d}" for i in range(1, 551)],
        "k107_observations": [],
        "k107_mapping_note": "The 550-case static matrix is proposed in PR #192 at docs/reference-kascada-107/full_function_test_matrix.json. Generic traversal does not establish its per-case assertions, so captures remain unassigned and all IDs stay WAITING.",
        "runtime_error_log": "android-runtime-errors.log",
        "limitations": [
            "Quick profile targets the Craft ID17 palette-to-properties path; it is a focused sample, never a complete UI audit.",
            "Full profile explores up to 300 unique states with depth 12, up to 240 controls per state, and a 5h45m limit so artifact upload has time before the six-hour runner cap.",
            "Full traversal uses one app session with Back navigation and de-duplicates identical visible accessibility states; identical screens with different hidden data may merge.",
            "Depth, per-screen action, state, or wall-clock caps are recorded as incomplete coverage rather than a complete audit.",
            "The quick profile skips project-dialog orientation/cancel branches and FBD macro exploration to keep the normal component/property path short.",
            "Screenshots and accessibility trees are flushed to the report after each unique state.",
            "Unavailable Bluetooth and USB hardware is not simulated.",
            "Generic values are entered only into visible text fields to reveal subsequent screens; they are not valid device credentials.",
            "Project loading is observed for 2 seconds; a persistent loading screen is recorded once and is not expanded into duplicate branches."
        ],
    }
    target = OUT / "ui-audit.json"
    atomic_write_json(target, report)
    if len(state_rows) % 10 == 0:
        try:
            log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
            (OUT / "android-runtime-errors.log").write_text(log)
        except (ProbeDeadlineExceeded, RuntimeError):
            pass


def capture_state(path):
    global launcher_observed, editor_observed, screenshot_verified
    label = f"state-{len(state_rows):03d}"
    root, xml, png = dump_ui(label, capture_screenshot=False)
    package_nodes = [n for n in root.iter("node") if n.attrib.get("package") == PACKAGE]
    if not state_rows and package_nodes:
        launcher_observed = True
    editor_ids = ("menu_bar_component", "switch_layout", "tab_dyn_pictures", "worktoolbar")
    if any(any(token in n.attrib.get("resource-id", "").lower() for token in editor_ids)
           for n in package_nodes):
        editor_observed = True
    loading_stalled = is_project_loading(root)
    visible = visible_state(root)
    signature = hashlib.sha256(json.dumps(visible, ensure_ascii=False).encode()).hexdigest()
    if signature in seen:
        metrics["states_duplicate"] += 1
        (OUT / f"{label}.xml").unlink(missing_ok=True)
        return None
    seen.add(signature)

    # Save the expensive screenshot only for a new state; every unique capture
    # still waits WAIT seconds after the preceding action before reaching here.
    screenshot_ok = save_screenshot(png)
    screenshot_verified = screenshot_verified or screenshot_ok
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
    row = {"state": label, "signature": signature, "path": path_labels, "control_count": len(ctrls),
           "controls": ctrls, "screenshot_sha256": screenshot_hash,
           "project_loading_stalled": loading_stalled}
    state_rows.append(row)
    metrics["states_new"] += 1
    print(f"{label}: depth={len(path)} controls={len(ctrls)} loading_stalled={loading_stalled} path={' / '.join(path_labels[-3:])}", flush=True)
    if PROFILE == "quick":
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
    queue = deque(frontier)
    while queue and len(state_rows) < MAX_STATES:
        path = queue.popleft()
        replay(path)
        captured = capture_state(path)
        if captured is None:
            expanded_paths.append(path)
            frontier[:] = list(queue)
            save_checkpoint()
            continue
        _, _, actions = captured
        if len(path) >= MAX_DEPTH:
            if actions:
                coverage_limited = True
            continue
        if len(actions) > MAX_ACTIONS_PER_STATE:
            coverage_limited = True
        for action in actions[:MAX_ACTIONS_PER_STATE]:
            child = path + [action]
            if len(queue) + len(state_rows) >= MAX_QUEUED:
                coverage_limited = True
                break
            queue.append(child)
            transition_rows.append({
                "from": state_rows[-1]["state"],
                "from_signature": state_rows[-1]["signature"],
                "action": action["label"],
                "action_path": [step["label"] for step in child],
                "depth": len(child),
            })
        expanded_paths.append(path)
        frontier[:] = list(queue)
        save_checkpoint()
    return len(queue)


def guided_quick_walk():
    """Follow project creation -> Craft ID17 placement -> ID17 properties."""
    global coverage_limited, quick_goal_reached
    path = []
    captured = capture_state(path)
    if captured is None:
        return
    steps = [
        ("create project dialog", lambda n: "CREATE A NEW PROJECT" in n.get("text", "").upper()),
        ("project name", lambda n: n.get("field")),
        ("create project", lambda n: n.get("text", "").strip().upper() == "CREATE" and not n.get("field")),
        ("component palette", lambda n: n.get("resource_id", "").endswith("menu_bar_component")),
        ("drag Craft ID17 dynamic picture to canvas", lambda n: n.get("resource_id", "").endswith("tab_dyn_pictures")),
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
        if label == "drag Craft ID17 dynamic picture to canvas":
            drop_x, drop_y = canvas_probe_point(captured[0])
            action = {
                "kind": "drag", "node": action["node"], "drop_x": drop_x,
                "drop_y": drop_y, "duration_ms": 1600,
                "label": "DRAG Craft ID17 dynamic picture to canvas",
            }
        transition_rows.append({"from": captured[0], "action": action["label"], "depth": len(path) + 1})
        perform(action)
        path.append(action)
        captured = capture_state(path)
        latest_controls = state_rows[-1]["controls"] if state_rows else []
        if label == "create project":
            action_results["project_created"] = editor_observed
        elif label == "component palette":
            action_results["palette_opened"] = any(
                node.get("resource_id", "").endswith("tab_dyn_pictures")
                for node in latest_controls
            )
        if label == "drag Craft ID17 dynamic picture to canvas" and captured is not None:
            with Image.open(OUT / f"{captured[0]}.png") as image:
                width, height = image.size
            reveal_action = {
                "kind": "fling_left",
                "node": {"x": int(width * 0.82), "y": int(height * 0.55), "field": False},
                "end_x": int(width * 0.55), "duration_ms": 260,
                "label": "Swipe Craft palette left to expose canvas",
            }
            transition_rows.append({
                "from": captured[0], "action": reveal_action["label"],
                "depth": len(path) + 1,
            })
            perform(reveal_action)
            path.append(reveal_action)
            captured = capture_state(path)
        if label == "drag Craft ID17 dynamic picture to canvas" and captured is not None:
            property_x, property_y = placed_widget_point(captured[0])
            properties_action = {
                "kind": "tap", "node": {"x": property_x, "y": property_y, "field": False},
                "label": "Open Craft ID17 properties",
            }
            transition_rows.append({
                "from": captured[0], "action": properties_action["label"],
                "depth": len(path) + 1,
            })
            perform(properties_action)
            path.append(properties_action)
            captured = capture_state(path)
            visible_controls = [] if captured is None or not state_rows else state_rows[-1]["controls"]
            property_controls = [node for node in visible_controls if node.get("field")]
            palette_still_open = captured is not None and any(
                node.get("resource_id", "").endswith("tab_dyn_pictures")
                and on_screen(node, captured[0])
                for node in visible_controls
            )
            quick_goal_reached = bool(property_controls) and not palette_still_open
            action_results["component_properties_opened"] = quick_goal_reached
            coverage_limited = True
            if not quick_goal_reached:
                print(
                    "guided route incomplete: Craft ID17 property editor was not verified; "
                    f"captured={captured is not None}, palette_still_open={palette_still_open}, "
                    f"property_fields={len(property_controls)}",
                    flush=True,
                )


try:
    checkpoint_file = OUT / "checkpoint.json"
    if PROFILE == "full" and checkpoint_file.exists():
        restored = load_checkpoint(checkpoint_file, APK_PROVENANCE, APK_SHA256)
        frontier[:] = restored["frontier"]
        seen.update(restored["visited_signatures"])
        state_rows.extend(restored["states"])
        transition_rows.extend(restored["transitions"])
        expanded_paths.extend(restored["expanded_paths"])
        coverage_limited = bool(restored.get("coverage_limited"))
        print(f"resumed checkpoint: {len(state_rows)} states, {len(frontier)} pending paths", flush=True)
    fresh_start()
    if PROFILE == "quick":
        guided_quick_walk()
    else:
        fast_walk([])
except ProbeDeadlineExceeded as exc:
    runtime_limit_hit = True
    probe_error = str(exc)
except Exception as exc:
    probe_error = f"{type(exc).__name__}: {exc}"

if budget.remaining <= 0:
    runtime_limit_hit = True
    if not probe_error:
        probe_error = f"probe exceeded its {MAX_RUNTIME_SECONDS}s interaction budget"

# A failed or timed-out probe still publishes its partial diagnostics.
try:
    if budget.remaining > 0:
        log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
        (OUT / "android-runtime-errors.log").write_text(log)
except (ProbeDeadlineExceeded, RuntimeError):
    pass
save_checkpoint(complete=not probe_error)
summary = json.loads((OUT / "ui-audit.json").read_text())
print(json.dumps({k: summary[k] for k in (
    "profile", "states_captured", "transitions_visited", "interaction_seconds",
    "execution_finished", "quick_smoke_passed", "coverage_complete", "termination_reason",
    "metrics",
)}), flush=True)
if probe_error or (PROFILE == "quick" and not summary["quick_smoke_passed"]):
    sys.exit(1)

