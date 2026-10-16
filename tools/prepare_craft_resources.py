#!/usr/bin/env python3
"""Prepare pinned Craft reference assets without downloading expiring CI artifacts."""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import shutil
import stat
import zipfile

from bundle_craft_resources import bundle

EXPECTED_SHA256 = "316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd"


def unpack_reference(archive, destination, expected_sha256=EXPECTED_SHA256):
    archive, destination = Path(archive), Path(destination)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected_sha256:
        raise ValueError("Craft reference checksum mismatch")
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) > 5000 or sum(i.file_size for i in entries) > 64 * 1024 * 1024:
            raise ValueError("Craft reference exceeds limits")
        seen = set()
        for item in entries:
            name = item.filename
            parts = name.removesuffix('/').split('/')
            if (not name or '\\' in name or any(p in ('', '.', '..') for p in parts)
                    or ':' in parts[0] or name.startswith('/') or name in seen
                    or stat.S_ISLNK(item.external_attr >> 16)):
                raise ValueError("Unsafe or duplicate Craft reference path")
            seen.add(name)
        # Validate the complete archive before writing any files.
        for item in entries:
            target = destination.joinpath(*PurePosixPath(item.filename).parts)
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read(item))
    return len(entries)


def prepare(archive, decoded_root, app_root):
    decoded_root, app_root = Path(decoded_root), Path(app_root)
    unpack_reference(archive, decoded_root)
    groups = bundle(decoded_root, app_root)
    drawable = app_root / 'src/main/res/drawable-nodpi'
    raw = app_root / 'src/main/res/raw'
    drawable.mkdir(parents=True, exist_ok=True)
    raw.mkdir(parents=True, exist_ok=True)
    images = sounds = 0
    for item in sorted((decoded_root / 'apktool/res/drawable').iterdir()):
        if item.suffix.lower() in ('.png', '.jpg', '.jpeg') and not item.name.endswith('.9.png'):
            shutil.copyfile(item, drawable / ('kc_' + item.name))
            images += 1
    for item in sorted((decoded_root / 'apktool/res/raw').iterdir()):
        if item.suffix.lower() in ('.wav', '.mp3', '.ogg'):
            shutil.copyfile(item, raw / ('kc_' + item.name))
            sounds += 1
    if images < 100 or sounds < 10:
        raise ValueError('Incomplete Craft image or sound catalogue')
    return dict(groups, compiled_images=images, compiled_sounds=sounds)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=Path('reference/craft-reference.zip'))
    parser.add_argument('--decoded', type=Path, default=Path('build/craft-reference'))
    parser.add_argument('--app', type=Path, default=Path('app'))
    args = parser.parse_args()
    print(prepare(args.archive, args.decoded, args.app))
