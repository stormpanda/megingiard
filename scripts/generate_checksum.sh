#!/usr/bin/env zsh

SCRIPT_DIR="${0:A:h}"
APK_DIR="$SCRIPT_DIR/../app/release"

apks=("$APK_DIR"/*.apk)

if [[ ! -e "${apks[1]}" ]]; then
  echo "Error: No APK found in $APK_DIR" >&2
  exit 1
fi

if (( ${#apks[@]} > 1 )); then
  echo "Error: Multiple APKs found in $APK_DIR — expected exactly one:" >&2
  for f in "${apks[@]}"; do
    echo "  $f" >&2
  done
  exit 1
fi

apk="${apks[1]}"
apk_name="$(basename "$apk" .apk)"
out="$APK_DIR/${apk_name}-checksum-md5.txt"

md5 -q "$apk" > "$out"

echo "Checksum written to $out"
