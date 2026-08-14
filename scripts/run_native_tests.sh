#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

mkdir -p build/test
OUT="build/test/test_native_injectors"
SRC="companion/ui/src/main/cpp/test_native_injectors.c"

echo "Compiling native C unit tests..."
clang -Wall -Wextra -std=c99 -O2 "$SRC" -o "$OUT"

echo "Executing native C unit tests..."
"$OUT"
