#!/usr/bin/env python3
"""Report original drawable name candidates for the BLOODSKY component picker.

This is an audit only: filename matches do not prove a drawable belongs to a
specific original component. Manual visual/source confirmation is required.
"""
import argparse
from pathlib import Path
import re

COMPONENT_KEYWORDS = {
    "button": ("button", "btn", "switch"),
    "indicator": ("lamp", "led", "indicator"),
    "setpoint": ("input", "edit", "set", "numeric"),
    "scale": ("scale", "meter", "gauge", "arrow"),
    "progress": ("progress", "level", "bar"),
    "chart": ("chart", "graph", "trend"),
    "sound": ("sound", "alarm", "speaker"),
    "navigation": ("next", "screen", "link", "arrow"),
    "image": ("image", "photo", "picture"),
}
VALID = re.compile(r"^[a-z][a-z0-9_]*$")


def audit(drawable_dir: Path, output: Path):
    if not drawable_dir.is_dir():
        raise ValueError("Original drawable directory is missing")
    names = sorted(p.stem for p in drawable_dir.iterdir()
                   if p.is_file() and p.suffix.lower() == ".png"
                   and not p.name.endswith(".9.png") and VALID.fullmatch(p.stem))
    if len(names) < 100:
        raise ValueError("Original drawable catalogue is incomplete")
    lines = [
        "# Original Craft component-picker resource candidates",
        "",
        "Generated from pinned APK drawables. **Candidates only**, not confirmed UI mappings.",
        "Do not connect a drawable to a component without checking original source and screenshots.",
        "",
        f"Original PNG resources inspected: **{len(names)}**.",
        "",
    ]
    for component, keywords in COMPONENT_KEYWORDS.items():
        matches = [name for name in names if any(word in name for word in keywords)]
        lines += [f"## {component}", "", f"Filename candidates: **{len(matches)}**.", ""]
        lines += [f"- `{name}`" for name in matches] or ["No filename candidates."]
        lines += [""]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    return len(names)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("drawable_dir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print("Original drawable files audited:", audit(args.drawable_dir, args.output))
