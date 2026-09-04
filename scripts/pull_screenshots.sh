#!/usr/bin/env bash
# pull_screenshots.sh — Capture both AYN Thor screens via ADB and save locally.
# TOP  = port 131 (Built-in Screen), BOTTOM = port 132 (Screen-2).
# screencap -d takes the 64-bit unique display ID reported by SurfaceFlinger.
set -euo pipefail

ADB="${ADB:-$(command -v adb 2>/dev/null || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
DEVICE="${DEVICE:-}"   # leave empty to auto-detect connected device
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTDIR="$REPO_ROOT/screenshots"
TS="$(date +%Y%m%d_%H%M%S)"

if [[ ! -x "$ADB" ]] && ! command -v "$ADB" >/dev/null 2>&1; then
  echo "ERROR: ADB not found at '$ADB'. Please ensure Android SDK platform-tools is installed." >&2
  exit 1
fi

# Auto-detect device if not explicitly provided
if [[ -z "$DEVICE" ]]; then
  raw_devices=$("$ADB" devices 2>/dev/null | grep -v "List of devices" | grep -E '[[:space:]]device$' | awk '{print $1}')
  devices=()
  for dev in $raw_devices; do
    [[ -n "$dev" ]] && devices+=("$dev")
  done

  if [[ ${#devices[@]} -eq 0 ]]; then
    echo "ERROR: No connected Thor/Android device found via ADB." >&2
    exit 1
  elif [[ ${#devices[@]} -eq 1 ]]; then
    DEVICE="${devices[0]}"
  else
    # Prefer a USB-connected device (not containing ':' or '_adb')
    DEVICE="${devices[0]}"
    for d in "${devices[@]}"; do
      if [[ "$d" != *":"* && "$d" != *"_adb"* ]]; then
        DEVICE="$d"
        break
      fi
    done
    echo "WARN: Multiple ADB devices detected (${devices[*]}). Using: $DEVICE" >&2
  fi
fi

ADB_CMD=("$ADB" "-s" "$DEVICE")

mkdir -p "$OUTDIR"

# Resolve unique display IDs from SurfaceFlinger (by hardware port number).
if ! SF_IDS=$("${ADB_CMD[@]}" shell dumpsys SurfaceFlinger --display-id 2>&1); then
  echo "ERROR: Failed to query display IDs via SurfaceFlinger on device ($DEVICE):" >&2
  echo "$SF_IDS" >&2
  exit 1
fi

ID_TOP=$(echo "$SF_IDS"    | grep 'port=131' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1 || true)
ID_BOTTOM=$(echo "$SF_IDS" | grep 'port=132' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1 || true)

if [[ -z "$ID_TOP" || -z "$ID_BOTTOM" ]]; then
  echo "ERROR: Could not resolve display IDs for Thor (port 131 / 132). Output:" >&2
  echo "$SF_IDS" >&2
  exit 1
fi

echo "Device:            $DEVICE"
echo "TOP    display ID: $ID_TOP"
echo "BOTTOM display ID: $ID_BOTTOM"

echo "Capturing TOP screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_TOP" -p /sdcard/mgnrd_top.png
"${ADB_CMD[@]}" pull /sdcard/mgnrd_top.png "$OUTDIR/${TS}_TOP.png"

echo "Capturing BOTTOM screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_BOTTOM" -p /sdcard/mgnrd_bottom.png
"${ADB_CMD[@]}" pull /sdcard/mgnrd_bottom.png "$OUTDIR/${TS}_BOTTOM.png"

"${ADB_CMD[@]}" shell rm -f /sdcard/mgnrd_top.png /sdcard/mgnrd_bottom.png

echo "Saved:"
echo "  $OUTDIR/${TS}_TOP.png"
echo "  $OUTDIR/${TS}_BOTTOM.png"
