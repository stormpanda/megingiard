#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG="${PKG:-com.stormpanda.megingiard}"
ASSET_PATH="${ASSET_PATH:-app/src/main/assets/gamepadreader_arm64}"
TMP_BIN="/data/local/tmp/gamepadreader_arm64_verify"
APP_BIN_NAME="gamepadreader_arm64_verify"
APP_FILES_DIR="/data/user/0/${PKG}/files"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

need_cmd awk
need_cmd grep
need_cmd head
need_cmd sed

[[ -f "$ASSET_PATH" ]] || fail "Binary asset not found: $ASSET_PATH"
[[ -x "$ADB_BIN" ]] || fail "ADB not executable: $ADB_BIN"

echo "[INFO] Using adb: $ADB_BIN"
echo "[INFO] Package: $PKG"
echo "[INFO] Asset: $ASSET_PATH"

echo "[STEP] Clear logcat buffer for fresh classification"
$ADB_BIN logcat -c >/dev/null 2>&1 || true

device_line="$($ADB_BIN devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
[[ -n "$device_line" ]] || fail "No connected device in state 'device'"
echo "[INFO] Connected device: $device_line"

proc_dump="$($ADB_BIN shell "cat /proc/bus/input/devices" 2>/dev/null || true)"
[[ -n "$proc_dump" ]] || fail "Could not read /proc/bus/input/devices"

device_path="$(printf "%s\n" "$proc_dump" | tr -d '\r' | awk '
  /N: Name="Xbox Wireless Controller"/ { xbox=1; next }
  xbox && /H: Handlers=/ {
    if (match($0, /event[0-9]+/)) {
      print "/dev/input/" substr($0, RSTART, RLENGTH)
      exit
    }
  }
  /^$/ { xbox=0 }
')"

if [[ -z "$device_path" ]]; then
  device_path="$(printf "%s\n" "$proc_dump" | tr -d '\r' | awk '
    BEGIN { RS=""; FS="\n"; IGNORECASE=1 }
    {
      name=""; handlers=""
      for (i=1; i<=NF; i++) {
        if ($i ~ /^N: Name=/) name=$i
        if ($i ~ /^H: Handlers=/) handlers=$i
      }
      if (name ~ /(xbox|gamepad|controller|joystick|dualshock|dualsense|switch pro)/ && handlers != "") {
        if (match(handlers, /event[0-9]+/)) {
          print "/dev/input/" substr(handlers, RSTART, RLENGTH)
          exit
        }
      }
    }
  ')"
fi

if [[ -z "$device_path" ]]; then
  device_path="/dev/input/event9"
  echo "[WARN] Could not auto-detect controller node; using fallback $device_path"
else
  echo "[INFO] Detected gamepad node: $device_path"
fi

echo "[STEP] Push binary to /data/local/tmp and test in shell context"
$ADB_BIN push "$ASSET_PATH" "$TMP_BIN" >/dev/null
$ADB_BIN shell "chmod 755 '$TMP_BIN'"

shell_out="$($ADB_BIN shell "sh -c '
  out=/data/local/tmp/gamepadreader_shell.out;
  rm -f \"\$out\";
  \"$TMP_BIN\" \"$device_path\" >\"\$out\" 2>&1 &
  pid=\$!;
  sleep 1;
  kill \$pid >/dev/null 2>&1 || true;
  head -n 12 \"\$out\"
'" 2>&1 || true)"
printf "%s\n" "[SHELL OUTPUT]"
printf "%s\n" "$shell_out"

echo "[STEP] Inject binary into app filesDir via run-as (no reinstall needed)"
cat "$ASSET_PATH" | $ADB_BIN shell "run-as '$PKG' sh -c 'cat > ${APP_FILES_DIR}/${APP_BIN_NAME} && chmod 700 ${APP_FILES_DIR}/${APP_BIN_NAME}'" >/dev/null

echo "[STEP] Run injected binary in app context with explicit node"
app_out="$($ADB_BIN shell "run-as '$PKG' sh -c '
  out=${APP_FILES_DIR}/${APP_BIN_NAME}.out;
  rm -f \"\$out\";
  ${APP_FILES_DIR}/${APP_BIN_NAME} \"$device_path\" >\"\$out\" 2>&1 &
  pid=\$!;
  sleep 1;
  kill \$pid >/dev/null 2>&1 || true;
  head -n 12 \"\$out\"
'" 2>&1 || true)"
printf "%s\n" "[APP OUTPUT]"
printf "%s\n" "$app_out"

summary="UNKNOWN"
if printf "%s\n" "$app_out" | grep -q '^R '; then
  summary="PASS_READY"
elif printf "%s\n" "$app_out" | grep -q '^NOPERM '; then
  summary="FAIL_ACCESS_DENIED"
elif printf "%s\n" "$app_out" | grep -q '^NODEV'; then
  summary="FAIL_NO_DEVICE"
fi

echo "[STEP] Recent recorder logs"
recent_logs="$($ADB_BIN logcat -d -v time 2>/dev/null | grep -E "Mgnrd\.GamepadRecordingManager|Mgnrd\.GamepadRecordingOverlay|gamepadreader_a|NOPERM|NODEV" | tail -n 80 || true)"
printf "%s\n" "$recent_logs"

if printf "%s\n" "$recent_logs" | grep -q "Gamepad node access denied"; then
  summary="FAIL_ACCESS_DENIED_APP_RUNTIME"
fi

echo "[RESULT] $summary"
case "$summary" in
  PASS_READY)
    echo "[OK] Reader reached ready state in app context."
    exit 0
    ;;
  FAIL_ACCESS_DENIED)
    echo "[ERROR] Reader sees controller but cannot access input node in app context."
    exit 2
    ;;
  FAIL_ACCESS_DENIED_APP_RUNTIME)
    echo "[ERROR] App runtime still reports gamepad node access denied."
    exit 5
    ;;
  FAIL_NO_DEVICE)
    echo "[ERROR] Reader did not report a usable device."
    exit 3
    ;;
  *)
    echo "[ERROR] Could not classify output."
    exit 4
    ;;
esac
