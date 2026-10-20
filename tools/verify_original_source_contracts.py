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

    # Extract referenced suffixes (e.g. lamp_green from R.drawable.kc_lamp_green or "kc_lamp_green")
    direct_refs = set(re.findall(r'\bR\.drawable\.kc_([a-z0-9_]+)\b', java_code)) | set(re.findall(r'\"kc_([a-z0-9_]+)\"', java_code))

    used = []
    unused = []
    for img in images:
        stem = Path(img).stem
        normalized_stem = stem.removeprefix("kc_")
        if stem in direct_refs or normalized_stem in direct_refs:
            used.append(img)
        else:
            unused.append(img)

    alias_status = contracts.get("image_aliases_status", {})
    if alias_status.get("total_packaged_in_apk") != len(images):
        raise ValueError(f"Total image count mismatch: JSON={alias_status.get('total_packaged_in_apk')}, actual={len(images)}")

    if len(used) + len(unused) != len(images):
        raise ValueError("Sum of used and unused images does not equal total images")

    expected_used = alias_status.get("directly_referenced_in_bloodsky_app")
    if expected_used is None or len(used) != expected_used:
        raise ValueError(
            f"Referenced image count mismatch: JSON declared {expected_used}, "
            f"actual source scan found {len(used)}"
        )

    expected_unused = alias_status.get("packaged_but_unused_in_bloodsky")
    if expected_unused is None or len(unused) != expected_unused:
        raise ValueError(
            f"Unused image count mismatch: JSON declared {expected_unused}, "
            f"actual source scan found {len(unused)}"
        )

    # 5. Verify cited BLOODSKY production call sites exist in MainActivity.java
    main_activity_path = app_source_dir / "com/bloodsky/hmi/MainActivity.java"
    if main_activity_path.exists():
        main_code = main_activity_path.read_text(encoding="utf-8", errors="replace")
        cited_call_sites = set()

        for comp in contracts.get("components", []):
            cs = comp.get("bloodsky_call_site", "")
            if cs.startswith("com.bloodsky.hmi.MainActivity."):
                method = cs.split(".")[-1]
                cited_call_sites.add(method)

        for fbd in contracts.get("fbd_contracts", []):
            cs = fbd.get("bloodsky_call_site", "")
            if cs.startswith("com.bloodsky.hmi.MainActivity."):
                method = cs.split(".")[-1]
                cited_call_sites.add(method)

        for method in cited_call_sites:
            pattern = rf'\b{method}\s*\('
            if not re.search(pattern, main_code):
                raise ValueError(f"Cited call-site method '{method}' does not exist in MainActivity.java")

    # 6. Verify system register map against decompiled source q/d/b.java
    q_d_b_path = decoded_dir / "jadx/sources/ru/himik/nightscream/kascada/q/d/b.java"
    if q_d_b_path.exists():
        q_d_b_text = q_d_b_path.read_text(encoding="utf-8", errors="replace")
        if "f9265j" not in q_d_b_text or "2000" not in q_d_b_text:
            raise ValueError("System register array signature missing from q/d/b.java")

    # 7. Check synthetic fixtures
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
