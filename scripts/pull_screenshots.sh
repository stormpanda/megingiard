#!/usr/bin/env bash
# pull_screenshots.sh — Capture both AYN Thor screens via ADB and save locally.
# TOP  = port 131 (Built-in Screen), BOTTOM = port 132 (Screen-2).
# screencap -d takes the 64-bit unique display ID reported by SurfaceFlinger.
set -euo pipefail

ADB="${ADB:-$(command -v adb 2>/dev/null || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
DEVICE="${DEVICE:-}"   # leave empty to use the only connected device
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTDIR="$REPO_ROOT/screenshots"
TS="$(date +%Y%m%d_%H%M%S)"

ADB_CMD=("$ADB")
if [[ -n "$DEVICE" ]]; then
  ADB_CMD+=("-s" "$DEVICE")
fi

mkdir -p "$OUTDIR"

# Resolve unique display IDs from SurfaceFlinger (by hardware port number).
SF_IDS=$("${ADB_CMD[@]}" shell dumpsys SurfaceFlinger --display-id 2>/dev/null)
ID_TOP=$(echo "$SF_IDS"    | grep 'port=131' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1)
ID_BOTTOM=$(echo "$SF_IDS" | grep 'port=132' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1)

if [[ -z "$ID_TOP" || -z "$ID_BOTTOM" ]]; then
  echo "ERROR: Could not resolve display IDs. Output:" >&2
  echo "$SF_IDS" >&2
  exit 1
fi

echo "TOP    display ID: $ID_TOP"
echo "BOTTOM display ID: $ID_BOTTOM"

echo "Capturing TOP screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_TOP" -p /sdcard/mgnrd_top.png
"${ADB_CMD[@]}" pull /sdcard/mgnrd_top.png "$OUTDIR/${TS}_TOP.png"

echo "Capturing BOTTOM screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_BOTTOM" -p /sdcard/mgnrd_bottom.png
"${ADB_CMD[@]}" pull /sdcard/mgnrd_bottom.png "$OUTDIR/${TS}_BOTTOM.png"

"${ADB_CMD[@]}" shell rm /sdcard/mgnrd_top.png /sdcard/mgnrd_bottom.png

echo "Saved:"
echo "  $OUTDIR/${TS}_TOP.png"
echo "  $OUTDIR/${TS}_BOTTOM.png"
