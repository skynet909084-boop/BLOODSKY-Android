#!/usr/bin/env python3
"""Verify original APK source contracts, system register maps, and image alias usage.

Reusable read-only extraction and validation tool.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from prepare_craft_resources import EXPECTED_SHA256, unpack_reference


def verify_contracts(
    reference_zip: Path,
    contracts_json: Path,
    decoded_dir: Path,
    app_source_dir: Path,
    fixtures_dir: Path
):
    # 1. Verify archive checksum
    archive_bytes = reference_zip.read_bytes()
    archive_sha = hashlib.sha256(archive_bytes).hexdigest()
    if archive_sha != EXPECTED_SHA256:
        raise ValueError(f"Reference zip SHA256 mismatch: {archive_sha} != {EXPECTED_SHA256}")

    # 2. Unpack reference if not already unpacked or incomplete
    if not (decoded_dir / "apktool/res/drawable").exists():
        unpack_reference(reference_zip, decoded_dir)

    # 3. Read contracts JSON
    contracts = json.loads(contracts_json.read_text(encoding="utf-8"))
    if contracts.get("archive_sha256") != EXPECTED_SHA256:
        raise ValueError("Contract JSON archive SHA mismatch")

    # 4. Verify 152 original image files
    drawable_dir = decoded_dir / "apktool/res/drawable"
    images = sorted([
        p.name for p in drawable_dir.iterdir()
        if p.suffix.lower() in (".png", ".jpg") and not p.name.endswith(".9.png")
    ])
    if len(images) != 152:
        raise ValueError(f"Expected 152 original image files, found {len(images)}")

    java_files = list(app_source_dir.rglob("*.java"))
    java_code = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in java_files)
    direct_refs = set(re.findall(r'R\.drawable\.kc_([a-z0-9_]+)', java_code)) | set(re.findall(r'\"kc_([a-z0-9_]+)\"', java_code))

    used = [img for img in images if Path(img).stem in direct_refs]
    unused = [img for img in images if Path(img).stem not in direct_refs]

    alias_status = contracts.get("image_aliases_status", {})
    if alias_status.get("total_packaged_in_apk") != len(images):
        raise ValueError("Image alias count mismatch in contracts JSON")
    if alias_status.get("directly_referenced_in_bloodsky_java") != len(used):
        raise ValueError("Used image count mismatch in contracts JSON")
    if alias_status.get("packaged_but_unused_in_bloodsky") != len(unused):
        raise ValueError("Unused image count mismatch in contracts JSON")

    # 5. Verify system register map against decompiled source q/d/b.java
    q_d_b_path = decoded_dir / "jadx/sources/ru/himik/nightscream/kascada/q/d/b.java"
    if q_d_b_path.exists():
        q_d_b_text = q_d_b_path.read_text(encoding="utf-8", errors="replace")
        if "f9265j" not in q_d_b_text or "2000" not in q_d_b_text:
            raise ValueError("System register array signature missing from q/d/b.java")

    # 6. Check synthetic fixtures
    synthetic_files = list(fixtures_dir.glob("synthetic_*.json"))
    if not synthetic_files:
        raise ValueError("No synthetic test fixtures found in reference/fixtures/")
    for syn in synthetic_files:
        data = json.loads(syn.read_text(encoding="utf-8"))
        if "_comment" not in data or "SYNTHETIC FIXTURE" not in data["_comment"]:
            raise ValueError(f"Fixture {syn.name} missing synthetic label in _comment")

    return {
        "status": "OK",
        "total_images": len(images),
        "referenced_images": len(used),
        "unused_images": len(unused),
        "system_registers_count": len(contracts["system_registers"]["map"]),
        "fbd_blocks_count": len(contracts["fbd_contracts"]),
        "synthetic_fixtures": len(synthetic_files)
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, default=Path("reference/craft-reference.zip"))
    parser.add_argument("--contracts", type=Path, default=Path("docs/ORIGINAL_APK_SOURCE_CONTRACTS.json"))
    parser.add_argument("--decoded", type=Path, default=Path("build/craft-reference"))
    parser.add_argument("--app", type=Path, default=Path("app/src/main/java"))
    parser.add_argument("--fixtures", type=Path, default=Path("reference/fixtures"))
    args = parser.parse_args()

    res = verify_contracts(args.zip, args.contracts, args.decoded, args.app, args.fixtures)
    print("Source Contracts Verification Results:")
    print(json.dumps(res, indent=2))
