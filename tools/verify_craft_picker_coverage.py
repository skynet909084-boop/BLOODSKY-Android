#!/usr/bin/env python3
"""Check exact parity of Craft PNG filenames and the Android visual picker."""
import argparse
from pathlib import Path
import re


def verify(drawable_dir: Path, java_file: Path):
    original = {
        p.stem for p in drawable_dir.glob("*.png")
        if not p.name.endswith(".9.png")
    }
    source = java_file.read_text(encoding="utf-8")
    start = source.index("private static final String[] CRAFT_IMAGE_NAMES = {")
    end = source.index("};", start)
    names = re.findall(r'"([a-z0-9_]+)"', source[start:end])
    available = set(names)
    missing = sorted(original - available)
    stale = sorted(available - original)
    duplicates = len(names) - len(available)
    if missing or stale or duplicates:
        raise ValueError(
            f"Original artwork picker mismatch: missing={missing}, "
            f"stale={stale}, duplicates={duplicates}"
        )
    return len(original)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("original_drawable_directory", type=Path)
    parser.add_argument("main_activity_java", type=Path)
    args = parser.parse_args()
    print("Exact original image picker coverage:", verify(
        args.original_drawable_directory, args.main_activity_java))
