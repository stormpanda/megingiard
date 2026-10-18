#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

CLANG="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang"
SYSROOT="/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"

INJECT_SRC="companion/ui/src/main/cpp/megingiard_gfx_inject.c"
INJECT_OUT="companion/ui/src/main/assets/megingiard_gfx_inject_arm64"

LAYER_SRC="companion/ui/src/main/cpp/megingiard_gfx_layer.c"
LAYER_OUT="companion/ui/src/main/assets/libVkLayer_MegingiardInspector.so"

"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android35 -pie -O2 -s \
    -Wall -Wextra -Werror \
    -o "$INJECT_OUT" "$INJECT_SRC" -ldl

"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android35 -shared -fPIC -O2 -s \
    -Wall -Wextra -Werror \
    -o "$LAYER_OUT" "$LAYER_SRC" -ldl

ls -lh "$INJECT_OUT" "$LAYER_OUT"
