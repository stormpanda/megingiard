#!/bin/sh
# Prefer ANDROID_NDK_HOME / NDK env vars; fall back to the original Homebrew path.
NDK_ROOT="${ANDROID_NDK_HOME:-${NDK:-/opt/homebrew/Caskroom/android-ndk/29/AndroidNDK14206865.app/Contents/NDK}}"

# Derive host tag from the current system (darwin-arm64, darwin-x86_64, linux-x86_64, …)
HOST_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
HOST_ARCH=$(uname -m)
case "$HOST_OS" in
  darwin) HOST_TAG_OS="darwin" ;;
  linux)  HOST_TAG_OS="linux"  ;;
  *) echo "Unsupported host OS: $HOST_OS" >&2; exit 1 ;;
esac
case "$HOST_ARCH" in
  x86_64|amd64)     HOST_TAG_ARCH="x86_64" ;;
  arm64|aarch64)    HOST_TAG_ARCH="arm64"  ;;
  *) echo "Unsupported host arch: $HOST_ARCH" >&2; exit 1 ;;
esac
HOST_TAG="${HOST_TAG_OS}-${HOST_TAG_ARCH}"

CLANG="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/bin/aarch64-linux-android33-clang"
SYSROOT="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/sysroot"
SRC="app/src/main/cpp/keyinjector.c"
OUT="app/src/main/assets/keyinjector_arm64"
"$CLANG" --sysroot="$SYSROOT" --target=aarch64-linux-android33 -static -O2 -s -o "$OUT" "$SRC"
echo "exit=$?"
ls -lh "$OUT"
