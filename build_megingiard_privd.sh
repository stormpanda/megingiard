#!/bin/sh
CLANG="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang"
SYSROOT="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
SRC="app/src/main/cpp/megingiard_privd.c"
OUT="app/src/main/assets/megingiard_privd_arm64"

# ---------------------------------------------------------------------------
# Read the HMAC key from local.properties (key: megingiard.privd.hmac.key).
# If not set, use the same default that is baked into BuildConfig.PRIVD_HMAC_KEY
# when the key is absent from local.properties.  The key must be exactly 64
# uppercase hex chars (32 bytes).  Both the app and the daemon binary must be
# built with the same value — rebuild this binary whenever the key changes.
# ---------------------------------------------------------------------------
HMAC_KEY=$(grep '^megingiard\.privd\.hmac\.key=' local.properties 2>/dev/null \
    | cut -d'=' -f2- \
    | tr -d '[:space:]' \
    | tr '[:lower:]' '[:upper:]')
if [ -z "$HMAC_KEY" ] || [ "${#HMAC_KEY}" -ne 64 ]; then
    HMAC_KEY="A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2"
    echo "build_megingiard_privd: using default HMAC key (set megingiard.privd.hmac.key in local.properties for a custom key)"
fi

"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android35 -static -O2 -s \
    -DPRIVD_HMAC_KEY_HEX="\"$HMAC_KEY\"" \
    -o "$OUT" "$SRC"
echo "exit=$?"
ls -lh "$OUT"
