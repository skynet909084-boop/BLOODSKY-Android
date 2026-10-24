#!/usr/bin/env python3
"""Bundle the *complete* decoded Craft resource trees outside the APK.

Original resource names, paths, and bytes are preserved for parity work under
build/craft-reference/bundled. Keeping the reference package outside app/src
prevents legacy service names and unused resources from shipping in BLOODSKY.
The selected kc_ PNG/audio aliases remain separately compiled for current UI.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import sys


def bundle(source_root: Path, app_root: Path) -> dict:
    source = source_root / "apktool"
    destination = app_root.parent / "build/craft-reference/bundled"
    if not (source / "res").is_dir() or not (source / "assets").is_dir():
        raise ValueError("Missing decoded apktool/res or apktool/assets")
    if destination.exists():
        shutil.rmtree(destination)
    manifest = []
    for group in ("res", "assets"):
        for item in sorted((source / group).rglob("*")):
            if not item.is_file():
                continue
            relative = item.relative_to(source)
            # Do not flatten, rename, or reinterpret source files.
            if any(part in ("..", "") for part in relative.parts):
                raise ValueError("Unsafe original resource path")
            target = destination.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(item, target)
            manifest.append({
                "path": relative.as_posix(),
                "size": item.stat().st_size,
                "sha256": hashlib.sha256(item.read_bytes()).hexdigest(),
            })
    groups = {
        "res": sum(x["path"].startswith("res/") for x in manifest),
        "assets": sum(x["path"].startswith("assets/") for x in manifest),
    }
    if groups["res"] < 500 or groups["assets"] < 4:
        raise ValueError(f"Incomplete original resource tree: {groups}")
    (destination / "MANIFEST.json").write_text(
        json.dumps({"source": "KaScada Craft 1.0.7 decompiled APK",
                    "groups": groups, "files": manifest},
                   ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return groups


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("decoded_root", type=Path)
    parser.add_argument("app_root", type=Path)
    args = parser.parse_args()
    print("Bundled original resource trees:", bundle(args.decoded_root, args.app_root))
