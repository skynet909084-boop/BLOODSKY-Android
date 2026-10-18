#!/usr/bin/env python3
"""Capture the original Craft 1.0.7 overflow menu and exercise its visible entries.

The emulator is deliberately offline. Each action result is captured before returning
to the main screen, so dialogs and navigation targets can be compared to screenshots.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "ru.kascada.modbus"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(["adb", *args], text=True, capture_output=True)
    if check and p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout.strip()


def capture(label: str) -> ET.Element:
    remote = "/sdcard/craft-probe-window.xml"
    adb("shell", "uiautomator", "dump", remote, check=False)
    adb("pull", remote, str(OUT / f"{label}.xml"), check=False)
    subprocess.run(["adb", "exec-out", "screencap", "-p"], check=False,
                   stdout=(OUT / f"{label}.png").open("wb"))
    try:
        return ET.parse(OUT / f"{label}.xml").getroot()
    except (ET.ParseError, OSError):
        return ET.Element("hierarchy")


def nodes(root: ET.Element):
    found = []
    for n in root.iter("node"):
        b = n.attrib.get("bounds", "")
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m or n.attrib.get("clickable") != "true" or n.attrib.get("enabled") == "false":
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        found.append({
            "text": n.attrib.get("text", ""),
            "description": n.attrib.get("content-desc", ""),
            "class": n.attrib.get("class", ""),
            "bounds": b,
            "x": (x1 + x2) // 2,
            "y": (y1 + y2) // 2,
            "right": x2,
            "top": y1,
        })
    return found


def tap(item):
    adb("shell", "input", "tap", str(item["x"]), str(item["y"]))
    time.sleep(1.2)


def start_app():
    adb("shell", "monkey", "-p", PACKAGE, "1", check=False)
    time.sleep(5)


initial = capture("01-main-screen")
clickables = nodes(initial)
menu = next((n for n in clickables if re.search(
    r"menu|more|overflow|ещ[её]|дополн|больше",
    n["text"] + " " + n["description"], re.I)), None)
if menu is None:
    # Craft 1.0.7 uses an icon-only overflow affordance on the upper-right.
    menu = max((n for n in clickables if n["right"] >= 0.82 * 1080 and n["top"] < 180),
               key=lambda n: n["right"], default=None)

report = {
    "package": PACKAGE,
    "offline": True,
    "initial_clickable_count": len(clickables),
    "initial_clickables": clickables,
    "overflow_button": menu,
    "menu_entries": [],
    "note": "",
}
if menu is None:
    report["note"] = "No upper-right accessible overflow control was found; main screen evidence was still saved."
else:
    tap(menu)
    menu_root = capture("02-overflow-menu")
    entries = nodes(menu_root)
    report["menu_entries"] = entries
    for i, entry in enumerate(entries, 1):
        label = re.sub(r"[^\w-]+", "-", (entry["text"] or entry["description"] or f"item-{i}"), flags=re.UNICODE).strip("-")[:48]
        tap(entry)
        screen = capture(f"menu-{i:02d}-{label or 'item'}")
        report["menu_entries"][i - 1]["result_clickables"] = nodes(screen)
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        # Re-open the overflow menu for the next sibling action.
        tap(menu)
        capture(f"menu-restored-{i:02d}")
    report["note"] = "Every accessible entry in the overflow menu was tapped once; each result and the restored menu were captured."

(OUT / "probe-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps({"initial_clickables": len(clickables), "overflow_found": menu is not None,
                  "menu_entries": len(report["menu_entries"]), "note": report["note"]}, ensure_ascii=False))
