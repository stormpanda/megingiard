#!/bin/sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

CLANG="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang"
SYSROOT="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
SRC="companion/ui/src/main/cpp/mouseinjector.c"
OUT="companion/ui/src/main/assets/mouseinjector_arm64"
"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android35 -static -O2 -s -o "$OUT" "$SRC"
echo "exit=$?"
ls -lh "$OUT"
