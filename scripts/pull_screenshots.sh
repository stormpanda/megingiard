#!/usr/bin/env bash
# pull_screenshots.sh — Capture both screens of a dual-screen Android handheld via ADB and save locally.
# Resolves TOP (HWC display 0) and BOTTOM (HWC display 1) 64-bit physical SurfaceFlinger display IDs
# dynamically (works across AYN Thor, Retroid Pocket Duo, and future dual-screen handhelds).
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
    echo "ERROR: No connected Android handheld found via ADB." >&2
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

# Resolve unique physical display IDs from SurfaceFlinger (HWC display 0 = top, HWC display 1 = bottom).
if ! SF_IDS=$("${ADB_CMD[@]}" shell dumpsys SurfaceFlinger --display-id 2>&1); then
  echo "ERROR: Failed to query display IDs via SurfaceFlinger on device ($DEVICE):" >&2
  echo "$SF_IDS" >&2
  exit 1
fi

ID_TOP=$(echo "$SF_IDS"    | grep '(HWC display 0)' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1 || true)
ID_BOTTOM=$(echo "$SF_IDS" | grep '(HWC display 1)' | grep -oE 'Display [0-9]+' | awk '{print $2}' | head -1 || true)

# Fallback to first two physical SurfaceFlinger displays if HWC annotations differ
if [[ -z "$ID_TOP" || -z "$ID_BOTTOM" ]]; then
  ID_TOP=$(echo "$SF_IDS"    | grep -oE 'Display [0-9]+' | awk 'NR==1 {print $2}' || true)
  ID_BOTTOM=$(echo "$SF_IDS" | grep -oE 'Display [0-9]+' | awk 'NR==2 {print $2}' || true)
fi

if [[ -z "$ID_TOP" || -z "$ID_BOTTOM" ]]; then
  echo "ERROR: Could not resolve TOP and BOTTOM SurfaceFlinger display IDs. Output:" >&2
  echo "$SF_IDS" >&2
  exit 1
fi

echo "Device:            $DEVICE"
echo "TOP    display ID: $ID_TOP"
echo "BOTTOM display ID: $ID_BOTTOM"

echo "Capturing TOP screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_TOP" -p /data/local/tmp/mgnrd_top.png
"${ADB_CMD[@]}" pull /data/local/tmp/mgnrd_top.png "$OUTDIR/${TS}_TOP.png"

echo "Capturing BOTTOM screen…"
"${ADB_CMD[@]}" shell screencap -d "$ID_BOTTOM" -p /data/local/tmp/mgnrd_bottom.png
"${ADB_CMD[@]}" pull /data/local/tmp/mgnrd_bottom.png "$OUTDIR/${TS}_BOTTOM.png"

"${ADB_CMD[@]}" shell rm -f /data/local/tmp/mgnrd_top.png /data/local/tmp/mgnrd_bottom.png

echo "Saved:"
echo "  $OUTDIR/${TS}_TOP.png"
echo "  $OUTDIR/${TS}_BOTTOM.png"
