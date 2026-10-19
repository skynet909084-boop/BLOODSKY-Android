#!/usr/bin/env python3
"""Capture Craft 1.0.7 UI states, including accessibility-declared long presses."""
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
from PIL import Image
from original_craft_probe_state import is_project_loading
from original_craft_longpress_state import (
    LONG_PRESS_MS, action_identity, actions_for_controls, safe_field_value, touch_command,
)

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "ru.kascada.modbus"
ADB_TIMEOUT = 15
PROFILE = "quick" if "--full" not in sys.argv[2:] else "full"
DIRECTION = next((arg.partition("=")[2] for arg in sys.argv[2:] if arg.startswith("--direction=")), "balanced")
MAX_STATES = 20 if PROFILE == "quick" else 100
MAX_DEPTH = 4 if PROFILE == "quick" else 8
MAX_QUEUED = 240
MAX_ACTIONS_PER_STATE = 6 if PROFILE == "quick" else 12
MAX_RUNTIME_SECONDS = 0 if PROFILE == "quick" else 50 * 60
WAIT = 0.5
SEED_EDITOR_DIRECTIONS = {"project-create", "components", "fbd", "macros", "devices", "network", "runtime"}


def adb(*args: str, check: bool = True) -> str:
    try:
        p = subprocess.run(["adb", *args], text=True, capture_output=True,
                           timeout=ADB_TIMEOUT)
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"adb {' '.join(args)} timed out after {ADB_TIMEOUT}s") from exc
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout.strip()


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
        with png.open("wb") as f:
            try:
                subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f,
                               check=False, timeout=ADB_TIMEOUT)
            except subprocess.TimeoutExpired as exc:
                raise RuntimeError(f"adb screenshot timed out after {ADB_TIMEOUT}s") from exc
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
        visual_control = PROFILE == "full" and package == PACKAGE and (
            cls.endswith("ImageView") or cls.endswith("TextView")
        )
        clickable = (node.attrib.get("clickable") == "true" or permission_button or visual_control) and node.attrib.get("enabled") != "false"
        field = cls.endswith("EditText") and node.attrib.get("enabled") != "false"
        long_clickable = node.attrib.get("long-clickable") == "true" and node.attrib.get("enabled") != "false"
        if not clickable and not field and not long_clickable:
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
            "clickable": clickable or permission_button,
            "long_clickable": long_clickable,
            "package": package,
            "permission_message": permission_message if permission_button else "",
            "system_permission": permission_button,
        })
    return out


def action_key(a):
    return (a["resource_id"], a["text"], a["description"], a["hint"],
            a["class"], a["bounds"], a["field"])


def perform(action):
    if action["kind"] == "back":
        adb("shell", "input", "keyevent", "4")
    else:
        node = action["node"]
        adb(*touch_command(action, LONG_PRESS_MS))
        if node.get("field") and action["kind"] == "tap":
            time.sleep(0.3)
            # Only enter generic probe data when the accessibility hint clearly
            # identifies a known field. In particular, never append "1" to an
            # existing screen name: that created Screen1, Screen11, ... branches.
            value = safe_field_value(node.get("hint", ""))
            if value is not None:
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


def visible_state(root):
    loading = is_project_loading(root)
    if loading:
        # Collapse a spinner and its changing elapsed-time label to one state.
        return [("__craft_project_loading__", "loading", "project-loading", "", "", "", "")]
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


project_seed_result = {"attempted": False, "success": None, "elapsed_seconds": 0.0, "failure": ""}

def _label(node):
    return " ".join((node.get("text", ""), node.get("description", ""),
                     node.get("hint", ""), node.get("resource_id", ""))).strip().casefold()


def _find_control(root, predicate):
    return next((node for node in controls(root) if predicate(_label(node), node)), None)


def _wait_screen_change(previous, timeout=15.0, wait_out_loading=False):
    deadline = time.time() + timeout
    latest = None
    while time.time() < deadline:
        time.sleep(1.0)
        latest, _, _ = dump_ui("seed-check", capture_screenshot=False)
        loading = is_project_loading(latest)
        signature = hashlib.sha256(json.dumps(
            visible_state(latest), ensure_ascii=False).encode()).hexdigest()
        if (wait_out_loading and not loading) or (not wait_out_loading and signature != previous and not loading):
            return latest, True
    return latest, False


def _seed_tap(root, predicate):
    node = _find_control(root, predicate)
    if node is None:
        return root, False
    before = hashlib.sha256(json.dumps(
        visible_state(root), ensure_ascii=False).encode()).hexdigest()
    adb("shell", "input", "tap", str(node["x"]), str(node["y"]))
    time.sleep(WAIT)
    latest, changed = _wait_screen_change(before)
    return (latest if latest is not None else root), changed


def seed_project():
    """Create one project before spending time on editor navigation."""
    global project_seed_result
    started_seed = time.time()
    project_seed_result = {"attempted": True, "success": False,
                           "elapsed_seconds": 0.0, "failure": ""}
    root, _, _ = dump_ui("seed-start", capture_screenshot=True)
    root, changed = _seed_tap(
        root, lambda label, node: "create a new project" in label or "создать новый проект" in label)
    if not changed:
        project_seed_result["failure"] = "new-project-entry-not-found-or-no-transition"
        return False

    portrait = _find_control(root, lambda label, node: "portrait orientation" in label or "портретная ориентация" in label)
    if portrait:
        before = hashlib.sha256(json.dumps(visible_state(root), ensure_ascii=False).encode()).hexdigest()
        adb("shell", "input", "tap", str(portrait["x"]), str(portrait["y"]))
        time.sleep(WAIT)
        latest, _ = _wait_screen_change(before)
        if latest is not None:
            root = latest

    field = _find_control(root, lambda label, node: node.get("field", False))
    if field is None:
        project_seed_result["failure"] = "project-name-field-not-found"
        return False
    adb("shell", "input", "tap", str(field["x"]), str(field["y"]))
    time.sleep(0.2)
    adb("shell", "input", "text", "CraftAudit")
    adb("shell", "input", "keyevent", "4")
    time.sleep(WAIT)
    root, _, _ = dump_ui("seed-named-project", capture_screenshot=True)

    submit = _find_control(root, lambda label, node: (node.get("text", "").strip().casefold() in {"create", "создать"} or node.get("content-desc", "").strip().casefold() in {"create", "создать"} or node.get("resource_id", "").rsplit("/", 1)[-1].casefold() in {"create", "создать"}))
    if submit is None:
        project_seed_result["failure"] = "create-submit-not-found"
        return False
    adb("shell", "input", "tap", str(submit["x"]), str(submit["y"]))
    time.sleep(WAIT)

    deadline = time.time() + 90.0
    last_screenshot = -10
    while time.time() < deadline:
        elapsed = int(time.time() - started_seed)
        root, _, _ = dump_ui("seed-create-%03d" % elapsed,
                             capture_screenshot=(elapsed - last_screenshot >= 10))
        if elapsed - last_screenshot >= 10:
            last_screenshot = elapsed
        if not is_project_loading(root):
            project_seed_result["success"] = True
            project_seed_result["elapsed_seconds"] = round(time.time() - started_seed, 1)
            print("project seed reached a non-loading screen after %.1fs" %
                  project_seed_result["elapsed_seconds"], flush=True)
            return True
        time.sleep(2.0)

    project_seed_result["elapsed_seconds"] = round(time.time() - started_seed, 1)
    project_seed_result["failure"] = "project-loading-persisted-for-90-seconds"
    print("project seed still loading after 90s; preserving evidence and skipping blind traversal", flush=True)
    return False


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
                              and not probe_error and len(state_rows) < MAX_STATES),
        "probe_error": probe_error,
        "termination_reason": ("adb_timeout" if "timed out after" in probe_error else
                               "adb_or_probe_error" if probe_error else
                               "runtime_limit" if runtime_limit_hit else
                               "state_limit" if len(state_rows) >= MAX_STATES else
                               "depth_control_or_cycle_limit" if coverage_limited else "frontier_exhausted"),
        "states_captured": len(state_rows),
        "unique_state_signatures": len(seen),
        "transitions_visited": len(transition_rows),
        "long_press_controls_available": sum(row["long_press_controls_available"] for row in state_rows),
        "long_press_actions_executed": sum(1 for row in transition_rows if row["kind"] == "long_press"),
        "elapsed_seconds": round(time.time() - started, 1),
        "project_loading_stalled_states": sum(1 for row in state_rows if row["project_loading_stalled"]),
        "repeated_actions_pruned": cycle_pruned_actions,
        "project_seed": project_seed_result,
        "states": state_rows,
        "transitions": transition_rows,
        "runtime_error_log": "android-runtime-errors.log",
        "limitations": [
            "Quick profile visits at most 20 unique states, depth 4, and 6 controls per state.",
            "Full profile explores up to 100 unique states with depth 8, up to 12 actions per state, and a 50-minute limit so artifact upload has time.",
            "Full traversal uses one app session with Back navigation and de-duplicates identical visible accessibility states; identical screens with different hidden data may merge.",
            "Depth, per-screen action, state, wall-clock caps, and repeated-action cycle pruning are recorded as incomplete coverage rather than a complete audit.",
            "Only views marked long-clickable=true in the Android accessibility tree receive a one-second stationary long press.",
            "The quick profile follows clickable accessibility controls, text fields, and explicitly long-clickable views, so custom canvas targets may be absent.",
            "Screenshots and accessibility trees are flushed to the report after each unique state.",
            "Unavailable Bluetooth and USB hardware is not simulated.",
            "Generic values are entered only into visible text fields to reveal subsequent screens; they are not valid device credentials.",
            "Project creation is observed for up to 90 seconds before traversal; persistent loading is recorded once and not expanded into duplicate branches."
        ],
    }
    target = OUT / "ui-audit.json"
    temporary = OUT / "ui-audit.json.tmp"
    temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    temporary.replace(target)
    if len(state_rows) % 10 == 0:
        try:
            log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
            (OUT / "android-runtime-errors.log").write_text(log)
        except RuntimeError as exc:
            (OUT / "android-runtime-errors.log").write_text(f"Logcat unavailable: {exc}\\n")


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
    with png.open("wb") as f:
        try:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f,
                           check=False, timeout=ADB_TIMEOUT)
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError(f"adb screenshot timed out after {ADB_TIMEOUT}s") from exc
    loading_stalled = is_project_loading(root)
    ctrls = [] if loading_stalled else controls(root)
    if png.exists() and not loading_stalled:
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
           "long_press_controls_available": sum(1 for c in ctrls if c.get("long_clickable")),
           "controls": ctrls, "screenshot_sha256": screenshot_hash,
           "project_loading_stalled": loading_stalled}
    state_rows.append(row)
    print(f"{label}: depth={len(path)} controls={len(ctrls)} loading_stalled={loading_stalled} path={' / '.join(path_labels[-3:])}", flush=True)
    save_checkpoint()

    actions = actions_for_controls(ctrls, DIRECTION)
    return label, signature, actions


runtime_limit_hit = False
coverage_limited = False
probe_error = ""
cycle_pruned_actions = 0


def fast_walk(path, parent_signature=None):
    global runtime_limit_hit, coverage_limited, cycle_pruned_actions
    if MAX_RUNTIME_SECONDS and time.time() - started >= MAX_RUNTIME_SECONDS:
        runtime_limit_hit = True
        return
    if len(state_rows) >= MAX_STATES:
        return
    captured = capture_state(path)
    if captured is None:
        return
    parent_label, signature, actions = captured
    if len(path) >= MAX_DEPTH:
        if actions:
            coverage_limited = True
        return
    if len(actions) > MAX_ACTIONS_PER_STATE:
        coverage_limited = True
    path_action_ids = {action_identity(previous) for previous in path}
    for action in actions[:MAX_ACTIONS_PER_STATE]:
        if action_identity(action) in path_action_ids:
            cycle_pruned_actions += 1
            coverage_limited = True
            continue
        if len(state_rows) >= MAX_STATES:
            break
        if MAX_RUNTIME_SECONDS and time.time() - started >= MAX_RUNTIME_SECONDS:
            runtime_limit_hit = True
            break
        transition_rows.append({"from": parent_label, "action": action["label"],
                                 "kind": action["kind"], "depth": len(path)+1})
        perform(action)
        fast_walk(path + [action], signature)
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


try:
    fresh_start()
    seed_ok = True
    if PROFILE == "full" and DIRECTION in SEED_EDITOR_DIRECTIONS:
        seed_ok = seed_project()
        save_checkpoint()
    if seed_ok:
        fast_walk([])
except RuntimeError as exc:
    probe_error = str(exc)
    print(f"Probe stopped safely; preserving partial evidence: {probe_error}", flush=True)
remaining_frontier = 0

# Capture all uncaught Android Java exceptions from this audit session when ADB is responsive.
try:
    log = adb("logcat", "-d", "-v", "brief", "AndroidRuntime:E", "*:S", check=False)
except RuntimeError as exc:
    log = f"Logcat unavailable: {exc}\\n"
(OUT / "android-runtime-errors.log").write_text(log)
save_checkpoint(complete=True)
summary = json.loads((OUT / "ui-audit.json").read_text())
print(json.dumps({k: summary[k] for k in ("profile", "states_captured", "transitions_visited", "elapsed_seconds", "execution_finished", "coverage_complete", "termination_reason")}), flush=True)

