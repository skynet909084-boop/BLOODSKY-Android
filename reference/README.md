# Recovery of original Craft assets

Source: user-provided KaScada Craft 1.0.7 decompilation, GitHub Actions artifact `11912849123` (`BLOODSKY-Kaskada-decompiled`), run `38600957172`. Recovered on 2026-10-16 from the private project; this is reference material for the authorized project, not a public release.

`craft-reference.zip` is a reduced, byte-preserving selection of that archive: apktool/res, apktool/assets, the app manifest, REPORT.md, and original app Java rendering sources under jadx/sources/ru/himik/nightscream/kascada. Native binaries and unrelated third-party source are excluded. Original decompilation limitations remain; these Java files are reference material, not buildable BLOODSKY code.

SHA-256: `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`. ZIP size: 6,871,095 bytes.

Run `python tools/prepare_craft_resources.py` from the repository root before Android builds. It verifies the archive checksum, rejects unsafe/duplicate paths and size-limit violations, unpacks reference sources under build/craft-reference, preserves the decoded resource/asset tree under build/craft-reference/bundled, and compiles image/audio aliases with the kc_ prefix. Original XML and service strings remain in the local build reference only; they are not packaged in BLOODSKY or compiled into its R namespace.

CI uses this committed archive, not a temporary artifact download. Reports catalogue exact files/hashes and missing literal resource bindings. The current main UI does not yet wire these images to all controls: packaging resources is NOT proof of working thumbnails or visual parity. The drawable audit currently finds zero explicit literal kc_ PNG references; dynamic bindings require separate verification. XML mapping reports unresolved references; inspect the generated report rather than treating every reference as resolved.

Generated files under build/craft-reference, app/src/main/res/drawable-nodpi/kc_* and app/src/main/res/raw/kc_* should not be manually edited. Keep the original user archive separately if unrelated native analysis is needed.
