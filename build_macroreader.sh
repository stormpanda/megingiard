#!/bin/sh
# Builds macroreader_arm64 — reads raw evdev events from the physical gamepad
# and forwards them to the Kotlin macro recorder via stdout.
#
# Requires Android NDK. Update CLANG / SYSROOT to match your local NDK install.

CLANG="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang"
SYSROOT="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
SRC="app/src/main/cpp/macroreader.c"
OUT="app/src/main/assets/macroreader_arm64"

"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android35 -static -O2 -s -o "$OUT" "$SRC"
echo "exit=$?"
ls -lh "$OUT"
