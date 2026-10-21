#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:?output directory required}"
shift
mkdir -p "$OUT_DIR"

adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
adb shell svc data disable || true

if ! adb install -r build/KaScada-Smart-Craft-1.0.7-release.apk; then
  echo "Failed to install the hash-verified original Release APK; stopping without substituting a rebuilt app." >&2
  exit 1
fi
export KASCADA_PROBE_APK_SOURCE=original-release
export KASCADA_PROBE_APK_SHA256=11464db76f59393ccf698f57e60af1756756ffc78cb315d43052ae92adb50d4f

printf 'APK source: %s\nAPK SHA-256: %s\n' "$KASCADA_PROBE_APK_SOURCE" "$KASCADA_PROBE_APK_SHA256" | tee "$OUT_DIR/apk-source.txt"
python tools/original_craft_emulator_probe.py "$OUT_DIR" "$@"
