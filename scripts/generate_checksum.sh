#!/usr/bin/env zsh

# Generate a SHA-256 checksum file for the release APK.
#
# SHA-256 is used instead of MD5 because MD5 is broken against collision
# attacks: an attacker who can pick the contents of two files can construct
# them to share an MD5 hash, which defeats the purpose of an integrity
# fingerprint published next to a download. SHA-256 has no known practical
# collision attack and is the modern baseline for binary distribution.
#
# For maximum trust, sign the resulting <apk>-checksum-sha256.txt file with
# GPG (e.g. `gpg --detach-sign --armor <file>`) and publish the .asc
# signature alongside the public key fingerprint in the README.

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
out="$APK_DIR/${apk_name}-checksum-sha256.txt"

# `shasum -a 256` is available on macOS by default and on most Linux distros;
# on Linux-only environments `sha256sum` is the canonical command. Prefer
# shasum for cross-platform consistency.
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$apk" | awk '{ print $1 }' > "$out"
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$apk" | awk '{ print $1 }' > "$out"
else
  echo "Error: neither 'shasum' nor 'sha256sum' found — cannot generate checksum" >&2
  exit 1
fi

# Clean up any stale MD5 checksum file from earlier releases so the
# directory has a single, unambiguous integrity artifact.
legacy_md5="$APK_DIR/${apk_name}-checksum-md5.txt"
if [[ -f "$legacy_md5" ]]; then
  rm -f "$legacy_md5"
  echo "Removed legacy MD5 checksum: $legacy_md5"
fi

echo "SHA-256 checksum written to $out"
echo "Hash: $(cat "$out")"
