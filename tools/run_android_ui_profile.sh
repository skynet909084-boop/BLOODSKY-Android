#!/usr/bin/env bash
set -euo pipefail

apk="${1:?Usage: bash tools/run_android_ui_profile.sh <debug-apk>}"
case "${BLOODSKY_UI_PROFILE:-full}" in
  quick) exec python tools/android_ui_fbd_quick.py --apk "$apk" ;;
  full)  exec python tools/android_ui_smoke.py --apk "$apk" ;;
  *) echo "Unsupported BLOODSKY_UI_PROFILE: $BLOODSKY_UI_PROFILE" >&2; exit 2 ;;
esac
