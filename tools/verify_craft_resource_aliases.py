#!/usr/bin/env python3
"""Fail the build when compiled Craft aliases drift from the pinned original APK.

The original reference archive is SHA-256 pinned by prepare_craft_resources.py.
This gate checks that the separately compiled kc_ drawable/audio aliases still
match their decoded source files byte for byte. It proves asset provenance,
not that any screen is pixel-identical to the original app.
"""
import argparse
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

DRAWABLE_SUFFIXES = {".png", ".jpg", ".jpeg"}
AUDIO_SUFFIXES = {".wav", ".mp3", ".ogg"}
ANDROID = "{http://schemas.android.com/apk/res/android}"
ORIGINAL_SELECTION_ACTIONS = {
    "kc_action_selectActionCut": "kc_action_cut",
    "kc_action_selectActionCopy": "kc_action_copy",
    "kc_action_selectActionPaste": "kc_action_paste",
    "kc_action_openDialogAlignComponents": "kc_align_open_menu",
}
ESSENTIAL_DRAWABLES = {
    "kc_main_background_small", "kc_action_cut", "kc_action_copy",
    "kc_action_paste", "kc_align_open_menu", "kc_menu_app_bar",
    "kc_start", "kc_stop",
}


def digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def image_files(source: Path):
    return sorted(
        p for p in source.iterdir()
        if p.is_file() and p.suffix.lower() in DRAWABLE_SUFFIXES
        and not p.name.lower().endswith(".9.png")
    )


def audio_files(source: Path):
    return sorted(
        p for p in source.iterdir()
        if p.is_file() and p.suffix.lower() in AUDIO_SUFFIXES
    )


def verify_selection_layout(layout: Path) -> None:
    if not layout.is_file():
        raise ValueError("Original selection-actions layout is missing: " + str(layout))
    tree = ET.parse(layout)
    action_nodes = {}
    for node in tree.iter():
        resource_id = node.attrib.get(ANDROID + "id", "")
        if resource_id.startswith("@+id/") or resource_id.startswith("@id/"):
            action_nodes[resource_id.split("/", 1)[1]] = node
    for view_id, image in ORIGINAL_SELECTION_ACTIONS.items():
        node = action_nodes.get(view_id)
        if node is None:
            raise ValueError("Original action missing from XML: " + view_id)
        if node.attrib.get(ANDROID + "background") != "@drawable/" + image:
            raise ValueError("Wrong original icon for action: " + view_id)
        if node.attrib.get(ANDROID + "scaleType") != "fitXY":
            raise ValueError("Original fitXY scale lost for action: " + view_id)
        for dimension in ("layout_width", "layout_height"):
            value = node.attrib.get(ANDROID + dimension, "")
            if value not in ("40dp", "40.0dp"):
                raise ValueError("Original 40dp dimension lost for action: " + view_id)


def verify(decoded_root: Path, app_root: Path, report: Path,
           min_images: int = 100, min_sounds: int = 10,
           verify_ui: bool = True) -> dict:
    original_res = decoded_root / "apktool" / "res"
    drawable_source = original_res / "drawable"
    audio_source = original_res / "raw"
    if not drawable_source.is_dir() or not audio_source.is_dir():
        raise ValueError("Pinned original drawable/raw trees are missing")
    files = [
        ("drawable", p, app_root / "src/main/res/drawable-nodpi" / ("kc_" + p.name))
        for p in image_files(drawable_source)
    ] + [
        ("raw", p, app_root / "src/main/res/raw" / ("kc_" + p.name))
        for p in audio_files(audio_source)
    ]
    images = sum(group == "drawable" for group, _, _ in files)
    sounds = sum(group == "raw" for group, _, _ in files)
    if images < min_images or sounds < min_sounds:
        raise ValueError(f"Incomplete original aliases: {images} images, {sounds} sounds")
    if verify_ui:
        names = {alias.stem for group, _, alias in files if group == "drawable"}
        missing = ESSENTIAL_DRAWABLES - names
        if missing:
            raise ValueError("Required original icons missing: " + ", ".join(sorted(missing)))
        verify_selection_layout(app_root / "src/main/res/layout/kc_selection_actions.xml")

    rows = []
    for group, original, alias in files:
        if not alias.is_file():
            raise ValueError("Original resource not compiled: " + str(alias))
        original_hash = digest(original)
        if original_hash != digest(alias):
            raise ValueError("Original resource bytes changed: " + str(alias))
        rows.append((group, original.relative_to(decoded_root).as_posix(),
                     alias.relative_to(app_root).as_posix(), original_hash))

    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# KaScada Craft 1.0.7 — compiled original resource integrity",
        "",
        "Generated from the SHA-256-pinned reference extraction. Each listed",
        "kc_ alias matches the original source file **byte for byte**.",
        "This report does not establish screenshot/pixel or runtime parity.",
        "",
        f"Verified original images: **{images}**.",
        f"Verified original sounds: **{sounds}**.",
        "Selection controls: original 40dp FIT_XY icons verified."
        if verify_ui else "Selection layout validation skipped in test fixture.",
        "",
        "| Group | Original path | Compiled alias | SHA-256 |",
        "|---|---|---|---|",
    ]
    lines.extend(
        f"| {group} | `{original}` | `{alias}` | `{sha}` |"
        for group, original, alias, sha in rows
    )
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return {"images": images, "sounds": sounds, "verified": len(rows)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--decoded", type=Path, default=Path("build/craft-reference"))
    parser.add_argument("--app", type=Path, default=Path("app"))
    parser.add_argument("--report", type=Path,
                        default=Path("build-reports/kascada-original-alias-integrity.md"))
    args = parser.parse_args()
    print(verify(args.decoded, args.app, args.report))
